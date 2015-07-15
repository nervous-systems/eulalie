(ns eulalie.support
  (:require [plumbing.map]
            [eulalie.core :as eulalie]
            [glossop.core]))

#? (:clj
    (defmacro defissuer [service target-name args req-fn resp-fn & [doc]]
      (let [fname!  (-> target-name name (str "!") symbol)
            fname!! (-> target-name name (str "!!") symbol)
            args'   (into '[creds] (conj args '& '[extra]))
            body  `(eulalie/issue-request!
                    ~service ~(keyword target-name) ~'creds
                    (merge {:service ~service
                            :target ~(keyword target-name)
                            :creds ~'creds}
                           (plumbing.map/keyword-map ~@args)
                           ~'extra)
                    ~req-fn ~resp-fn)
            md     (cond-> (meta target-name)
                     doc (assoc :doc doc))]
        `(do
           (defn ~(with-meta fname!  md) ~args' ~body)
           ~(when-not (:ns &env)
              `(defn ~(with-meta fname!! md) ~args' (glossop.core/<?! ~body)))))))
