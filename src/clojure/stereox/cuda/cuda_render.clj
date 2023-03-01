(ns stereox.cuda.cuda-render
  (:require [stereox.cuda.cuda :as cuda])
  (:gen-class)
  (:import (clojure.lang IFn)))

(defn- self-ref [self]
  {:self  self :*self self
   :state self :*state self
   :ref   self :*ref self
   :this  self :*this self})

(defn create
  "Creates render function, second version runs callbacks with cuda context.
  Note that STATE argument has reference :*state which can be used to
  modify state from the inside of render callback.

  Expects:
    f_render - Render callback that returns (new) frame:
               [frame STATE & context devices] -> frame

    f_init   - Initialization callback that returns state:
               [frame & context devices] -> STATE

  Returns:
    fn: [frame] -> frame"
  {:static true
   :tag    IFn}
  ([f_render f_init]
   (let [*state (atom nil)]
     (fn [frame]
       (if (nil? @*state)
         (reset! *state (merge (f_init frame) (self-ref *state))))
       (f_render frame @*state))))
  ([f_render f_init _]
   (let [*state (atom nil)]
     (fn [frame]
       (if (nil? @*state)
         (cuda/with-context (fn [c d] (merge (f_init frame c d) (self-ref *state)))))
       (cuda/with-context (fn [c d] (f_render frame @*state c d)))))))