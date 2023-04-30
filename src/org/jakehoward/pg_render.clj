(ns org.jakehoward.pg-render
  (:require [clojure.string :as str]))

;; todo - [x] https://www.postgresql.org/docs/15/datatype.html
;;      - [ ] GitHub Actions build
;;           - Fail on test failure
;;           - Build and deploy JAR to clojars/maven/both
;;      - [ ] Readme
;;           - Examples
;;           - Possibly surprising behaviour (%LL:v would render as is, lib gets out of the way)
;;           - Performance tests
;;           - Why Pg only
;;           - Rationale (Sam Ritchie literate programming talk, what's the philosophy)

(def default-escape-tokens {:literal "L"
                            :identifier "I"
                            :string "s"
                            :array "A"})

(def number-literal-types
  #{Long
    Short
    Integer
    Float
    Double
    clojure.lang.Ratio
    clojure.lang.BigInt
    java.math.BigDecimal
    BigInteger})

(def allowed-chars
  #{\A \B \C \D \E \F \G \H \I \J \K \L \M \N \O \P \Q \R \S \T \U \V \W \X \Y \Z
    \a \b \c \d \e \f \g \h \i \j \k \l \m \n \o \p \q \r \s \t \u \v \w \x \y \z
    \0 \1 \2 \3 \4 \5 \6 \7 \8 \9
    \- \_})

(defprotocol PgEscapeLiteral
  (pg-escape-literal [this]))

