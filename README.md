# Pg-Render


## Benchmarks

Using [criterium](https://github.com/hugoduncan/criterium) quick-bench
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
