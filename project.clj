(defproject lein-clique "0.1.0-SNAPSHOT"
  :description "Make dependency graphs of Clojure functions"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :eval-in-leiningen true
  :source-paths ["./src"]
  :dependencies
  [
    [org.clojure/clojure "1.5.1"]
    [org.clojure/math.numeric-tower "0.0.2"]
    [org.clojure/data.zip "0.1.1"]
    [org.clojure/tools.namespace "0.2.0"]
    [clj-ns-browser "1.3.1"]
    [hiccup "1.0.0-beta1"]
    [lacij "0.8.0"]
  ]
)