(extend-protocol PgEscapeLiteral
  Long
  (pg-escape-literal [this] this)

  Short
  (pg-escape-literal [this] this)

  Integer
  (pg-escape-literal [this] this)

  Float
  (pg-escape-literal [this] this)

  Double
  (pg-escape-literal [this] this)

  clojure.lang.Ratio
  (pg-escape-literal [this] (double this))

  clojure.lang.BigInt
  (pg-escape-literal [this] this)

  java.math.BigDecimal
  (pg-escape-literal [this] this)

  BigInteger
  (pg-escape-literal [this] this)

  Number
  (pg-escape-literal [this]
    (pg-escape-literal (.toString this)))

  Boolean
  (pg-escape-literal [this]
    (if (= true this)
      "TRUE"
      "FALSE"))

  java.time.LocalDate
  (pg-escape-literal [this]
    (pg-escape-literal (.toString this)))

  java.time.LocalDateTime
  (pg-escape-literal [this]
    (pg-escape-literal (.toString this)))

  java.time.Instant
  (pg-escape-literal [this]
    (pg-escape-literal (.toString this)))

  String
  (pg-escape-literal [this]
    (str "'" (str/replace (.toString this) #"'" "''") "'")))

(defmulti pg-escape (fn [escape-type _] escape-type))

(defmethod pg-escape :literal [_ v]
  (if (nil? v)
    "NULL"
    (pg-escape-literal v)))

(defmethod pg-escape :identifier [_ v]
  (str "\"" (str/replace v #"\"" "\"\"") "\""))

(defn render-as-literal? [v]
  (boolean
   (or (nil? v)
       (number-literal-types (type v)))))

(defmethod pg-escape :array [_ vs]
  ;; Postgres doesn't like mixed type arrays without an explicit cast
  ;; so if all values are numbers, don't escape like strings, if there
  ;; is a single non-number value (excluding null), then escape all
  ;; values like strings.
  (let [stringify-all (not (every? render-as-literal? vs))
        escaped-vs    (->> vs
                           (map (fn [v] (let [escaped (pg-escape :literal v)]
                                          (if (and (render-as-literal? v) stringify-all)
                                            (pg-escape :literal (str escaped))
                                            escaped)))))
        array-body    (str/join "," escaped-vs)]
    (str/join "" ["ARRAY[" array-body "]"])))

(defmethod pg-escape :string [_ v]
  v)

(defn- reverse-lookup [m]
  (into {} (map (fn [[a b]] [b a]) m)))

(defn- contains-disallowed-chars? [s]
  (->> s
       (map (fn [c] (not (allowed-chars c))))
       (some identity)
       boolean))

(defn- create-override-error-msg [bad-val]
  (str
   "Override tokens must contain [a-zA-Z0-9_-]+ Offending value: "  "\"" bad-val "\""))

;; A note on naming:
;;
;; select %L:foo
;; select %<escape-token>:<value-name>
;;
;; L        = escape-token
;; :literal = escape-type
;; foo      = value-name

(defn compile-template
  "Given a template, and possibly some override escape tokens,
  create a compiled template that can be serialized and stored.

  These compiled templates can be loaded ahead of query team
  and then rendered using render-compiled."
  ([template] (compile-template template {}))
  ([^String template override-escape-tokens]
   (when-let [error-messages
              (->> (vals override-escape-tokens)
                   (filter (some-fn contains-disallowed-chars? empty?))
                   (map create-override-error-msg)
                   seq)]

     (throw (ex-info (str "Override errors: " (str/join "|" error-messages))
                     {:type ::render-exception})))

   (let [escape-type->escape-tokens (merge default-escape-tokens override-escape-tokens)]
     (when (not=
            (count (set (vals escape-type->escape-tokens)))
            (count escape-type->escape-tokens))

       (throw (ex-info
               (str "Override errors: you must not use the same escape "
                    "token for more than one escape type")
               {:type ::render-exception}))))

   (let [escape-token->escape-type (->> override-escape-tokens
                                        (merge default-escape-tokens)
                                        reverse-lookup)]
     (loop [pos            0
            current-chunk  []
            chunks         []]
       (let [current-char  (get template pos)]
         (cond (nil? current-char)
               {:version 1
                :template
                (->> (if (seq current-chunk)
                       (conj chunks (str/join "" current-chunk))
                       chunks)
                     flatten
                     vec)}

               (= \% current-char)
               (let [next-char        (get template (inc pos))
                     escape-token-end (.indexOf template ":" pos)
                     escape-token      (when (not= -1 escape-token-end)
                                         (subs template (inc pos) escape-token-end))
                     rest-template    (when escape-token
                                        (subs template (inc escape-token-end)))
                     value-name       (when rest-template
                                        (->> rest-template
                                             (take-while allowed-chars)
                                             (str/join "")))]
                 (cond
                   (= \% next-char)
                   (recur (+ pos 2) (conj current-chunk \%) chunks)

                   (and value-name
                        (contains? escape-token->escape-type escape-token))
                   (recur (+ pos 1 (count escape-token) 1 (count value-name))
                          []
                          (-> chunks
                              (conj (str/join "" current-chunk))
                              (conj {:escape-type (get escape-token->escape-type escape-token)
                                     :value-name (keyword value-name)})))
                   :else
                   (recur (inc pos) (conj current-chunk \%) chunks)))

               :else
               (recur (inc pos) (conj current-chunk current-char) chunks)))))))

(defn render-compiled
  "Render a pre-compiled template. See (compile-template ...)"
  [compiled values]
  (when (not= 1 (:version compiled))
    (ex-info (str "Compiled template version " (:version compiled) " not supported")
             {:type ::render-exception}))

  (->> compiled
       :template
       (map (fn [token]
              (if (= (type token) String)
                token
                (if (contains? values (:value-name token))
                  (pg-escape (:escape-type token) (get values (:value-name token)))
                  (throw (ex-info (str (:value-name token) " not found in values")
                                  {:type ::render-exception}))))))
       (str/join "")))

(defn render
  "Render a postgres sql template, for example

    select
      %I:col
    from
      %I:schema.%I:table t
    where
      %I:col = ANY(%A:xs)
      and t.foo = %L:bar
      and t.baz in (%s:hack)

  and provide values, like
    {:col \"id\" :schema \"s\" :table \"t\" :xs [1 2] hack \"'a','b','c'\"}
  call:
    (render template-string values)
  and recieve an escaped template in return.

  Mostly safe against SQL injection...(software provided without warranty)

  Default templating:
  %L a literal (number, string, etc)
  %I an identifier (column name, table name, schema name, etc)
  %A an array literal
  %s inject an unescaped string (WARNING!! SQL injection vector!)

  In the unlikely event that you'd like to use different templating
  values, the third argument allows you to override them
  (render template values {:literal \"my_literal\"})
  and then use:
    select ... where %my_literal:val
  Override types are :literal, :identifier, :string, and :array.

  Render a literal % as %%. If we can't find a replace value
  a single % will render as a % too. Convenient for static LIKE statements.

  Explicit nil -> null.
  Missing value throws an exception.

  Check code for a full list of types that can be rendered into a pg literal
  by default."
  ([template] (render template {}))
  ([template values] (render template values {}))
  ([template values override-escape-tokens]
   (let [compiled-template (compile-template template override-escape-tokens)]
     (render-compiled compiled-template values))))

(comment
  (let [ids     ["a" "b" "c"]
        ids-str (->> ids
                     (map #(pg-escape :literal %))
                     (str/join ","))]
    (render "select ... in (%s:ids)" {:ids ids-str}))
;
  )
