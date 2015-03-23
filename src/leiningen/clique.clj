(ns leiningen.clique
  "Generate a graph of dependencies between the functions in your project"
  (:require
    [leiningen.core.eval :as lein]
    [leiningen.core.main :as lmain]))

(defmacro q
  ([p e] ``(apply clique.core/lein-main ~~p ~~e))
  ([p] ``(clique.core/lein-main ~~p)))

(defn symbol-or-string
  [s]
  (if (= (first s) \:)
    (symbol s)
    s))

(defn clique
  [project & args]
  ; XXX Should we be doing this for all x in (:source-paths project)?
  (lmain/info "Generating dependency graph for" (first (:source-paths project)))
  (lein/eval-in-project
    ; Add lein-clique to the project dependencies
    (update-in project [:dependencies] conj '[lein-clique "1.0.0-SNAPSHOT"])
    `(clique.core/lein-main ~(first (:source-paths project)) ~@(mapv symbol-or-string args))
    `(require '[clique.core])))

