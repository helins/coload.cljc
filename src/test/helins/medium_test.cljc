(ns helins.medium-test

  ""

  {:author "Adam Helinski"}

  (:require [clojure.test  :as t]
            [helins.medium :as medium])
  #?(:cljs (:require-macros [helins.medium-test])))  ;; Just in case. In prior commits, this was ultimately throwing.


;;;;;;;;;;


(t/deftest target

  (let [x (medium/target*)]
    (t/is #?(:clj  (= :clojure
                      x)
             :cljs (#{:cljs/dev
                      :cljs/release} x))
          "Current target is set accordingly")

    #?(:clj (t/is (= x
                     (medium/target nil))
                  "Macro returns the same result as the function"))))


;;;;;;;;;;


(t/deftest when-target*

  (t/is (medium/when-target* #?(:clj  :clojure
                                :cljs #{:cljs/dev
                                        :cljs/release})
          true)))


;;;;;;;;;;


(def expand-test-value
     420)



(def expand-test-var)



(medium/expand*
  (let [x 1]
    `(alter-var-root (var expand-test-var)
                     (constantly ~(+ expand-test-value
                                     x)))))


(t/deftest expand*

  (t/is (= (+ 1
              expand-test-value)
           expand-test-var)
        "Var was defined during expansion"))



#?(:clj (def *a (atom false)))


#?(:clj (def b true))


(medium/when-compiling*
  (reset! *a
          b))



(t/deftest when-compiling*

  (t/is (medium/expand* @*a)))


;;;;;;;;;;


(t/deftest load-edn*


  (t/is (= {:a ['b "c" #{4}]}
           (medium/load-edn* "./resources/load.edn"))))
