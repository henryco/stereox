(in-ns 'stereox.config.view)

(defn render-image [{:keys [camera scale]}]
  {:fx/type     :v-box
   :style       {:-fx-background-color :darkgray}
   :min-width   (-> camera :viewport :width (* scale))
   :min-height  (-> camera :viewport :height (* scale))
   :max-width   (-> camera :viewport :width (* scale))
   :max-height  (-> camera :viewport :height (* scale))
   :pref-width  (-> camera :viewport :width (* scale))
   :pref-height (-> camera :viewport :height (* scale))
   :children    (if (some? (:image camera))
                  [{:fx/type        :image-view
                    :preserve-ratio true
                    :fit-height     (-> camera :viewport :height (* scale))
                    :viewport       (:viewport camera)
                    :image          (:image camera)
                    }]
                  [])
   })

(defn slider [{:keys [id min max value update-fn]}]
  {:fx/type  :h-box
   :spacing  5
   :children [{:fx/type :region :min-width 10 :min-height 1}
              {:fx/type  :v-box
               :spacing  5
               :children [{:fx/type :label
                           :text    id}
                          {:fx/type  :h-box
                           :spacing  10
                           :children [{:fx/type          :slider
                                       :min-width        200
                                       :min              min
                                       :max              max
                                       :value            value
                                       :on-value-changed update-fn}
                                      {:fx/type         :text-field
                                       :text            (str value)
                                       :max-width       50
                                       :on-text-changed #(let [numb (try (Integer/parseInt %)
                                                                         (catch Exception _ nil))]
                                                           (if (some? numb)
                                                             (update-fn (commons/clamp min max numb))))}]
                           }]}
              ]}
  )

(defn matcher-parameters [{:keys [matcher]}]
  {:fx/type    :v-box
   :style      {:-fx-background-color :white}
   :min-height 10
   :spacing    20
   :children   (map (fn [{:keys [id min max val]}]
                      {:fx/type   slider
                       :value     (if (some? val) (int val) -1)
                       :id        id
                       :max       max
                       :min       min
                       :update-fn #(debounce-matcher-update id (int %))})
                    matcher)
   })

(defn camera-parameters [{:keys [camera]}]
  {:fx/type    :v-box
   :style      {:-fx-background-color :white}
   :min-height 10
   :spacing    20
   :children   (map (fn [{:keys [id min max val]}]
                      {:fx/type   slider
                       :value     (if (some? val) (int val) -1)
                       :id        id
                       :max       max
                       :min       min
                       :update-fn #(debounce-camera-update id (int %))})
                    camera)
   })

(defn v-separator [_]
  {:fx/type    :v-box
   :style      {:-fx-background-color :white}
   :min-height 15
   :spacing    20})

(defn save-button [{:keys [saved]}]
  {:fx/type   :v-box
   :alignment :center
   :children  [{:fx/type v-separator}
               {:fx/type   :h-box
                :alignment :center-right
                :spacing   25
                :children  [{:fx/type   :label
                             :text-fill :green
                             :text      (if (true? saved)
                                          "Settings saved"
                                          "")}
                            {:fx/type   :button
                             :text      "Save"
                             :on-action save-settings}
                            {:fx/type v-separator}]}
               {:fx/type v-separator}]
   })

(defn camera-mode-group [_]
  {:fx/type   :v-box
   :alignment :center
   :children  [{:fx/type v-separator}
               {:fx/type   :h-box
                :alignment :center-left
                :spacing   13
                :children  [{:fx/type v-separator}
                            {:fx/type   :button
                             :text      "Disparity"
                             :on-action (fn [_] (on-mode-selected :disparity))}
                            {:fx/type   :button
                             :text      "Depth"
                             :on-action (fn [_] (on-mode-selected :depth))}
                            {:fx/type   :button
                             :text      "3D"
                             :on-action (fn [_] (on-mode-selected :3D))}
                            {:fx/type   :button
                             :text      "L"
                             :on-action (fn [_] (on-mode-selected :L))}
                            {:fx/type   :button
                             :text      "R"
                             :on-action (fn [_] (on-mode-selected :R))}
                            {:fx/type v-separator}]}
               {:fx/type v-separator}]
   })

(defn render-parameters [state]
  {:fx/type    :v-box
   :style      {:-fx-background-color :white}
   :min-width  (-> state :panel :width)
   :max-width  (-> state :panel :width)
   :max-height (:height state)
   :children   [{:fx/type camera-mode-group}
                {:fx/type     :scroll-pane
                 :hbar-policy :NEVER
                 :content     {:fx/type   :v-box
                               :style     {:-fx-background-color :white}
                               :min-width (-> state :panel :width)
                               :max-width (-> state :panel :width)
                               :children  [{:fx/type v-separator}
                                           {:fx/type matcher-parameters
                                            :matcher (-> state :controls :matcher)}
                                           {:fx/type v-separator}
                                           {:fx/type camera-parameters
                                            :camera  (-> state :controls :camera)}
                                           {:fx/type v-separator}
                                           {:fx/type v-separator}]}
                 }
                {:fx/type save-button :saved (:saved state)}]
   })

(defn root [state]
  {:fx/type           :stage
   :resizable         true
   :showing           (:alive state)
   :title             (:title state)
   :on-close-request  shutdown
   :on-height-changed on-win-height-change
   :on-width-changed  on-win-width-change
   :scene             {:fx/type           :scene
                       :on-height-changed on-scene-height-change
                       :on-width-changed  on-scene-width-change
                       :root              {:fx/type :border-pane
                                           :style   {:-fx-background-color :darkgray
                                                     :-fx-alignment        :center}
                                           :right   (merge state {:fx/type render-parameters})
                                           :center  (merge state {:fx/type render-image})
                                           }
                       }
   })