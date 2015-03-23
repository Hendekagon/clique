(ns clique.core
  "# Clique: function dependency graph visualizations and analysis"
  (:require
    [clique.impl.core :as impl :refer :all]
    [clojure.stacktrace :as st :refer :all]
    [clojure.java.io :as io :refer :all]
    [clojure.tools.namespace.find :as nsf :refer :all]
    [clojure.zip :as zip]
    [clojure.repl :as repl]
    [clojure.java.shell :refer [sh]]
    [plumbing.core :as pc :refer [?>]]
    [lacij.edit.graph :as leg]
    [lacij.view.graphview :as lgv]
    [lacij.layouts.core :as llc]
    [lacij.layouts.layout :as lll]
    [loom [graph :as g]
          [attr :as gattr]
          [io :as gio]]))


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

(defn apply-attrs
  "Graph helper: Take a graph, a node, and apply all k/v pairs in attrs map as attributes of the node"
  [g n attrs]
  (reduce
    (fn [g [k v]]
      (gattr/add-attr g n k v))
    g
    attrs))

; Should multimethod dispatch this
(defn fn-dep-graph
  "Given a function metadata map, return the dependency graph of all functions that
  function depends on"
  [fn-meta]
  (let [fn-deps (fn-dependencies fn-meta)
        graph   (->> (map :fq-name fn-deps)
                     (map vector (repeat (:fq-name fn-meta)))
                     (apply g/digraph))]
    ; For each function metadata map,
    (reduce
      (fn [g fmeta]
        ; And for each key/value pair in that map,
        (apply-attrs g (:fq-name fmeta) fmeta))
      graph
      (conj fn-deps fn-meta))))

(defn add-digraphs
  "The `g/digraph` function when called on existing graphs fails to preserve attributes.
  This function fixes that."
  [& gs]
  (let [g (reduce g/digraph gs)
        node-attr-map (mapcat
                        (fn [g]
                          (map (fn [n] [n (gattr/attrs g n)]) (g/nodes g)))
                        gs)]
    (reduce
      (fn [g [n attrs]]
        (apply-attrs g n attrs))
      g
      node-attr-map)))

; Also, should dispatch for this
(defn ns-dependencies
  "Given a namespace symbol, return a dependency graph for all functions that namespace's
  functions depend on."
  [ns]
  (->> (ns-functions ns)
       (map fn-dep-graph)
       (apply add-digraphs)))

;; ## Filtering functions
;;
;; Here we have functions for filtering our dependency graphs by namespace.

(defn g-attribute-filter
  "Return a new graph which contains nodes in graph for which keep-fn is truthy with respect
  to the given attribute"
  [graph attr keep-fn]
  (->> (g/nodes graph)
       ; We remove the things we are going to keep, because what's left is what gets passed to remove-nodes
       (remove
         #(keep-fn (gattr/attr graph % attr)))
       (apply g/remove-nodes graph)))

(defn ns-restrict
  "Restrict a dependency graph to the specified namespaces. There are 4 kw-args:

  * `:include` - namespace object, symbol, string or collection thereof; any function with namespace
    not in this list will be removed.
  * `:exclude` - namespace object, symbol, string or collection thereof; any function with namespace
    in this list will be removed.
  * `:include-re`, `:exclude-re` - regex, regex string, or collection thereof; behave analogous to
    `:include` and `:exclude`.

  If there are namespaces matching both an inclusion and an exclusion, those namepsaces will be
  removed."
  [dep-g & {:keys [include exclude include-re exclude-re]}]
  (let [nss (map #(str (gattr/attr dep-g % :ns)) (g/nodes dep-g))
        ; Make sure these either stay nil, or are seqs of strings (if a single ns, wrap in [])
        [include exclude]
        (map
          (fn [ns]
            (when ns
              (if (coll? ns)
                (map str ns)
                [(str ns)])))
          [include exclude])
        ; Allow strings as regular expressions; also wrap in a collection if not already in one;
        ; leave nil if already nil
        [include-re exclude-re]
        (map
          (fn [re-or-res]
            (when re-or-res
              (if (coll? re-or-res)
                (map re-pattern re-or-res)
                [(re-pattern re-or-res)])))
          [include-re exclude-re])
        ; First check if we are doing inclusions, ow assume all nss are included
        included (if (or include include-re)
                   (-> (set (if (coll? include) include [include])) ; have to make sure it's the right set
                       (into (mapcat
                               (fn [re]
                                 (filter (partial re-matches re) nss))
                               include-re)))
                   (set nss))
        ; Now remove any needed
        included (->> included
                      (remove (set exclude)) ; if exclude nil, this doesn't do anythign
                      (remove (fn [ns]
                                (some
                                  #(re-matches % ns)
                                  exclude-re)))) ; similarly, from above, if exclude-re is nil, it becomes []
        keep? (fn [ns] ((set included) (str ns)))]
    (g-attribute-filter dep-g :ns keep?)))

(def ^:dynamic default-exclude
(defn project-dependencies
  "Returns a dependency graph of functions found in all namespaces within dir `src-dir`"
  [src-dir]
  (->> (find-namespaces-in-dir (file src-dir))
       (map ns-dependencies)
       (reduce add-digraphs)))

  "Default namespaces to exclude from dependency graphs. Can be rebound."
  ["clojure" "java" "System" ""])

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

