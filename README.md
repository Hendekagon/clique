# lein-clique

A Leiningen plugin for generating function dependency graphs.

## Usage

Put `[lein-clique "0.1.1"]` into the `:plugins` vector of your
`:user` profile, or if you are on Leiningen 1.x do `lein plugin install
lein-clique 0.1.1`.


lein-clique goes through your source code to find which functions external to a function's
namespace it depends on, then generates a graphviz graph of those dependencies.
If you analyze the graph, you can find for example which functions are most used
by other functions (in-degree - in the Clojure core namespace these are: concat, seq and list),
or which functions are most dependent on other functions.



Example:

cd into your project and:

    $ lein clique

and to filter out namespaces beginning with certain strings:

    $ lein clique [\"clojure\"]

for example, would exclude anything in the clojure namespace from the graph,
this generates a graphviz file called deps.dot which you can run through Gephi or
something to make it look nice.

## License

Copyright © 2013 Matthew Chadwick

Distributed under the Eclipse Public License, the same as Clojure.
