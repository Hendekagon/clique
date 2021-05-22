(ns clique.core
  "
    Function dependency graph generation
  "
  (:require
    [clojure.tools.namespace.find :as nsf]
    [clojure.java.io :as io]
    [loom [graph :as lg]
          [attr :as la]
          [io :as lio]]))

(defn get-namespace-forms [filename]
  (let [read-params {:eof nil}]
    (with-open [r (java.io.PushbackReader. (io/reader filename))]
      (binding [*read-eval* false]
        (loop [forms [] form (read read-params r)]
          (if form
            (recur (conj forms form) (read read-params r))
            forms))))))

(defn get-ns-defs
  ([filename]
    (get-ns-defs {} filename))
  ([params file]
    (get-ns-defs params file (get-namespace-forms file)))
  ([{include-defs :include-defs :or {include-defs #{'defn 'defmacro}}}
     file [[_ ns-name :as ns-dec] & forms :as nsf]]
   (if (try (do (require ns-name) true) (catch Exception e false))
     (sequence
      (comp
        (filter (comp include-defs first))
        (map (fn [form] (with-meta form {:ns-name ns-name}))))
      forms)
     '())))

(defn fqsym
  "Returns the fully qualified symbol s in namespace ns"
  ([ns s]
    (fqsym (meta (ns-resolve ns s))))
  ([{ans :ns sym :name :as m}]
   (if m
     (with-meta (symbol (str ans) (name sym)) m)
     nil)))

(defn get-deps
  "Return the dependencies of the function or macro
   defined by a-def"
  [{:keys [ignore] :or {ignore #{}}} a-def]
  (let [{ns-name :ns-name :as m} (meta a-def)
        syms (filter symbol? (tree-seq seqable? seq a-def))
        [deff fq-name & that] (remove nil? (map (partial fqsym ns-name) syms))
       ]
     (assoc m
       :depends-on (sequence (comp (filter (comp seq :arglists meta)) (remove (comp ignore namespace)) (remove (into #{} syms))) that)
       :fq-name fq-name
       :kind (if (= "defmacro" (name deff)) :macro :function))))

(defn as-graph
  "Return the given dependencies as a graph"
  [deps]
  (apply lg/digraph
    (mapcat
      (fn [{:keys [depends-on fq-name ns-name]}]
        (map (fn [d] [fq-name d]) depends-on)) deps)))

(defn project-dependencies
  "Returns a dependency graph of functions
   found in all namespaces from path"
  ([path]
    (project-dependencies
      {:ignore #{"clojure.core"}} path))
  ([{:keys [ignore] :as params} path]
    (->>
       (io/file path)
       (nsf/find-sources-in-dir)
       (mapcat (partial get-ns-defs params))
       (map (partial get-deps params)))))

(def default-params
  {:graphviz
     {:fmt :pdf :alg :dot
      :graph {:ratio 0.618}
      :node  {:shape :record :fontsize 10}}
    :ignore #{"clojure.core"}
    :include-defs #{'defn 'defmacro}})

(defn view-deps
  ([]
    (view-deps default-params "."))
  ([{view-opts :graphviz :as params} path]
    (-> path
       ((partial project-dependencies params))
       as-graph
       ((partial apply lio/view) (mapcat identity view-opts)))))

(defn run [{path :path :as params}]
  (do
    (view-deps (merge default-params params) (str (or path ".")))
    (Thread/sleep 1000)
    (System/exit 0)))

(comment

  (view-deps)

)