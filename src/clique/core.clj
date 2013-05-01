(ns clique.core
  (:use
    clojure.tools.logging
    clojure.stacktrace
    clojure.java.io
    clojure.tools.namespace.find
    ;clj-ns-browser.sdoc
    )
  (:require
    [clojure.zip :as zip]
    [clojure.repl :as repl]
    [lacij.edit.graph :as leg]
    [lacij.view.graphview :as lgv]
    [lacij.layouts.core :as llc]
    [lacij.layouts.layout :as lll]
  )
)

(defn functions
"Returns all the functions in the namespace ns"
  ([ns] ;(println "require " y)
    (try
      ((fn [] (require ns) (filter :arglists (map meta (vals (ns-publics ns))))))
      (catch Exception e
        ((fn [] (println "Problem requiring " ns " > " e) (print-cause-trace e) []))))
  )
)

(defn fqns
  "Returns the fully qualified namespace of the given symbol s in namespace ns"
  ([ns s]
    (if-let [rns (-> (ns-resolve ns s) meta :ns)] (symbol (str rns) (name s)) s)
  )
)

(defn seq-map-zip [x]
  (zip/zipper
    (fn [n] (or (seq? n) (map? n) (vector? n)))
    (fn [b] (if (map? b) (seq b) b))
    (fn [node children] (with-meta children (meta node)))
    x))

(defn zip-nodes [x] (take-while (complement zip/end?) (iterate zip/next (seq-map-zip x))))

(defn symbols [x] (filter symbol? (map zip/node (zip-nodes x)))) ; also returns Java classes

(defn namespaced-symbols [expression] (filter namespace (symbols expression)))

(defn dependencies
  "returns all functions used by each function in the given namespace"
  ([namespace]
  (dependencies namespace (functions namespace)))
  ([namespace functions]
    (into
      {}
      (filter (comp not-empty second)
        (map
          (fn [[fn-name source]]
            [(symbol (str namespace) (str fn-name)) (symbols (read-string source))])
          (filter (comp identity second)
            (map vector
              (map :name functions)
              (map (comp repl/source-fn symbol (partial str namespace \/) :name) functions)))))
    )
  )
)

(defn except
  "Filter out symbols not in exclude"
  [sc exclude]
  (filter (comp
            (fn [^String ns]  (not-any? (fn [^String s] (.startsWith ns s)) exclude))
            namespace) sc)
)

(defn filtered
  "Filter out symbols in exclude"
  [ds exclude]
  (reduce
    (fn [r [f sc]]
      (assoc r f
       (except (map (partial fqns (symbol (namespace f))) (filter (comp identity namespace) sc)) exclude)
        )
    )
    {}
  ds)
)

(defn default-exclude [] ["clojure" "java" "System"])

(defn project-dependencies
"Returns a list of all functions found in all namespaces under the given path dir
and their dependent functions
"
  [dir exclude]
  (filtered (mapcat dependencies (find-namespaces-in-dir (file dir))) exclude)
)

(defn nodes [deps] (set (mapcat cons (keys deps) (vals deps))))

(defn edges [deps] (set (mapcat (fn [k v] (map vector (repeat k) v)) (keys deps) (vals deps))))

(defn export-graphviz
  ([nodes edges name]
    (println "Creating dependency graph " (str name ".dot") ", " (count nodes) " nodes "(count edges) " edges")
    (spit (str name ".dot")
      (apply str
        (concat [(str "digraph " name " {")]
          (map (fn [[s d]] (str "\"" (str s) "\"" " -> " "\"" (str d) "\"" ";\n")) edges)
          (map (fn [n] (str "\"" (str n) "\"" "[label=\"" (str n) "\"];\n")) nodes)
          ["}"])
        )
      )
    )
  ([dir] (export-graphviz dir (default-exclude)))
  ([dir exclude]
    (println "Excluding " exclude)
    ((fn [ds]
      (export-graphviz (nodes ds) (edges ds) (str  "deps")))
      (project-dependencies dir exclude)))
)

(defn graph
  ([deps] (graph (-> (leg/graph :width 512 :height 512) (leg/add-default-node-attrs :width 25 :height 25 :shape :circle)) deps))
  ([g deps]
    (reduce (fn [g [s d]] (leg/add-edge g (keyword (str (name s) "-" (name d))) s d))
      (reduce (fn [g n] (leg/add-node g n (name n))) g (nodes deps)) (edges deps)))
  )

(defn export-graph*
  ([ns]
    (-> (graph (dependencies ns)) (lll/layout :naive) (leg/build) (lgv/export (str "./" ns ".svg") :indent "yes"))
    )
  ([path output-name]
    (->
      (reduce
        graph
        (-> (leg/graph :width 1024 :height 1024) (leg/add-default-node-attrs :width 25 :height 25 :shape :circle))
        (map dependencies (find-namespaces-in-dir (file path)))
        )
      (lll/layout :naive) (leg/build)
      (lgv/export output-name :indent "yes")
      )
    )
  )
