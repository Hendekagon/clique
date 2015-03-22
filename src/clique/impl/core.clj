(ns clique.impl.core
  (:require [clojure.zip :as zip]
            [clojure.repl :as repl]))


(defn fn-source
  "Based on function metadata, get the source sexp of that function"
  [fn-meta]
  (repl/source-fn
    (symbol
      (str (:ns fn-meta) \/ (:name fn-meta)))))

; This or something else should really drop the names of the functions themselves
(defn seq-map-zip [sexp]
  (zip/zipper
    (fn [n] (or (seq? n) (map? n) (vector? n)))
    (fn [b] (if (map? b) (seq b) b))
    (fn [node children] (with-meta children (meta node)))
    sexp))

(defn sexp-symbols
  "Returns symbols and Java classes from source"
  [sexp]
  (->> (seq-map-zip sexp)
       (iterate zip/next)
       (take-while (complement zip/end?))
       (map zip/node)
       (filter symbol?)))

