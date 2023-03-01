(ns stereox.cuda.cuda-render
  (:require [stereox.cuda.cuda :as cuda])
  (:gen-class)
  (:import (clojure.lang IFn)))

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
               [frame ...user_args... STATE & context devices] -> frame

    f_init   - Initialization callback that returns state:
               [frame ...user_args... & context devices] -> STATE

  Returns:
    fn: [frame & user_args] -> frame"
  {:static true
   :tag    IFn}

  ([f_render]
   (let [*state (atom self-state)]
     (fn [frame & args]
       (apply f_render (concat [frame] (vec args) [@*state])))))

  ([f_render f_init]
   (let [*state (atom nil)]
     (fn [frame & args]
       (if (nil? @*state)
         (reset! *state (merge (apply f_init (concat [frame] (vec args))) (self-ref *state))))
       (apply f_render (concat [frame] (vec args) [@*state])))))

  ([f_render f_init _]
   (let [*state (atom nil)]
     (fn [frame & args]
       (if (nil? @*state)
         (cuda/with-context (fn [c d] (merge (apply f_init (concat [frame] (vec args) [c d])) (self-ref *state)))))
       (cuda/with-context
         (fn [c d] (apply f_render (concat [frame] (vec args) [@*state c d]))))))))