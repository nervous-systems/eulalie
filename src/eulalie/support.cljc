(ns eulalie.support
  (:require [eulalie.core :as eulalie]
            [plumbing.map]
            [eulalie.core :as eulalie]
            [#? (:clj clojure.core.async :cljs cljs.core.async) :as async]
            [glossop.core :as g
             #? (:clj :refer :cljs :refer-macros) [go-catching <?]])
  #? (:cljs (:require-macros [eulalie.support])))

(defmulti translate-error-type
  (fn [service error-type]
    (keyword "eulalie.service" (name service))))
(defmethod translate-error-type :default [_ error-type] error-type)

(defn error->throwable [service {:keys [type message] :as error}]
  (let [type (translate-error-type service type)]
    (ex-info (str (name type) ": " message) (assoc error :type type))))

(defn issue-request! [{:keys [body target service chan close?] :as req
                       :or {close? true}} & [req-fn resp-fn]]
  (cond->
      (go-catching
        (let [{:keys [error body]}
              (<? (eulalie/issue-request!
                   (-> req
                       (assoc :body (cond->> body req-fn (req-fn target)))
                       (dissoc :chan :close?))))]
          (if error
            (error->throwable service error)
            (cond->> body resp-fn (resp-fn target)))))
    chan (async/pipe chan close?)))

#? (:clj
    (defmacro defissuer [service target-name issue-args req-fn resp-fn & [doc]]
      (let [fname!  (-> target-name name (str "!") symbol)
            fname!! (-> target-name name (str "!!") symbol)
            args'   (into '[creds] (conj issue-args '& 'args))
            body    `(let [[op-args# req-args#] ~'args]
                       (eulalie.support/issue-request!
                        (merge
                         {:service ~service
                          :target  ~(keyword target-name)
                          :creds   ~'creds
                          :body    (merge
                                    (plumbing.map/keyword-map ~@issue-args)
                                    op-args#)}
                         req-args#)
                        ~req-fn ~resp-fn))
            md      (cond-> (meta target-name)
                      doc (assoc :doc doc))]
        `(do
           (defn ~(with-meta fname! md) ~args' ~body)
           ~(when-not (:ns &env)
              `(defn ~(with-meta fname!! md) ~args' (g/<?! ~body)))))))
