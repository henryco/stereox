(defproject stereox "0.0.1"
  :description "Stereo vision"
  :url "https://tindersamurai.dev"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :repl-options {:init-ns stereox.core}
  :dependencies [[org.clojure/tools.cli "1.0.214"]
                 [org.clojure/clojure "1.11.1"]
                 [org.openpnp/opencv "4.6.0-0"]
                 [cljfx "1.7.22"]
                 ]
  :main stereox.core)