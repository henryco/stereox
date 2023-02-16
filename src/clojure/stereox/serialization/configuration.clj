(ns stereox.serialization.configuration
  (:gen-class)
  (:require [taoensso.nippy :as nippy])
  (:import (clojure.lang APersistentMap APersistentVector)
           (java.io DataInput DataOutput File FileInputStream FileOutputStream)))

(defrecord CameraSettings
  [^APersistentVector ids
   ^Integer width
   ^Integer height
   ^Integer gain
   ^Integer gamma
   ^Integer brightness
   ^Integer fps
   ^Integer exposure
   ^Integer buffer
   ^Integer sharpness
   ^Integer iso
   ^chars codec])

(defrecord MatcherSettings
  [^String type
   ^APersistentMap properties])

(defrecord StereoSettings
  [^MatcherSettings matcher
   ^CameraSettings camera])

(defn- write-camera-settings
  {:static true}
  [^CameraSettings data ^DataOutput os]
  ; TODO
  )

(defn- read-camera-settings
  {:static true
   :tag    CameraSettings}
  [^DataInput is]
  ; TODO
  )

(defn- write-matcher-settings
  {:static true}
  [^MatcherSettings data ^DataOutput os]
  ; TODO
  )

(defn- read-matcher-settings
  {:static true
   :tag    MatcherSettings}
  [^DataInput is]
  ; TODO
  )

(defn- write-stereo-settings
  {:static true}
  [^StereoSettings data ^DataOutput os]
  (write-matcher-settings (:matcher data) os)
  (write-camera-settings (:camera data) os))

(defn- read-stereo-settings
  {:static true
   :tag    StereoSettings}
  [^DataInput is]
  (->StereoSettings (read-matcher-settings is)
                    (read-camera-settings is)))

(nippy/extend-freeze StereoSettings :serialization/stereo-settings [o s] (write-stereo-settings o s))
(nippy/extend-thaw :serialization/stereo-settings [s] (read-stereo-settings s))

(defn stereo-settings-to-file
  "Writes stereo-settings to file"
  {:static true}
  [^StereoSettings data ^File file]
  (with-open [o (FileOutputStream. file)]
    (.write o ^bytes (nippy/freeze data))))

(defn stereo-settings-from-file
  "Reads stereo settings from file"
  {:tag    StereoSettings
   :static true}
  [^File file]
  (with-open [i (FileInputStream. file)]
    (let [bts (.readAllBytes i)
          rsl (nippy/thaw bts)]
      rsl)))