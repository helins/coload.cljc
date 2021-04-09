;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at https://mozilla.org/MPL/2.0/.


(ns helins.coload.dev

  "When those dev namespaces are reloaded in CLJS, they should also get reloaded
   from Clojure and print a message."

  {:author "Adam Helinski"}

  (:require [helins.coload.dev-2]))


;;;;;;;;;;


#?(:clj (println :reload (str *ns*)))



(comment


  )
