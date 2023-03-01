(ns stereox.cv.frame-delta
  (:require [stereox.cuda.cuda-kernel :as kernel]
            [stereox.cuda.cuda-render :as renderer]
            [stereox.cuda.cuda :as cuda])
  (:import (org.bytedeco.opencv.global opencv_cudaarithm opencv_imgproc opencv_cudaimgproc)
           (org.bytedeco.opencv.opencv_core GpuMat)
           (org.bytedeco.cuda.global cudart)
           (org.bytedeco.javacpp Pointer)
           (clojure.lang Atom IFn))
  (:use [stereox.utils.cmacros])
  (:gen-class))

(defn initialize [input]
  (let [width (.cols input)
        height (.rows input)]
    {
     :last
     input

     :function
     (kernel/kernel-function
       "cuda/test_bgr_8u.cu"
       "test"
       width
       height
       Pointer
       int
       int
       int)

     }))

(defn render [^GpuMat frame & {:keys [^GpuMat last ^IFn function ^Atom *state]}]
  (if (not= frame last)
    (let [out (GpuMat. (.size frame) (.type frame))]
      ;(opencv_cudaarithm/absdiff
      ;  ^GpuMat prev
      ;  ^GpuMat frame
      ;  ^GpuMat out)

      ; TODO

      (function (.ptr ^GpuMat out)
                (.step out)
                (.cols out)
                (.rows out))

      (swap! *state assoc
             :last frame)
      out)
    frame))

(defn create-cuda-delta
  {:static true :tag IFn} []
  (renderer/create render initialize))