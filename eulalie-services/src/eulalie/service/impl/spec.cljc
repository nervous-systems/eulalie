(ns ^:no-doc eulalie.service.impl.spec
  (:require [#?(:clj clojure.spec :cljs cljs.spec) :as s]
            [#?(:clj clojure.spec.gen :cljs cljs.spec.impl.gen) :as gen]))

(defn string* [chars min-len & [max-len {keyword* :keyword?}]]
  (let [regex (re-pattern (str "(?i)" chars "{" min-len "," max-len "}"))]
    (s/with-gen (fn [s]
                  (and (or (string? s) (and keyword* (keyword? s)))
                       (re-matches regex (name s))))
     (fn []
       (gen/fmap
        #(cond-> (apply str %) keyword* keyword)
        (gen/vector (gen/char-alphanumeric min-len max-len)))))))
