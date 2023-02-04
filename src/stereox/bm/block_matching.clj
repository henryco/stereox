(ns stereox.bm.block-matching

  (:gen-class)
  (:import (clojure.lang Atom)
           (org.opencv.calib3d Calib3d StereoMatcher)
           (org.opencv.core Mat)))

(defn- ord
  "Returns original value if not nil,
  otherwise return default value"
  [v default]
  (if (nil? v) default v))

(defprotocol IBlockMatcher
  "Block matcher algorithm interface"

  (setup [this & kv]
    "Update algorithm parameter (key value)")

  (param [this key]
    "Get algorithm parameter")

  (params [this]
    "Get algorithm parameters")

  (disparity-map [this images]
    "Calculate disparity map
    Returns:
      org.opencv.core.Mat")

  (project3d [this disparity disparity-to-depth-map]
    "Project image to 3D
    Returns:
      org.opencv.core.Mat")
  )

(defrecord StereoBMProp
  [search-range
   window-size
   preset
   missing
   ddepth])

(deftype CpuStereoBM [^Atom *params
                      ^StereoMatcher matcher]
  IBlockMatcher

  (setup [_ & kv]
    ; TODO
    (swap! *params
           #(map (fn [[k v]]
                   (assoc % k v))
                 (partition 2 kv))))

  (param [_ key]
    (get @*params key))

  (params [_]
    (map identity @*params))

  (disparity-map [_ [left right]]
    (let [disparity (Mat.)]
      (.compute ^StereoMatcher matcher left right disparity)
      disparity))

  (project3d [_ disparity disparity-to-depth-map]
    (let [_3dImage (Mat.)
          handle (-> @*params :missing (true?))
          ddepth (-> @*params :ddepth (ord -1))]
      (Calib3d/reprojectImageTo3D disparity _3dImage disparity-to-depth-map handle ddepth)
      _3dImage)))