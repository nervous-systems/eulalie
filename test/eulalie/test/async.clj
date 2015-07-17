(ns eulalie.test.async
  (:require [clojure.test :as test]
            [glossop.core :refer [<?!]]))

(defmacro deftest [t-name & forms]
  `(test/deftest ~t-name
     (<?! (do ~@forms))))
