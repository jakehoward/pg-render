;; clj -M doc_test.clj
;; swap comment -> do to try it out
(require '[org.jakehoward.pg-render :as pgr])

(comment
  (let [template "select x from %I:table where x = %L:value"]
    (-> (pgr/render template {:table "treasure" :value "yes"})
        println)))

(comment
  (let [template "select %L:a, %L:b, %L:c, %L:d, %L:e"
        values {:a "I'm a string"
                :b (java.time.Instant/now)
                :c 4/5
                :d false
                :e nil}]
    (-> (pgr/render template values)
        println)))

(comment
  (-> (pgr/render "select gemstones from %I:schema.%I:table" {:schema "treasure" :table "chest"})
      println))

(comment
  (-> (pgr/render "select x as %I:hacker" {:hacker "x\"; drop it like it's hot;"})
      println))

(comment
  (-> (pgr/render "select x from y where id in (%s:ids)" {:ids "1,2,3"})
      println))

(comment
  (-> (pgr/render "select unnest(%A:xs)" {:xs ["I'm a string" (java.time.Instant/now)]})
      println))

(comment
  (-> (pgr/render "select unnest(%A:xs)" {:xs [1 2.5 3/2 (bigint 22)]})
      println))

(comment
  (-> (pgr/compile-template "select unnest(%A:xs)")
      str
      println))

(comment
  (let [compiled (pgr/compile-template "select unnest(%A:xs)")]
    (-> (pgr/render-compiled compiled {:xs ["a" "b"]})
        println)))

(comment
  (println "literal:"    (pgr/pg-escape :literal    "I'm a string"))
  (println "identifier:" (pgr/pg-escape :identifier "table_name"))
  (println "array:"      (pgr/pg-escape :array      ["a" "b"])))

(comment
  (-> (pgr/render "% L:v" {:v "x"})
      println)
  (-> (pgr/render "%l:v" {:v "x"})
      println)
  (-> (pgr/render "%%L:v" {:v "x"})
      println)

  (println)
  (-> (pgr/render "%L:v" {:not-v "x"})
      println))

(comment
  (-> (pgr/render "%my_literal:v" {:v "x"} {:literal "my_literal"})
      println)
  (-> (pgr/render "%my_identifier:v" {:v "x"} {:identifier "my_identifier"})
      println)
  (-> (pgr/render "%my_string:v" {:v "x"} {:string "my_string"})
      println)
  (-> (pgr/render "%my_array:v" {:v ["x"]} {:array "my_array"})
      println)
  (println "...and two together to prove it works:")
  (-> (pgr/render "%my_literal:v %my_identifier:v" {:v "x"} {:identifier "my_identifier"
                                                               :literal "my_literal"})
      println))
