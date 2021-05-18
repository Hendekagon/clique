(ns clique.core
  "
    Function dependency graph generation
  "
  (:require
    [clojure.tools.namespace.find :as nsf]
    [loom [graph :as lg]
          [attr :as la]
          [io :as lio]]
    [clojure.java.io :as io]))

(defn get-namespace-forms [file]
  (read-string (str "[" (slurp file) "]")))

(defn get-ns-defs
  ([file]
    (get-ns-defs file (get-namespace-forms file)))
  ([file [[_ ns-name :as ns-dec] & forms :as nsf]]
   (require ns-name)
   (sequence
     (comp
       (filter (comp #{'defn 'defmacro 'def} first))
       (map (fn [form] (with-meta form {:ns-name ns-name}))))
     forms)))

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
        syms (filter symbol? (tree-seq list? seq a-def))
        [deff fq-name & that] (remove nil? (map (partial fqsym ns-name) syms))
       ]
     (assoc m
       :depends-on (sequence (comp (remove (comp ignore namespace)) (remove (into #{} syms))) that)
       :fq-name fq-name
       :kind (if (= "defn" (name deff)) :function :macro)
       )))

(defn as-graph
  "Return the given dependencies as a graph"
  [deps]
  (apply lg/digraph
    (mapcat
      (fn [{:keys [depends-on fq-name ns-name]}]
        (cons [ns-name fq-name] (map (fn [d] [fq-name d]) depends-on))) deps)))

(defn project-dependencies
  "Returns a dependency graph of functions
   found in all namespaces from path"
  ([path]
    (project-dependencies {:ignore #{"clojure.core"}} path))
  ([{:keys [ignore] :as params} path]
    (->>
       (io/file path)
       (nsf/find-sources-in-dir)
       (mapcat get-ns-defs)
       (map (partial get-deps params)))))

(defn view-deps [path]
  (-> path
    project-dependencies
    as-graph
    lio/view))



(comment


  (view-deps ".")


)