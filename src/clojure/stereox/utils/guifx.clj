(ns stereox.utils.guifx
  (:require [cljfx.api :as fx])
  (:import (clojure.lang Atom)
           (javafx.animation AnimationTimer)
           (javafx.application Platform)))

(defprotocol IGuiFx
  (shutdown [this])
  (start-ui-loop [this func])
  (mount [this root] [this root func]))

(defrecord GuiFx [^Atom *state
                  ^Atom *inner]
  IGuiFx
  (shutdown [_]
    (try (swap! *inner assoc :alive false)
         (Platform/exit)
         (catch Exception e (.printStackTrace e))))
  (start-ui-loop [_ func]
    (.start
      (proxy [AnimationTimer] []
        (handle [_]
          (if (:alive @*inner)
            (func))))))
  (mount [_ root]
    (swap! *inner assoc :alive true)
    (fx/mount-renderer
      *state
      (fx/create-renderer
        :middleware (fx/wrap-map-desc
                      assoc :fx/type root))))
  (mount [this root func]
    (mount this root)
    (start-ui-loop this func)))

(defn create-guifx
  "Creates new instance of GuiFx"
  {:tag    GuiFx
   :static true}
  ([^Atom *state]
   (->GuiFx *state (atom {:alive false})))
  ([^Atom *state root]
   (let [gfx (create-guifx *state)]
     (mount gfx root)
     gfx))
  ([^Atom *state root func]
   (let [gfx (create-guifx *state root)]
     (start-ui-loop gfx func)
     gfx)))
