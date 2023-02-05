(in-ns 'stereox.config.view)

(defn render-image [{:keys [camera scale]}]
  {:fx/type    :v-box
   :style      {:-fx-background-color :black
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

(defn matcher-parameters [state]
  {:fx/type    :v-box
   :style      {:-fx-background-color :blue}
   :min-height (-> state :panel :min-height)
   })

(defn camera-parameters [state]
  {:fx/type    :v-box
   :style      {:-fx-background-color :red}
   :min-height (-> state :panel :min-height)
   })

(defn render-parameters [state]
  {:fx/type   :v-box
   :style     {:-fx-background-color :gray}
   :min-width (-> state :panel :min-width)
   :max-width (-> state :panel :min-width)
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
                       :root    {:fx/type  :h-box
                                 :style    {:-fx-background-color :black
                                            :-fx-alignment        :center}
                                 :children [(merge state {:fx/type render-image})
                                            (merge state {:fx/type render-parameters})]
                                 }
                       }
   })
