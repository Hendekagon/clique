# Clique

Some code for generating function dependency graphs

## Usage


`Hendekagon/clique {:git/url "https://github.com/Hendekagon/clique.git" :sha "8b6a331f2e7345091f1e2f5ff670e46c7d61add8"}`

in the `:extra-deps` of one of your `~/.clojure/deps.edn`'s aliases

Clique goes through your source code to find which functions depend on which other functions

`clj -A:clique-alias -X clique.core/run`

or

```
(require '[clique.core :as cc])

(cc/view-deps ".")
```




## License

Copyright © 2013 Matthew Chadwick

Distributed under the Eclipse Public License, the same as Clojure.
