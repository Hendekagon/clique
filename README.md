# Clique

Some code for generating function dependency graphs

## Usage


`Hendekagon/clique {:git/url "https://github.com/Hendekagon/clique.git" :sha "29859d561e27d4e6b9444798f385a7840a259a52"}`

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
