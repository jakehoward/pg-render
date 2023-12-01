# pg-render

pg-render is an opinionated SQL templating library for PostgreSQL.

I _do_ like writing sql, I _don't_ like positional parameters.

Currently in "alpha" because I haven't tested this in the wild. I'll publish it as 1.0.0 once I've kicked the tyres on it in a real project. One word of caution: the library does the SQL escaping rather than passing it to the database - use at your own risk.

## Quick start

```clojure
(require '[org.jakehoward.pg-render :as pgr])

(let [template "select x from %I:table where x = %L:value"]
    (-> (pgr/render template {:table "treasure" :value "yes"})
        println))

=> select x from "treasure" where x = 'yes'
```

## Examples

You can run them yourself by cloning the repo and changing `comment` to `do` in `doc_test.clj`

```
clj -M doc_test.clj
```

### Literals

Literals are escaped to prevent SQL injection...this software is provided without warranty.

See the code for all types that extend the `PgEscapeLiteral` protocol, here are some examples:

```clojure
(require '[org.jakehoward.pg-render :as pgr])

(let [template "select %L:a, %L:b, %L:c, %L:d, %L:e"
      values   {:a "I'm a string"
                :b (java.time.Instant/now)
                :c 4/5
                :d false
                :e nil}]
    (-> (pgr/render template values)
        println))

=> select 'I''m a string', '2023-04-30T14:03:26.155614Z', 0.8, FALSE, NULL
```

### Identifiers

Identifiers are escaped to work properly if you do weird things like use capital letters:

```clojure
(require '[org.jakehoward.pg-render :as pgr])

(-> (pgr/render "select gemstones from %I:schema.%I:table" {:schema "treasure" :table "chest"})
    println)

=> select gemstones from "treasure"."chest"
```

Identifiers are also escaped to prevent SQL injection...this software is provided without warranty.

```clojure
(require '[org.jakehoward.pg-render :as pgr])

(-> (pgr/render "select x as %I:hacker" {:hacker "x\"; drop it like it's hot;"})
    println)

=> select x as "x""; drop it like it's hot;"
```

### Raw strings

pg-render is willing to get out of the way. If you want to risk shoving in a raw string, go for it. Just in case it's not clear: you will be wide open to SQL injection attacks and it's on you to prevent them.

```clojure
(require '[org.jakehoward.pg-render :as pgr])

(-> (pgr/render "select x from y where id in (%s:ids)" {:ids "1,2,3"})
    println)

=> select x from y where id in (1,2,3)
```

### Arrays
Renders values as a Pg array literal, escaping them based on type:


```clojure

(require '[org.jakehoward.pg-render :as pgr])

  (-> (pgr/render "select unnest(%A:xs)" {:xs ["I'm a string" (java.time.Instant/now)]})
      println)

=> select unnest(ARRAY['I''m a string','2023-04-30T13:53:51.207378Z'])
```


If all values of the array are numbers known to pg-render, they won't be wrapped in quotes. This is a small convenience that means you don't have to cast the array for PostgreSQL to recognise it as an array of numbers.

```clojure
(require '[org.jakehoward.pg-render :as pgr])

(-> (pgr/render "select unnest(%A:xs)" {:xs [1 2.5 3/2 (bigint 22)]})
    println)

=> select unnest(ARRAY[1,2.5,1.5,22])
```

Nested arrays are _not_ currently supported but pg-render is open to workarounds by exposing the pg-escape method and giving you the `%s` raw string template type.

## Compiling templates

You can compile templates so that you don't have to suffer the parsing overhead every time you run a query:

```clojure
(require '[org.jakehoward.pg-render :as pgr])

(-> (pgr/compile-template "select unnest(%A:xs)")
      str
      println)

=> {:version 1, :template ["select unnest(" {:escape-type :array, :value-name :xs} ")"]}
```

