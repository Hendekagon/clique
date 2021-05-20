# Clique

Some code for generating function dependency graphs

## Usage


`Hendekagon/clique {:git/url "https://github.com/Hendekagon/clique.git" :sha "11ba7c36ab7edfbef9341d697846b56dcb6b1c4d"}`

in the `:extra-deps` of one of your `~/.clojure/deps.edn`'s aliases

Clique goes through your source code to find which functions depend on which other functions

```
(require '[clique.core :as cc])

(cc/view-deps ".")
```




## License

Copyright © 2013 Matthew Chadwick

Distributed under the Eclipse Public License, the same as Clojure.
