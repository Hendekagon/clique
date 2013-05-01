(ns leiningen.clique
"Generate a graph of dependencies between the functions in your project"
  (:require
    [leiningen.core.eval :as lein]
    )
  )

(defmacro q
  ([p e] ``(clique.core/export-graphviz ~~p ~~e))
  ([p] ``(clique.core/export-graphviz ~~p))
)

(defn clique
  [project & args]
  (println "generating dependency graph for " (first (:source-paths project)))
  ;(println project)
  ;(println "args: " args (type args) (apply read-string args) (type (apply read-string args)))
  (lein/eval-in-project
    (update-in project [:dependencies] (partial apply conj) ['[lein-clique "0.1.0-SNAPSHOT"] '[org.clojure/clojure "1.5.1"]])
    (if (not-empty args) (q (first (:source-paths project)) (apply read-string args)) (q (first (:source-paths project))))
    `(require '[clique.core])
  )
)