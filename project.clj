(defproject stereox "0.0.1"
  :description "Stereo vision"
  :url "https://tindersamurai.dev"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :injections [(nu.pattern.OpenCV/loadShared)]
  :repl-options {:init-ns stereox.core}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.openpnp/opencv "4.6.0-0"]
                 [cljfx "1.7.22"]])