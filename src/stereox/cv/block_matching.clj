(ns stereox.cv.block-matching
  (:import (clojure.lang Atom)
           (org.opencv.calib3d Calib3d StereoBM StereoMatcher)
           (org.opencv.core Mat))
  (:gen-class))

(defn- ord
  "Returns original value if not nil,
  otherwise return default value"
  [v default]
  (if (nil? v) default v))

(defn- to-odd
  "Returns 'v' if odd, otherwise 'v+1'"
  [v]
  (if (= (mod v 2)
         0)
    (+ 1 v)
    v))

(defprotocol BlockMatcher
  "Block matcher algorithm interface"

  (options [_]
    "Returns tweakable options map {:key :max-val}")

  (setup [_] [_ map] [_ k v]
    "Update algorithm parameter (key value)")

  (param [_ key]
    "Get algorithm parameter")

  (params [_]
    "Get algorithm parameters")

  (disparity-map [_ images]
    "Calculate disparity map
    Returns:
      org.opencv.core.Mat")

  (project3d [_ disparity disparity-to-depth-map]
    "Project image to 3D
    Returns:
      org.opencv.core.Mat")
  )

(defrecord StereoBMProp
  [^Integer search-range
   ^Integer window-size
   ^Boolean missing
   ^Integer ddepth])

(deftype CpuStereoBM [^Atom *params
                      ^Atom *matcher]
  BlockMatcher

  (setup [_]
    (reset! *matcher (StereoBM/create (* 16 (int (:search-range @*params)))
                                      (max 5 (to-odd (int (:window-size @*params)))))))

  (options [_]
    {"search-range" 100
     "window-size"  100})

  (setup [this m]
    (dosync
      (swap! *params
             #(map (fn [[k v]]
                     (assoc % k v))
                   (seq m)))
      (setup this)))

  (setup [this k v]
    (let [kk (if (keyword? k) k (keyword k))]
      (dosync
        (swap! *params assoc kk v)
        (setup this))))

  (param [_ key]
    (if (keyword? key)
      (get @*params key)
      (get @*params (keyword key))))

  (params [_]
    (map->StereoBMProp @*params))

  (disparity-map [_ [left right]]
    (let [disparity (Mat.)]
      (.compute ^StereoMatcher @*matcher left right disparity)
      disparity))

  (project3d [_ disparity disparity-to-depth-map]
    (let [_3dImage (Mat.)
          handle (-> @*params :missing (true?))
          ddepth (-> @*params :ddepth (ord -1))]
      (Calib3d/reprojectImageTo3D disparity _3dImage disparity-to-depth-map handle ddepth)
      _3dImage)))

(defn create-cpu-stereo-bm
  ([^StereoBMProp props]
   (let [matcher (->CpuStereoBM (atom (map->StereoBMProp props))
                                (atom nil))]
     (setup matcher)
     matcher))
  ([] (create-cpu-stereo-bm
        (map->StereoBMProp
          {:search-range 5
           :window-size  11
           :missing      false
           :ddepth       -1
           }))))