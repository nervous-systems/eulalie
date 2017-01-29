(ns eulalie.impl.http
  (:require [kvlt.core    :as kvlt]
            [clojure.set  :as set]
            [promesa.core :as p]))

(let [renames {:status  :eulalie.response/status
               :headers :eulalie.response/headers
               :body    :eulalie.response/body}]
  (defn request! [req]
    (let [req' {:headers (req :eulalie.request.signed/headers)
                :body    (req :eulalie.request/body)
                :url     (str (req :eulalie.request/endpoint))
                :method  (req :eulalie.request/method)}]
      (-> (kvlt/request! req')
          (p/branch
            (fn [resp]
              (set/rename-keys resp renames))
            (fn [e]
              (let [{:keys [status] :as e} (ex-data e)]
                (-> e
                    (assoc :eulalie.response/error {:eulalie.error/type :transport})
                    (set/rename-keys renames)))))
          (p/then #(assoc % :eulalie/request req))))))
