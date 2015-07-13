(ns eulalie.platform.util
  (:require [cljs.core.async :as async]))

(defn channel-fn [f & args]
  (let [chan (async/chan)
        args (conj (into [] args)
                   (fn [err result]
                     ;; FIXME wrap err if not js/Error
                     (async/put! chan (or err result))
                     (async/close! chan)))]
    (apply f args)
    chan))
