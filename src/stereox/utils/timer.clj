(ns stereox.utils.timer

  (:gen-class)
  (:import (clojure.lang Atom)))

(defprotocol ITimer
  "Simple timer protocol"

  (start [_] [_ time] [_ time fn]
    "Start timer
    Expects TIME in milliseconds and Function callback [optional]
    Returns itself")

  (reset [_]
    "Reset timer and returns itself")

  (stop [_]
    "Stop timer and returns itself")

  (tick [_] [_ fn]
    "Tick a timer and returns itself")

  (remains [_]
    "Get remaining time.
    Returns java.lang.Long"))

(defrecord FnTimer [^Atom *state]
  ITimer
  (start [this]
    (let [time (:time @*state)]
      (if (or (nil? time)
              (<= time 0))
        (throw (Exception. "Time did not set or invalid!")))
      (start this time)))

  (start [this time]
    (let [now (System/currentTimeMillis)
          func (:fn @*state)]
      (reset! *state
              {:te   (+ now time)
               :fn   (if (some? func)
                       func
                       #(do))
               :ok   true
               :time time}))
    this)

  (start [this time fn]
    (let [now (System/currentTimeMillis)]
      (reset! *state
              {:te   (+ now time)
               :fn   fn
               :ok   true
               :time time}))
    this)

  (tick [this func]
    (if (:ok @*state)
      (swap! *state
             #(let [time (:time %)
                    now (System/currentTimeMillis)
                    dt (- (:te %) now)]
                (if (< dt 0)
                  (do (func)
                      (assoc % :te (+ now time)))
                  %))))
    this)

  (tick [this]
    (tick this (:fn @*state)))

  (reset [this]
    (if (:ok @*state)
      (let [now (System/currentTimeMillis)
            time (:time @*state)]
        (swap! *state assoc :te (+ now time))))
    this)

  (stop [this]
    (reset! *state {:ok false})
    this)

  (remains [this]
    (if (:ok @*state)
      (do (tick this)
          (- (:te @*state)
             (System/currentTimeMillis)))
      0)))

(defn create-timer
  "Create new instance of FnTimer"
  {:tag    FnTimer
   :static true}
  ([] (FnTimer. (atom {:ok false :fn #(do)})))
  ([delay] (FnTimer. (atom {:ok false :time delay :fn #(do)})))
  ([delay func] (FnTimer. (atom {:ok false :time delay :fn func}))))

(defn create-start-timer
  "Create and start new instance of FnTimer"
  {:tag    FnTimer
   :static true}
  ([] (start (create-timer)))
  ([delay] (start (create-timer delay)))
  ([delay func] (start (create-timer delay func))))