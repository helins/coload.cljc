# Coloading Clojure and Clojurescript

[![Clojars](https://img.shields.io/clojars/v/io.helins/coload.svg)](https://clojars.org/io.helins/coload)

[![Cljdoc](https://cljdoc.org/badge/io.helins/coload)](https://cljdoc.org/d/io.helins/coload)

This is a [Shadow-CLJS compilation hook](https://shadow-cljs.github.io/docs/UsersGuide.html#build-hooks).

When running Shadow-CLJS in dev mode, this hook ensures that requested CLJC files
are loaded not only as Clojurescript in JS but also as Clojure on the JVM.

Those both universes are always kept in sync: when the user saves a CLJC file,
its Clojure equivalent is completely reloaded and then its Clojurescript
equivalent is recompiled by Shadow-CLJS.

Not often useful, but when useful, reveals to be **very** useful.


## Example

Clone this repo and start Shadow-CLJS:

```bash
$ ./bin/dev/cljs
```

Modify or touch any of the dev file in `./src/dev/helins/coload`:

```bash
$ touch ./src/dev/helins/coload/dev_2.cljc
```

Output:

```bash
[:dev] Compiling ...
2021-04-09T20:25:30.789Z dvlopt INFO [helins.coload:141] - Will load: #{user helins.coload.dev-2 helins.coload.dev}
:reload helins.coload.dev-2
:reload helins.coload.dev
:hook-test {:src [src/dev], :shadow.build/stage :compile-finish}
[:dev] Build completed. (115 files, 2 compiled, 0 warnings, 0.11s)
```

Commented output:

```bash
# Shadow-CLJS detects change and starts compilation
#
[:dev] Compiling ...

# Coload detects modified namespaces and namespaces that require them
#
2021-04-09T20:25:30.789Z dvlopt INFO [helins.coload:141] - Will load: #{user helins.coload.dev-2 helins.coload.dev}

# Reloading those namespaces in order. Each is meant to print a line from
# Clojure JVM, here is proof:
#
:reload helins.coload.dev-2
:reload helins.coload.dev

# Our plugin function, see "Setup" section
#
:hook-test {:src [src/dev], :shadow.build/stage :compile-finish}

# Then Shadow-CLJS recompiles those same namespaces in Clojurescript
#
[:dev] Build completed. (115 files, 2 compiled, 0 warnings, 0.11s)
```


## Setup

This hook accepts one or several plugins in the `shadow-cljs.edn` configuration
file. Actually, the `shadow-cljs.edn` file from this repository demonstrates
this.

In the dev profile, the `:build-hooks` key accepts a vector of hooks:

```clojure
{:dev
  {:asset-path  "/js"
   :build-hooks [(helins.coload/hook {helins.coload.dev/hook-test {:src ["src/dev"]}})]
   :modules     {:main {:entries [helins.coload.dev]}}
   :output-dir  "cljs/js"
   :target      :browser}}
```

Our hook contains a map of `plugin` -> `paramaters`. Zooming on that:

```clojure
(helins.coload/hook {helins.coload.dev/hook-test {:src ["src/dev"]}})
```

The parameter map must contain the `:src` key-value which points to a vector of
paths containing the CLJC to track for a given plugin. Here, will be tracked all
CLJC files in `src/dev` and any of its sub-directories.

A plugin is a namespaced symbol resolving to a function. Indeed, in
the `helins.coload.dev` we see the `hook-test` function:

```clojure
(defn hook-test

  {:shadow.build/stages #{:compile-finish
                          :configure}}

  [param]

  (println :hook-test param))
```

That function specifies 0 or more compilation steps for which it must execute.
See [build hooks and compilation stage in the Shadow-CLJS
book](https://shadow-cljs.github.io/docs/UsersGuide.html#build-hooks).

It takes as an argument the parameter map specified in the hook with an
additional key `:shadow.build/stage` indicating the current compilation stage.


## API

A couple of additional features to use at the REPL:

```clojure
(require [helins.coload :as coload])


;; Clearing tracker, ensuring that all files will be reloaded next time:
;;
(coload/clear!)


;; Retrieving the number of the current compilation cycle, increments each time:
;;
(coload/compilation-cycle)
```

## License

Copyright © 2021 Adam Helinski

Licensed under the term of the Mozilla Public License 2.0, see LICENSE.
