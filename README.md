# lein-clique

A Leiningen plugin for generating function dependency graphs.

## Usage

Put `[lein-clique "0.1.1"]` into the `:plugins` vector
of your [profile](https://github.com/technomancy/leiningen/blob/stable/doc/PROFILES.md)


lein-clique goes through your source code to find which functions external to a function's
namespace it depends on, then generates a graphviz graph of those dependencies.
If you analyze the graph, you can find for example which functions are most used
by other functions (in-degree - in the Clojure core namespace these are: concat, seq and list),
or which functions are most dependent on other functions.


Example:

cd into your project and:

    $ lein clique

by default, namespaces starting with 'clojure' are excluded
so if you want to *include* clojure's namespaces do:

	$ lein clique []

or to filter out namespaces beginning with certain strings:

    $ lein clique [\"somenamespace\" \"anothernamespace\"]

A graphviz file called deps.dot is then generated, which you can run
through a graph visualization tool like Gephi to make it look nice.

Using Clique without leiningen:

You can also just use Clique as a library by adding `[lein-clique "0.1.1"]` to your project.clj's :dependencies vector, then:

	=> (require '[clique.core :as c])
	=> (c/all-deps "./src")

will return a map of all functions in namespaces in you source path to the functions they depend on
(including duplicates, in case you want to know frequencies)

	=> (pprint (sort-by val (frequencies (mapcat val (c/all-deps "./src")))))

To get such a map for a particular namespace do:

	=> (c/all-fq (c/dependencies 'your.namespace))

## License

Copyright © 2013 Matthew Chadwick

Distributed under the Eclipse Public License, the same as Clojure.
