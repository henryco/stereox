; https://gist.github.com/oliyh/0c1da9beab43766ae2a6abc9507e732a
(ns stereox.utils.debounce
  (:import (java.util Timer TimerTask)))

(defn debounce
  ([f] (debounce f 1000))
  ([f timeout]
   (let [timer (Timer.)
         task (atom nil)]
     (with-meta
       (fn [& args]
         (when-let [t ^TimerTask @task]
           (.cancel t))
         (let [new-task (proxy [TimerTask] []
                          (run []
                            (apply f args)
                            (reset! task nil)
                            (.purge timer)))]
           (reset! task new-task)
           (.schedule timer new-task ^Long timeout)))
       {:task-atom task}))))