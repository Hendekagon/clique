(defproject lein-clique "1.0.0-SNAPSHOT"
  :description "Make dependency graphs of Clojure functions"
  :url "https://github.com/Hendekagon/lein-clique"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :eval-in-leiningen true
  :signing {:gpg-key "F5DE1E30"}
  :source-paths ["src"]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [aysylu/loom "0.5.0"]
                 [prismatic/plumbing "0.4.1"]
                 [org.clojure/tools.namespace "0.2.10"]]
  :profiles {:dev {:dependencies [[org.clojure/math.numeric-tower "0.0.2"]
                                  [clj-ns-browser "1.3.1"]]}})
