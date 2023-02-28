(ns stereox.cuda.cuda-render
  (:require [stereox.cuda.cuda :as cuda])
  (:gen-class))

(defn create
  "Creates render function, second version runs callbacks with cuda context.

  Expects:
    f_render - Render callback that returns (new) frame:
               [frame state & context devices] -> frame

    f_init   - Initialization callback that returns state:
               [frame & context devices] -> state

  Returns:
    fn: [frame] -> frame"
  ([f_render f_init]
   (let [*state (atom nil)]
     (fn [frame]
       (if (nil? @*state)
         (reset! *state (f_init frame)))
       (f_render frame @*state))))

  ([f_render f_init _]
   (let [*state (atom nil)]
     (fn [frame]
       (if (nil? @*state)
         (cuda/with-context (fn [c d] (reset! *state (f_init frame c d)))))
       (cuda/with-context (fn [c d] (f_render frame @*state c d)))))))