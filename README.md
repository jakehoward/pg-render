# pg-render

pg-render is an opinionated SQL templating library for PostgreSQL.

I _do_ like writing sql, I _don't_ like positional parameters. See end of README for rationale.

Currently in "alpha" because I haven't tested this in the wild. I'll publish it as 1.0.0 once I've kicked the tyres on it in a real project.

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

In his [talk on literate programming](https://www.youtube.com/watch?v=UCEzBNh9ufs), [Sam Ritchie](https://samritchie.io/) says (cleaned up for writing):

> I have this belief that when you pick up a tool the tool is an instantiation of the philosophy the tool builders put into it. A tool has a _should_ about the way you should work. It's saying something to you about how it should be used...

This library takes quite a bold stance.

- Work with raw SQL
- Use a different library if you're not using PostgreSQL (I have nothing against other databases)

What are the alternatives? There appear to be two ways to work with SQL: write raw sql; or use an abstraction layer like [honeysql](https://github.com/seancorfield/honeysql).

Abstraction layers are popular and I don't object to that. I picked honeysql for this discussion because it looks like an excellent example of the "other way of doing it" and you should check it out.

Here's my take on it.

### Raw SQL

Pros:
- No mental mapping between the SQL you want and what you write in the editor

Cons:
- Poor to middling editor experience (Clojure strings are especially painful)
- Hacking strings isn't fun
- Tricky to get query re-use
- No leverage to do clever things with the query because it's a string

### Abstraction layer

Pros:
- Better editor experience
- Leverage because you have your query as data
- Query re-use
- Possible entry point to add extra sugar (warnings on things that look unintentional, etc)

Cons:
- Adds nothing to simple things
- Gets in the way of complex things
- Extra layer to introduce bugs, complexity, confusion
- Extra thing to learn
- It's not a materially different way of interacting with the database, you're still outputting SQL at the end of it all.
- Workflow of "try it out in the sql console and then copy to prod" sucks if you have two different representations (slightly better in Clojure + honeysql because of the REPL and honeysql having a good mechanism to get sql out)

### Discussion

I feel strongly about preferring to use raw SQL. There are downsides, this library attempts to mitigate some of them.

Here's part of a query I wrote the other day, (heavily redacted and re-named):

```sql
with ... as (
  select
    coalesce(
      json_agg(
        json_build_object(
          'id', e._entity_id,
          'fee',
            case when e.fee is not null then
              json_build_object(
                'pricePerUnit', price.price_per_unit,
                ...
                'currency', json_build_object(
                  'name', currency.name,
                  'code', currency.code
                )
              )
            else null
            end,
          'billingModel', e.billing_model,
          ...
          'isTrial', e.is_trial
        ) order by ids.row_num),
      '[]') as e
  from
    table t
  cross join lateral unnest(t.array_col) with ordinality as ids(id, row_num)
  left join
    ...
  group by ...
),
```

You may recoil at the sight of this. Perhaps you should. But in the balance of the tradeoffs presented to me, this is where I ended up. I definitely don't want to write this in an abstraction layer. I do want to copy and paste it into a SQL console (like a REPL for SQL) to try it, re-use it, modify it, etc. It's worth noting that I wasn't in a Clojure environment so it's more painful to experiment "inline".

If I had to use an abstraction layer, I would have either opted out and written the raw sql anyway or been subtly pushed down the path of getting the raw data out of the database and stitching it together in code (bugs ahoy!).

I see SQL as a powerful tool that gives you more options for solving problems, it's my opinion that abstraction layers introduce downsides that outweigh their upsides. I tend to write SQL in a `.sql` file and then use a convention to load them by name rather than write inline in code.

All that being said, you'd have to be deranged to use _positional_ sql parameters. Hence pg-render.

I'm open to being convinced otherwise.

### PostgreSQL only?

I think it's achievable to make this library work for PostgreSQL and:
- be a pleasure to use
- allow some PostgreSQL oddities to creep in as first class citizens
- be performant
- be free of defects

I think writing it to allow other database engines could compromise some or all of those upsides.

I could be convinced otherwise (and in this case I'll fork, modify and publish under a different name).

### In conclusion

This library is _very_ opinionated, but I'm not. Perhaps you agree with its stance and get some value from it. If so, great!
