(in-ns 'stereox.config.view)

(defn render-image [{:keys [camera scale]}]
  {:fx/type    :v-box
   :style      {:-fx-background-color :darkgray
                :-fx-alignment        :center}
   :min-width  (-> camera :viewport :width (* scale))
   :min-height (-> camera :viewport :height (* scale))
   :children   (if (some? (:image camera))
                 [{:fx/type        :image-view
                   :preserve-ratio true
                   :viewport       (:viewport camera)
                   :fit-height     (-> camera :viewport :height (* scale))
                   :image          (:image camera)
                   }]
                 [])
   })

(defn slider [{:keys [min max value]}]
  {:fx/type  :v-box
   :spacing  5
   :children [{:fx/type :label
               :text    "TODO"}
              {:fx/type  :h-box
               :spacing  10
               :children [{:fx/type          :slider
                           :min-width        240
                           :block-increment  1
                           :major-tick-unit  10
                           :show-tick-labels true
                           :snap-to-ticks    true
                           :min              min
                           :max              max
                           :value            value
                           }
                          {:fx/type :label
                           :text    "TODO"}]
               }]})

(defn matcher-parameters [state]
  ; TODO
  {:fx/type    :v-box
   :style      {:-fx-background-color :white}
   :min-height 10
   :spacing    20
   :children   [{:fx/type slider
                 :min     0
                 :max     50
                 :value   33
                 }
                {:fx/type slider
                 :min     0
                 :max     50
                 :value   33
                 }
                ]
   })

(defn camera-parameters [state]
  ; TODO
  {:fx/type    :v-box
   :style      {:-fx-background-color :white}
   :min-height 10
   :spacing    10
   })

(defn render-parameters [state]
  {:fx/type   :v-box
   :style     {:-fx-background-color :white}
   :min-width (-> state :panel :width)
   :max-width (-> state :panel :width)
   :children  [(merge state {:fx/type matcher-parameters})
               (merge state {:fx/type camera-parameters})]
   })

(defn root [state]
  {:fx/type           :stage
   :resizable         true
   :showing           (:alive state)
   :title             (:title state)
   :on-close-request  shutdown
   :on-height-changed on-win-height-change
   :on-width-changed  on-win-width-change
   :scene             {:fx/type :scene
                       :root    {:fx/type :border-pane
                                 :style   {:-fx-background-color :black
                                           :-fx-alignment        :center}
                                 :right   (merge state {:fx/type render-parameters})
                                 :center  (merge state {:fx/type render-image})}}
   })