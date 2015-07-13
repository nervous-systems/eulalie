(ns eulalie.test.async.macros)

(defmacro deftest [t-name & forms]
  `(cemerick.cljs.test/deftest ~(with-meta t-name {:async true})
     (cemerick.cljs.test/block-or-done (do ~@forms))))
