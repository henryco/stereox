(in-ns 'stereox.config.view)

(defn render-image [{:keys [camera scale]}]

  )

(defn root [state]
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
