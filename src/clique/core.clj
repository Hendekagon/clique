(ns clique.core
  "# Clique: function dependency graph visualizations and analysis"
  (:require
    [clojure.stacktrace :as st :refer :all]
    [clojure.java.io :as io :refer :all]
    [clojure.tools.namespace.find :as nsf :refer :all]
    [clojure.zip :as zip]
    [clojure.repl :as repl]
    [lacij.edit.graph :as leg]
    [lacij.view.graphview :as lgv]
    [lacij.layouts.core :as llc]
    [lacij.layouts.layout :as lll]))



;; ## Base functions for building a dependency graph given a namespace"
;;
;; First we need to get all the functions from a namespace:


(defn ns-functions
  "Returns all the functions or macros in the namespace ns"
  [ns]
  (try
    ; XXX Wonder if we could use a static analyzer for this? Probably more trouble than worth.
    (require ns)
    (->> (ns-publics ns)
         (vals)
         (map meta)
         (filter :arglists))
    (catch Exception e
      nil)))

(defn fqns
  "Returns the fully qualified namespace of the given symbol s in namespace ns"
  [ns s]
  (if-let [rns (-> (ns-resolve ns s) meta :ns)]
    (symbol (str rns) (name s)) s))

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

(defn namespaced-symbols
  [expression]
  "Returns symbols which have namespaces"
  (filter namespace (sexp-symbols expression)))

(defn dependencies
  "Returns a map of all functions in the given namespace, used by each function in the given namespace"
  ([namespace]
  (dependencies namespace (ns-functions namespace)))
  ([namespace functions]
   (->> functions
        (map (comp repl/source-fn symbol (partial str namespace \/) :name))
        (map vector (map :name functions))
        (filter second)
        (map (fn [[fn-name sexp]]
               [(symbol (str namespace) (str fn-name))
                (map 
                  (partial fqns namespace)
                  (sexp-symbols (read-string sexp)))]))
        ; XXX Hmm... do we really want this for a complete graph? Empty nodes should be fine
        (filter (comp not-empty second))
        (into {}))))


;; ## Filtering functions
;;
;; Here we have functions for filtering our dependency graphs by namespace.

;: XXX The following 4 functions REALLY need to be refactored


(defn ns-remove
  "Filters out fully qualified function/macro names with namespace in exclude"
  ; XXX Should switch the order here, since fq-fnames is really the collection of interest
  [fq-fnames exclude]
  (filter
    (comp
      (fn [ns]
        (not-any? #(.startsWith (str ns) %) (map str exclude)))
      namespace)
    fq-fnames))

(defn ns-filter
  "Filters fully qualified function/macro names to only those given in include"
  ; XXX Should switch the order here, since fq-fnames is really the collection of interest
  [fq-fnames include]
  (filter
    (comp
      (fn [ns]
        (some #(.startsWith (str ns) %) (map str include)))
      namespace)
    fq-fnames))

(defn deps-ns-remove
  "Remove functions from dependencies which are in the given exclude namespaces"
  [deps exclude]
  (reduce
    (fn [r [f sc]]
      (assoc r f
       (ns-remove
         (map (partial fqns (symbol (namespace f))) (filter (comp identity namespace) sc))
         exclude)))
    {}
    deps))

(defn deps-ns-filter
  "Remove functions from dependencies which are in the given exclude namespaces"
  [deps include]
  ; XXX Should check here that include is a collection and make it so if not
  (reduce
    (fn [r [f sc]]
      (assoc r f
       (ns-filter
         (map (partial fqns (symbol (namespace f))) (filter (comp identity namespace) sc))
         include)))
    {}
    deps))

(defn all-fq
  "Filter out symbols in exclude"
  [deps]
  (reduce
    (fn [r [f sc]]
      (assoc r f (map (partial fqns (symbol (namespace f))) sc)))
    {}
    deps))

(defn default-exclude [] ["clojure" "java" "System"])

(defn project-dependencies
  "Returns a list of all functions found in all namespaces under the given path dir
  and their dependent functions"
  [dir exclude]
  (deps-ns-remove (mapcat dependencies (find-namespaces-in-dir (file dir))) exclude))

(defn all-deps
  "Returns a list of all functions found in all namespaces under the given path dir
  and their dependent functions"
  [dir]
  (all-fq (mapcat dependencies (find-namespaces-in-dir (file dir)))))

(defn nodes [deps] (set (mapcat cons (keys deps) (vals deps))))

(defn edges [deps] (set (mapcat (fn [k v] (map vector (repeat k) v)) (keys deps) (vals deps))))

(defn export-graphviz
  ; All filtering should happen before this; poor separation of concerns
  ([nodes edges name]
    (println "Creating dependency graph " (str name ".dot") ", " (count nodes) " nodes "(count edges) " edges")
    (spit (str name ".dot")
      (apply str
        (concat [(str "digraph " name " {")]
          (map (fn [[s d]] (str \" s "\" -> \"" d "\";\n")) edges)
          (map (fn [n] (str \" n "\" [label=\"" n "\"];\n")) nodes)
          ["}"]))))
  ([dir] (export-graphviz dir (default-exclude)))
  ([dir exclude]
    (println "Excluding " exclude)
    ((fn [ds]
      (export-graphviz (nodes ds) (edges ds) (str  "deps")))
      (project-dependencies dir exclude))))

;; This can now be used like this:
;;     (-> (dependencies 'clojure.tools.cli)
;;         (deps-ns-filter ['clojure.tools.cli\/])
;;         ((fn [ds] (export-graphviz (nodes ds) (edges ds) "tools.cli"))))

(defn lacij-graph
  ([deps] (lacij-graph (-> (leg/graph :width 512 :height 512) (leg/add-default-node-attrs :width 25 :height 25 :shape :circle)) deps))
  ([g deps]
    (reduce
      (fn [g [s d]]
        (leg/add-edge g (keyword (str (name s) "-" (name d))) s d))
      (reduce (fn [g n] (leg/add-node g n (name n))) g (nodes deps))
      (edges deps))))

(defn export-graph*
  ([ns]
    (-> (lacij-graph (dependencies ns))
        (lll/layout :naive)
        (leg/build)
        (lgv/export (str "./" ns ".svg") :indent "yes")))
  ([path output-name]
    (->
      (reduce
        lacij-graph
        (-> (leg/graph :width 1024 :height 1024)
            (leg/add-default-node-attrs :width 25 :height 25 :shape :circle))
        (map dependencies (find-namespaces-in-dir (file path))))
      (lll/layout :naive) (leg/build)
      (lgv/export output-name :indent "yes"))))

