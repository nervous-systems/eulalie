(ns eulalie.util.platform
  (:import java.util.zip.CRC32))

(defn response-checksum-ok? [{:keys [headers body]}]
  (let [crc (some-> headers :x-amz-crc32 Long/parseLong)]
    (or (not crc)
        ;; We're not going to calculate the checksum of gzipped responses, since
        ;; we need access to the raw bytes - look into how to do this with
        ;; httpkit
        (= (:content-encoding headers) "gzip")
        (= crc (.getValue
                (doto (CRC32.)
                  (.update (get-utf8-bytes body))))))))
