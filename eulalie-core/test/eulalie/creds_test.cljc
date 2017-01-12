(ns eulalie.creds-test
  (:require [eulalie.creds :as creds]
            [eulalie.impl.platform.time :as platform.time]
            [promesa.core :as p]
            #?(:clj  [clojure.test :as t]
               :cljs [cljs.test :as t :include-macros true])
            [promesa-check.util :as util]))

(util/deftest expiring
  (let [creds-map {:access-key "xyz" :secret-key "abc" :expiration 2}
        creds     (creds/expiring 1 #(p/resolved creds-map))]
    (t/is (creds/expired? creds))
    (p/then (creds/refresh! creds)
      (fn [creds-map']
        (t/is (= creds-map creds-map' (creds/resolve creds)))
        (with-redefs [platform.time/msecs-now (constantly 0)]
          (t/is (not (creds/expired? creds))))
        (with-redefs [platform.time/msecs-now (constantly 1)]
          (t/is (creds/expired? creds)))))))
