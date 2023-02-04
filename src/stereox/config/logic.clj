(ns stereox.config.logic
  (:require [taoensso.timbre :as log]
            [stereox.serialization.utils :as su])
  (:gen-class))

(defn configure [& {:keys [config-folder
                           ids
                           width
                           height
                           ]
                    :as args}]
  (log/info (pr-str args))

  (println (su/list-calibration-candidates config-folder width height ids))
  )