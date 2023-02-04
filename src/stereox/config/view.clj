(ns stereox.config.view
  (:require [stereox.utils.guifx :as gfx]
            [stereox.config.logic :as logic]
            )
  (:gen-class))

(def ^:private *logic
  "Logic"
  (atom nil))

(def ^:private *gui
  "GuiFx"
  (atom nil))

(def ^:private *state
  "JavaFX UI state"
  (atom {:title "StereoX Pattern Matching configuration"
         :camera {:viewport {:width 1 :height 1 :min-x 0 :min-y 0}}
         :scale  1.
         :alive  true
         :width  1280
         :height 720
         }))

(defn- shutdown [& {:keys [code]}]
  (try
    (gfx/shutdown @*gui)
    (catch Exception e (.printStackTrace e))
    (finally (System/exit (if (some? code) code 0)))))

(defn- on-win-change []
  (let [s (-> @*state :scale)
        ow (-> @*state :camera :viewport :width)
        oh (-> @*state :camera :viewport :height)
        ww (-> @*state :width)
        hh (-> @*state :height)]
    (if (and (some? ww) (some? hh))
      (let [[dw dh] (map - [ww hh] [(* ow s 2) (* oh s)])]
        (if (< dw dh)
          (swap! *state assoc :scale (/ ww ow 2))
          (swap! *state assoc :scale (/ hh oh)))
        )
      )
    ))

(defn- on-win-height-change [v]
  (swap! *state assoc :height v)
  (on-win-change))

(defn- on-win-width-change [v]
  (swap! *state assoc :width v)
  (on-win-change))

(defn- root [state]
  {:fx/type           :stage
   :resizable         true
   :showing           (:alive state)
   :title             (:title state)
   :on-close-request  shutdown
   :on-height-changed on-win-height-change
   :on-width-changed  on-win-width-change
   :scene             {:fx/type :scene
                       :root    {:fx/type  :v-box
                                 :style    {:-fx-background-color :black
                                            :-fx-alignment        :center}
                                 ;:children [(merge state {:fx/type render-images})]
                                 ; TODO
                                 }
                       }
   })

(defn- main-cb-loop []
  ; TODO
  )

(defn start-gui [& {:as args}]
  (reset! *logic (logic/configure args))
  (reset! *gui (gfx/create-guifx *state root main-cb-loop)))