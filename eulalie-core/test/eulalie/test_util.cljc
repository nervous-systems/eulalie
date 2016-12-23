(ns eulalie.test-util
  (:require [promesa.core :as p]
            #?(:clj  [clojure.test :as t]
               :cljs [cljs.test]))
  #?(:cljs (:require-macros [eulalie.test-util])))

#?(:clj
   (defmacro deftest [varn & forms]
     (if (:ns &env)
       `(cljs.test/deftest ~varn
          (cljs.test/async
           done#
           (-> (do ~@forms)
               (p/catch (fn [e#]
                          (println (.. e# -stack))
                          (cljs.test/is (not e#))))
               (p/then #(done#)))))
       `(t/deftest ~varn
          (deref (do ~@forms))))))
