(ns org.jakehoward.pg-render.bench
  (:require [org.jakehoward.pg-render :as pgr]
            [criterium.core :refer [quick-bench]]
            [clojure.java.io :as io]))

(defn -main [& args]
  (let [vs {:col         "something_legitimate"
            :schema      "very_well_named"
            :num         1
            :tableOne    "important_data"
            :tableTwo    "very_important_data"
            :tableThree  "very_very_important_data"
            :tableFour   "very_very_very_important_data"
            :xs          [1 2 (bigint 3) (double 55) "ha!"]
            :filter      "very filtery"
            :hackyThing  "'; drop tables *"
            :oths        "NOOOOOOOOOOOOOOOOOOOOOOOOOO"}
        q (slurp (io/resource "sql/bench.sql"))]

    (println "\nRunning bench on raw query...")
    (quick-bench (pgr/render q vs))

    (println "\nRunning bench on compiled query...")
    (let [compiled (pgr/compile-template q)]
      (quick-bench (pgr/render-compiled compiled vs)))))


