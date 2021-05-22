# Clique

Some code for generating function dependency graphs

## Usage


`Hendekagon/clique {:git/url "https://github.com/Hendekagon/clique.git" :sha "8b6a331f2e7345091f1e2f5ff670e46c7d61add8"}`

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
