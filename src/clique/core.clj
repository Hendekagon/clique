(ns clique.core
  "# Clique: function dependency graph visualizations and analysis"
  (:require
    [clique.impl.core :as impl :refer :all]
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

(defn fqns
  "Returns the fully qualified namespace of the given symbol s in namespace ns."
  [ns s]
  (if-let [rns (-> (ns-resolve ns s) meta :ns)]
    (symbol (str rns) (name s))
    s))

(defn assoc-fqns
  "Assoc the fully qualified function names into a function meta map."
  [fmeta]
  (assoc fmeta :fq-name (fqns (:ns fmeta) (:name fmeta))))

(defn ns-functions
  "Returns all the functions or macros in the namespace ns."
  [ns]
  (try
    ; XXX Wonder if we could use a static analyzer for this? Probably more trouble than worth.
    (require ns)
    (->> (ns-publics ns)
         (vals)
         (map meta)
         (filter :arglists)
         (map assoc-fqns))
    (catch Exception e
      nil)))

(defn fn-dependencies
  "Takes function metadata and computes a loom dependency graph based on the function source."
  [{:keys [ns name] :as fn-meta}]
  (->> (fn-source fn-meta)
       (read-string)
       (sexp-symbols)
       (map
         (fn [sym]
           (let [fq-name (fqns ns sym)]
             (-> fq-name resolve meta (assoc :fq-name fq-name)))))))

(defn fn-dep-graph
  [fn-meta]
  (let [fn-deps (fn-dependencies fn-meta)
        graph   (->> (map :fq-name fn-deps)
                     (map vector (repeat (:fq-name fn-meta)))
                     (apply g/digraph))]
    ; For each function metadata map,
    (reduce
      (fn [g fmeta]
        ; And for each key/value pair in that map,
        (reduce
          (fn [g [k v]]
            ; Add that attribuate to the node
            (gattr/add-attr g (:fq-name fmeta) k v))
          g
          fmeta))
      graph
      (conj fn-deps fn-meta))))


(defn ns-dependencies
  [ns]
  (->> (ns-functions ns)
       (map fn-dep-graph)
       (reduce g/digraph)))


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

