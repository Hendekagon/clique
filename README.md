# lein-clique

A Leiningen plugin for generating function dependency graphs.

## Usage

Put `[lein-clique "0.1.0-SNAPSHOT"]` into the `:plugins` vector of your
`:user` profile, or if you are on Leiningen 1.x do `lein plugin install
lein-clique 0.1.0-SNAPSHOT`.


Example:

cd into your project and:

    $ lein clique

and to filter out namespaces beginning with certain strings:

    $ lein clique [\"clojure\"]

for example, would exclude anything in the clojure namespace from the graph

## License

Copyright © 2013 Matthew Chadwick

Distributed under the Eclipse Public License, the same as Clojure.
