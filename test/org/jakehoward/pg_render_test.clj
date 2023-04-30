(ns org.jakehoward.pg-render-test
  (:require [org.jakehoward.pg-render :as pgr]
            [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(deftest renders-literals
  (testing "it correctly renders number values"
    (is (= "select 1"     (pgr/render "select %L:v" {:v 1})))
    (is (= "select 1"     (pgr/render "select %L:v" {:v (int 1)})))
    (is (= "select 1"     (pgr/render "select %L:v" {:v (long 1)})))
    (is (= "select 1"     (pgr/render "select %L:v" {:v (short 1)})))
    (is (= "select 1.5"   (pgr/render "select %L:v" {:v (double 1.5)})))
    (is (= "select 1.6"   (pgr/render "select %L:v" {:v (float 1.6)})))
    (is (= "select 3"     (pgr/render "select %L:v" {:v (bigint 3)})))
    (is (= "select 4"     (pgr/render "select %L:v" {:v (biginteger 4)})))
    (is (= "select 5.5"   (pgr/render "select %L:v" {:v (bigdec 5.5)})))
    (is (= "select 100.0" (pgr/render "select %L:v" {:v 1e2})))
    (is (= "select 0.5"   (pgr/render "select %L:v" {:v 1/2}))))

  (testing "it correctly renders string values"
    (is (= "select '1'"                     (pgr/render "select %L:v" {:v "1"})))
    (is (= "select 'I''m a string'"         (pgr/render "select %L:v" {:v "I'm a string"})))
    (is (= "select 'I''m ''''a'''' string'" (pgr/render "select %L:v" {:v "I'm ''a'' string"})))
    (is (= "select ''"                      (pgr/render "select %L:v" {:v ""}))))

  (testing "it correctly renders boolean values"
    (is (= "select TRUE"  (pgr/render "select %L:b" {:b true})))
    (is (= "select TRUE"  (pgr/render "select %L:b" {:b (Boolean. true)})))
    (is (= "select FALSE" (pgr/render "select %L:b" {:b false})))
    (is (= "select FALSE" (pgr/render "select %L:b" {:b (Boolean. false)})))
    (is (= "select FALSE" (pgr/render "select %L:b" {:b Boolean/FALSE})))
    (is (= "select FALSE" (pgr/render "select %L:b" {:b (Boolean/valueOf false)}))))

  (testing "it correctly renders LocalDate"
    (is (= "select '2023-04-30'"
           (pgr/render "select %L:d" {:d (java.time.LocalDate/parse "2023-04-30")}))))

  (testing "it correctly renders LocalDateTime"
    (is (= "select '2023-04-30T11:35:20.001'"
           (pgr/render "select %L:d"
                       {:d (java.time.LocalDateTime/parse "2023-04-30T11:35:20.001")}))))

  (testing "it correctly renders Instant"
    (is (= "select '1999-02-21T12:16:44.664627Z'"
           (pgr/render "select %L:i"
                       {:i (java.time.Instant/parse "1999-02-21T12:16:44.664627Z")}))))

  (testing "throws if unable to render value"
    (is (thrown? Exception (pgr/render "select %L:v" {:v (fn [] :x)}))))

  (testing "renders NULL if nil passed"
    (is (= "select NULL" (pgr/render "select %L:v" {:v nil})))))

(deftest renders-identifiers
  (testing "it correctly uses and escapes double quotes"
    (is (= "select \"col\""         (pgr/render "select %I:i" {:i "col"})))
    (is (= "select \"c\"\"o\"\"l\"" (pgr/render "select %I:i" {:i "c\"o\"l"}))))

  (testing "it doesn't object to rendering invalid sql"
    (is (= "select \"1\"" (pgr/render "select %I:i" {:i 1})))))

(deftest renders-string-literals
  (testing "it inserts what it's given verbatim"
    (is (= "select 1; drop tables" (pgr/render "select %s:str" {:str "1; drop tables"})))))

(deftest renders-arrays
  (testing "if all entries are number like, it renders without escaping"
    (is (= "unnest(ARRAY[1,2.1,3.1,0.5,4.1,5,6])"
           (pgr/render "unnest(%A:xs)" {:xs [1
                                             (float 2.1)
                                             (double 3.1)
                                             1/2
                                             (bigdec 4.1)
                                             (bigint 5)
                                             (biginteger 6)]}))))

  (testing "if any entry is not a number, it renders with escaping"
    (is (= "unnest(ARRAY['1','2.0','3.0','0.5','2'])"
           (pgr/render "unnest(%A:xs)" {:xs [1 (float 2) (double 3) 1/2 "2"]}))))

  (testing "can render nulls in arrays"
    (is (= "unnest(ARRAY[1,NULL,2,NULL])"
           (pgr/render "unnest(%A:xs)" {:xs [1 nil 2 nil]}))))

  (testing "array examples"
    (is (= "unnest(ARRAY[])"
           (pgr/render "unnest(%A:xs)" {:xs '()})))
    (is (= "unnest(ARRAY['bob','ethel'])"
           (pgr/render "unnest(%A:xs)" {:xs '("bob" "ethel")})))
    (is (= "unnest(ARRAY['a','b'])"
           (pgr/render "unnest(%A:xs)" {:xs ["a" "b"]})))
    (is (= "unnest(ARRAY['a','b'])"
           (pgr/render "unnest(%A:xs)" {:xs (lazy-seq ["a" "b"])})))
    (is (= "unnest(ARRAY['a','b'])"
           (pgr/render "unnest(%A:xs)" {:xs (seq ["a" "b"])})))
    (is (= "unnest(ARRAY['a','b'])"
           (pgr/render "unnest(%A:xs)" {:xs (list "a" "b")})))))

(deftest overriding-symbols
  (testing "when you override a symbol, the default one no longer works"
    (is (= "select 'foo' %L:v" (pgr/render "select %Lit:v %L:v" {:v "foo"} {:literal "Lit"}))))

  (testing "you cannot use the same escape token for different escape types"
    (is (thrown? Exception (pgr/render "select %I:v" {:v "f"} {:literal "I"})))
    (is (thrown? Exception (pgr/render "select %I:v" {:v "f"} {:literal "Wat" :identifier "Wat"}))))

  (testing "every symbol can be overriden"
    (is (= "select 'foo'"     (pgr/render "select %Literal:v" {:v "foo"} {:literal "Literal"})))
    (is (= "select \"foo\""   (pgr/render "select %ident:v" {:v "foo"} {:identifier "ident"})))
    (is (= "select foo"       (pgr/render "select %str:v" {:v "foo"} {:string "str"})))
    (is (= "select ARRAY['foo']" (pgr/render "select %arr:v" {:v '("foo")} {:array "arr"}))))

  (testing "all symbols can be overriden"
    (is (= "select 'foo' \"foo\" foo ARRAY['foo']"
           (pgr/render "select %Literal:v %ident:v %str:v %arr:vs"
                       {:v "foo" :vs '("foo")}
                       {:literal "Literal" :identifier "ident" :string "str" :array "arr"}))))

  (testing "you can override only a subset of the symbols"
    (is (= "select 'foo'"   (pgr/render "select %Literal:v" {:v "foo"} {:literal "Literal"})))
    (is (= "select \"foo\"" (pgr/render "select %I:v" {:v "foo"} {:literal "Literal"})))
    (is (= "select \"foo\"" (pgr/render "select %fyer:v" {:v "foo"} {:identifier "fyer"})))
    (is (= "select 'bar'"   (pgr/render "select %L:v" {:v "bar"} {:identifier "fyer"})))))

(deftest gotchas
  (testing "pct escaping"
    (is (= "select %"                (pgr/render "select %%")))
    (is (= "select %% from"          (pgr/render "select %%% from")))
    (is (= "select %% from"          (pgr/render "select %%%% from")))
    (is (= "select %L:v from ..."    (pgr/render "select %%L:v from ..." {:v 1})))
    (is (= "select %"                (pgr/render "select %" {:v "unused"}))))

  (testing "trixy override values are forbidden"
    (is (thrown? Exception (pgr/render "select %LO:v" {:v "boom"}  {:literal "LO:L"})))
    (is (thrown? Exception (pgr/render "whatever"     {:v "uh-oh"} {:literal "%LOL"})))
    (is (thrown? Exception (pgr/render "whatever"     {:v "noooo"} {:literal ""}))))

  (testing "ambiguities and trickiness"
    (is (= "select 2"      (pgr/render "select %L:sub-foo" {:sub 1 :sub-foo 2})))
    (is (= "select 1"      (pgr/render "select %L:sub" {:sub 1 :sub-foo 2})))
    (is (thrown? Exception (pgr/render "select %L::v" {:v 1})))
    (is (thrown? Exception (pgr/render "select %L:" {:v 1})))
    (is (thrown? Exception (pgr/render "select %s:variable" {:var "nope"})))
    (is (= ":"             (pgr/render ":"     {:v 1})))
    (is (= "%:v"           (pgr/render "%:v"   {:v 1})))
    (is (= "%L "           (pgr/render "%L "   {:v 1})))
    (is (= "%L :v"         (pgr/render "%L :v" {:v 1})))
    (is (= ""              (pgr/render ""      {:v 1})))
    (is (= "ARRAY[']']"    (pgr/render "%A:xs" {:xs ["]"]}))))

  (testing "anyone who tries multi-pass regex replacement feels my wrath"
    (is (= "select '%I:v'"         (pgr/render "select %L:v" {:v "%I:v"})))
    (is (= "select \"%L:v\""       (pgr/render "select %I:v" {:v "%L:v"}))))

  (testing "characters"
    (is (= "select 'hey\t\nnewline'" (pgr/render "select %L:v" {:v "hey\t\nnewline"})))
    (is (= "select 'João'" (pgr/render "select %L:v" {:v "João"}))))

  (testing "using and re-using values for different escape types with different cases"
    (let [vs {:a "a" :bb "bb" :Ab "Ab" :c-c "see-see"}
          q  (str "select %I:c-c, foo, bar from %I:a.%I:bb "
                  "where foo = %L:c-c and %I:c-c = %L:Ab")]
      (is (= (str "select \"see-see\", foo, bar "
                  "from \"a\".\"bb\" where foo = 'see-see' and \"see-see\" = 'Ab'")
             (pgr/render q vs))))))

(deftest missing-values
  (testing "throws exception if escape-type with value-name found, but no variable supplied"
    (is (thrown? java.lang.Exception (pgr/render "select %L:v" {:not-v 1}))))

  (testing "does not throw if nil supplied for value"
    (is (= "select NULL" (pgr/render "select %L:v" {:v nil}))))

  (testing "throws exception if substring of variable found"
    (is (thrown? Exception (pgr/render "select %L:vv" {:v 1})))))

(comment
  
;
  )
