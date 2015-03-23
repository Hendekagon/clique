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
  (println "Generating dependency graph for " (first (:source-paths project)))
  (lein/eval-in-project
    (update-in project [:dependencies] conj '[lein-clique "1.0.0-SNAPSHOT"])
    (if (not-empty args) (q (first (:source-paths project)) (apply read-string args)) (q (first (:source-paths project))))
    `(require '[clique.core])
  )
)