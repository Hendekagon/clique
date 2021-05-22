# Clique

Some code for generating function dependency graphs

## Usage


`Hendekagon/clique {:git/url "https://github.com/Hendekagon/clique.git" :sha "05ffaaf7908118834f8936918979158f56cfc7b4"}`

in the `:extra-deps` of one of your `~/.clojure/deps.edn`'s aliases

from your project root:

`clj -A:my-alias-containing-clique -X clique.core/run`

or

```
(require '[clique.core :as c])

(c/view-deps)
```

See the source for more options



## License

Copyright © 2013 Matthew Chadwick

Distributed under the Eclipse Public License, the same as Clojure.
