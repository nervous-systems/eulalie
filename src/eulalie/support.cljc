(ns eulalie.support
  (:require [eulalie.core :as eulalie]
            [plumbing.map]
            [eulalie.core :as eulalie]
            #? (:clj
                [glossop.core :refer [<? go-catching]]
                :cljs
                [cljs.core.async]))
  #? (:cljs (:require-macros [glossop.macros :refer [<? go-catching]])))

(defmulti translate-error-type
  (fn [service error-type]
    (keyword "eulalie.service" (name service))))
(defmethod translate-error-type :default [_ error-type] error-type)

(defn error->throwable [service {:keys [type message] :as error}]
  (let [type (translate-error-type service type)]
    (ex-info (name type) (assoc error :type type))))

(defn issue-request! [{:keys [body target service] :as req} req-fn resp-fn]
  (go-catching
    (let [{:keys [error body]}
          (<? (eulalie/issue-request!
               (assoc req :body (req-fn target body))))]
      (if error
        (error->throwable service error)
        (resp-fn target body)))))

#? (:clj
    (defmacro defissuer [service target-name args req-fn resp-fn & [doc]]
      (let [fname!  (-> target-name name (str "!") symbol)
            fname!! (-> target-name name (str "!!") symbol)
            args'   (into '[creds] (conj args '& '[extra]))
            body  `(eulalie.support/issue-request!
                    {:service ~service
                     :target  ~(keyword target-name)
                     :creds   ~'creds
                     :body    (merge (plumbing.map/keyword-map ~@args) ~'extra)}
                    ~req-fn ~resp-fn)
            md     (cond-> (meta target-name)
                     doc (assoc :doc doc))]
        `(do
           (defn ~(with-meta fname!  md) ~args' ~body)
           ~(when-not (:ns &env)
              `(defn ~(with-meta fname!! md) ~args' (glossop.core/<?! ~body)))))))
