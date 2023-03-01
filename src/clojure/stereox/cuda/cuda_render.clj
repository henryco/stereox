(ns stereox.cuda.cuda-render
  (:require [stereox.cuda.cuda :as cuda])
  (:gen-class)
  (:import (clojure.lang IFn)))

(defn- context-state [*state context devices]
  (merge (deref *state) {:context context
                         :devices devices}))

(defn- self-ref [self]
  {:self  self :*self self
   :state self :*state self
   :ref   self :*ref self
   :this  self :*this self})

(defn- self-state []
  (let [*state (atom {})]
    (reset! *state (self-ref *state))
    *state))

(defn create
  "Creates render function, third version runs callbacks with cuda context.
  Note that STATE argument has reference :*state which can be used to
  modify state from the inside of render callback.

  Expects:
    f_render - Render callback that returns (new) frame:
               [[user_args...] STATE] -> frame

    f_init   - Initialization callback that returns state:
               [[user_args...] & context devices] -> STATE

  Returns:
    fn: [& user_args] -> frame"
  {:static true
   :tag    IFn}

  ([f_render]
   (let [*state (atom self-state)]
     (fn [frame & args]
       (apply f_render (concat [frame] (vec args) [@*state])))))

  ([f_render f_init]
   (let [*state (atom nil)]
     (fn [& args]
       (if (nil? @*state)
         (reset! *state (merge (apply f_init (concat (vec args))) (self-ref *state))))
       (apply f_render (concat (vec args) [@*state])))))

  ([f_render f_init _]
   (let [*state (atom nil)]
     (fn [& args]
       (if (nil? @*state)
         (cuda/with-context (fn [c d] (merge (apply f_init (concat (vec args) [c d])) (self-ref *state)))))
       (cuda/with-context
         (fn [c d] (apply f_render (concat (vec args) [(context-state *state c d)]))))))))