You can store this and then load as edn using [read-string](https://clojuredocs.org/clojure.edn/read-string).

Running a compiled template:

```clojure
(require '[org.jakehoward.pg-render :as pgr])

(let [compiled (pgr/compile-template "select unnest(%A:xs)")]
    (-> (pgr/render-compiled compiled {:xs ["a" "b"]})
        println))

=> select unnest(ARRAY['a','b'])
```

See benchmarks to help you decide whether it's worth the hassle...

## Escaping

You can escape things yourself (useful for preparing `%s` raw strings safely)

```clojure
(require '[org.jakehoward.pg-render :as pgr])

(println "literal:"    (pgr/pg-escape :literal    "I'm a string"))
(println "identifier:" (pgr/pg-escape :identifier "table_name"))
(println "array:"      (pgr/pg-escape :array      ["a" "b"]))

=> literal: 'I''m a string'
   identifier: "table_name"
   array: ARRAY['a','b']
```

Percent literal is rendered with a double `%` => `%%`. However, if pg-render doesn't detect a template, it will render a single `%`.

## Configuring

You can override the escape tokens if they get in your way (unlikely). Override none, one, many, all, to taste.

```clojure

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
    println)

=> 'x'
   "x"
   x
   ARRAY['x']
   ...and two together to prove it works
   'x' "x"
```

## Extending

`PgEscapeLiteral` is a protocol. Extend it to get a literal rendering of your type.

`pg-escape` is a multi-method. Create a `defmethod` to add a new escaping type (for example, query within a query, better-array because I refused the PR implementing nesting, etc...).

## The rough edges

pg-render is quite permissive. It throws exceptions for certain types of nonsense (see code), but mainly lets you get on with it.

You can see the tests for some examples, but for your convenience:

```clojure
(require '[org.jakehoward.pg-render :as pgr])

(-> (pgr/render "% L:v" {:v "x"})
    println)
(-> (pgr/render "%l:v" {:v "x"})
    println)
(-> (pgr/render "%%L:v" {:v "x"})
    println)

(println)
(-> (pgr/render "%L:v" {:not-v "x"})
    println)

=> % L:v
   %l:v
   %L:v

   Execution error (ExceptionInfo) at org.jakehoward.pg-render/render-compiled$fn (pg_render.clj:234).
   :v not found in values
```

You also can't have templating butt up against acceptable value-name characters. It would be an ambiguity were a value key a sub-string of another one. This hasn't come up as a problem in practice for me and I can't think of when it ever would. If this happens...you'll have to not use pg-render for that query

```
(pgr/render "select %L:t23" {:t 1}) ;; => nope, :t23 not found in values
```


## Benchmarks

Using [criterium](https://github.com/hugoduncan/criterium) quick-bench on a Macbook Pro 2.4 GHz Quad-Core Intel Core i5 16GB 2133 MHz LPDDR3
```
$ clj -X:bench

Running bench on raw query...
Evaluation count : 2808 in 6 samples of 468 calls.
             Execution time mean : 227.500900 µs
    Execution time std-deviation : 14.304122 µs
   Execution time lower quantile : 212.980816 µs ( 2.5%)
   Execution time upper quantile : 248.875912 µs (97.5%)
                   Overhead used : 7.019674 ns

Running bench on compiled query...
Evaluation count : 36744 in 6 samples of 6124 calls.
             Execution time mean : 17.599793 µs
    Execution time std-deviation : 894.172439 ns
   Execution time lower quantile : 16.699077 µs ( 2.5%)
   Execution time upper quantile : 18.947053 µs (97.5%)
                   Overhead used : 7.019674 ns

Found 1 outliers in 6 samples (16.6667 %)
	low-severe	 1 (16.6667 %)
 Variance from outliers : 13.8889 % Variance is moderately inflated by outliers
```

## Development

Running tests

```
clj -X:test
```

Issues and PR's welcome. It's best to discuss the design of a potential PR over an issue first if you're worried about spending a lot of time on something that might not get merged.

## Rationale

In his [talk](https://www.youtube.com/watch?v=UCEzBNh9ufs) on literate programming, [Sam Ritchie](https://samritchie.io/) says (cleaned up for writing):

> I have this belief that when you pick up a tool the tool is an instantiation of the philosophy the tool builders put into it. A tool has a _should_ about the way you should work. It's saying something to you about how it should be used...

This library intends to add some leverage to using SQL as it should be used. I would like to extend it to other databases and will also consider re-designing to pass the escaping off to the DB engine if possible.
