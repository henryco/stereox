(defproject stereox "0.0.1"
  :description "Stereo vision"
  :url "https://tindersamurai.dev"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :repl-options {:init-ns stereox.core}
  :dependencies [[org.clojure/tools.cli "1.0.214"]
                 [org.clojure/clojure "1.11.1"]
                 [org.bytedeco/opencv-platform "4.6.0-1.5.8"]
                 [org.bytedeco/opencv-platform-gpu "4.6.0-1.5.8"]
                 [org.bytedeco/cuda-platform "11.8-8.6-1.5.8"]
                 [org.bytedeco/cuda-platform-redist "11.8-8.6-1.5.8"]
                 [com.taoensso/nippy "3.2.0"]
                 [com.taoensso/timbre "5.2.1"]
                 [cljfx "1.7.22"]
                 ]
  :main stereox.core)