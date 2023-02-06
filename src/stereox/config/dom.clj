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
                   :fit-height     (-> camera :viewport :height (* scale))
                   :viewport       (:viewport camera)
                   :image          (:image camera)
                   }]
                 [])
   })

(defn slider [{:keys [id min max value update-fn]}]
  {:fx/type  :v-box
   :spacing  5
   :children [{:fx/type :label
               :text    id}
              {:fx/type  :h-box
               :spacing  10
               :children [{:fx/type          :slider
                           :min-width        240
                           :block-increment  1
                           :major-tick-unit  1
                           :minor-tick-count 1
                           :show-tick-labels true
                           :snap-to-ticks    true
                           :min              min
                           :max              max
                           :value            value
                           :on-value-changed update-fn
                           }
                          {:fx/type :label
                           :text    (str value)}]
               }]})

(defn matcher-parameters [{:keys [matcher]}]
  {:fx/type    :v-box
   :style      {:-fx-background-color :white}
   :min-height 10
   :spacing    20
   :children   (map (fn [{:keys [id max val]}]
                      {:fx/type   slider
                       :value     val
                       :id        id
                       :max       max
                       :min       0
                       :update-fn #(on-matcher-update id (int %))})
                    matcher)
   })

(defn camera-parameters [{:keys [camera]}]
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
   :children  [{:fx/type matcher-parameters
                :matcher (-> state :controls :matcher)}
               {:fx/type camera-parameters
                :camera  (-> state :controls :camera)}]
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