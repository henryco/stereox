(ns stereox.cv.frame-delta
  (:require [stereox.cuda.cuda-kernel :as kernel]
            [stereox.cuda.cuda :as cuda])
  (:import (org.bytedeco.cuda.cudart CUctx_st CUfunc_st CUmod_st CUstream_st)
           (org.bytedeco.javacv OpenCVFrameConverter$ToMat OpenCVFrameConverter$ToOrgOpenCvCoreMat)
           (org.bytedeco.cuda.global cudart)
           (org.bytedeco.javacpp BytePointer IntPointer LongPointer Pointer PointerPointer)
           (org.bytedeco.javacv GLCanvasFrame)
           (org.bytedeco.opencv.opencv_core GpuMat Mat Scalar)
           (org.bytedeco.opencv.global opencv_cudaarithm opencv_imgproc opencv_cudaimgproc)
           (org.opencv.core CvType))
  (:gen-class))

(def ^:private *function (atom nil))

(defn- gpu-to-cv
  {:static true
   :tag    Mat}
  [^GpuMat mat]
  (let [cv_mat (Mat.)]
    (.download mat cv_mat)
    cv_mat))

(defn- cv-to-core
  {:static true
   :tag    org.opencv.core.Mat}
  [^Mat cvMat]
  (let [conv_1 (new OpenCVFrameConverter$ToMat)
        conv_2 (new OpenCVFrameConverter$ToOrgOpenCvCoreMat)]
    (->> cvMat (.convert conv_1) (.convert conv_2))))


(defprotocol FrameDelta
  "Delta calculator"
  (delta [_ input] "Calculate delta"))

(defn render [^GpuMat curr ^GpuMat prev]

  (let [out (GpuMat. (.size curr)
                     (.type curr)
                     (Scalar. 0 0 0 0))]
    ;(opencv_cudaarithm/absdiff
    ;  ^GpuMat prev
    ;  ^GpuMat curr
    ;  ^GpuMat out)

    (cuda/with-context
      (fn [_ _]

        (let [function (deref *function)]

          ; FIXME kernel/parameters?
          (function (.ptr ^GpuMat out)
                    (.step out)
                    (.cols out)
                    (.rows out))

          (let [mat (gpu-to-cv out)
                mta (cv-to-core mat)
                arr (byte-array (int (* (.total mta) (.channels mta))))
                ]
            (.get mta 0 0 arr)

            ;(println (CvType/typeToString (.type mta)))

            (println (alength ^bytes arr))
            (println (.step mat))
            (println (aget ^bytes arr 0)
                     (aget ^bytes arr 1)
                     (aget ^bytes arr 2)

                     (aget ^bytes arr 3)
                     (aget ^bytes arr 4)
                     (aget ^bytes arr 5)

                     (aget ^bytes arr 6)
                     (aget ^bytes arr 7)
                     (aget ^bytes arr 8)

                     (aget ^bytes arr 9)
                     (aget ^bytes arr 10)
                     (aget ^bytes arr 11)

                     (aget ^bytes arr (+ 0 (* 3 3) (* (.step mat) 350 )))
                     (aget ^bytes arr (+ 1 (* 3 3) (* (.step mat) 350 )))
                     (aget ^bytes arr (+ 2 (* 3 3) (* (.step mat) 350 )))
                     )


            )


          ;(cuda/status (cudart/cuMemAlloc d_src_2 (* (.step curr) (.rows curr))))
          ;(cuda/status (cudart/cuMemAlloc d_dst_2 (* (.step out) (.rows out))))

          ;(cuda/status (cudart/cuMemcpyDtoD (aget d_src_2 0) (.address d_src_1) (* (.step curr) (.rows curr))))
          ;(cuda/status (cudart/cuMemcpyDtoD (aget d_dst_2 0) (.address d_dst_1) (* (.step out) (.rows out))))

          ;(cuda/status (cudart/cudaMemcpy d_src_2 d_src_1 size_src cudart/cudaMemcpyDeviceToDevice))
          ;(cuda/status (cudart/cudaMemcpy d_dst_2 d_dst_1 size_dst cudart/cudaMemcpyDeviceToDevice))

          ;(function d_src_2
          ;          d_dst_2
          ;          (.step curr)
          ;          (.step out)
          ;          (.cols curr)
          ;          (.rows curr))

          )

        ;(println "-")
        ;(println (.type curr)                                     ;16
        ;         (.channels curr)                                 ;3
        ;         (.depth curr)                                    ;0
        ;         (.elemSize curr)                                 ;3
        ;         (.step curr)                                     ;4096
        ;         (.cols curr)                                     ;1280
        ;         (.rows curr)                                     ;720
        ;         (.isContinuous curr)                             ;false
        ;         (-> curr .type CvType/ELEM_SIZE)                 ;3
        ;         (-> curr .type CvType/typeToString)              ;CV_8UC3
        ;         (-> curr .depth CvType/typeToString)             ;CV_8UC1
        ;         )

        ))
    out))

(defn init [width height]
  (if (nil? @*function)
    (do
      ;(reset! *function
      ;        (kernel/kernel-function
      ;          "cuda/conv_bgr_8u.cu"
      ;          "conv"
      ;          width
      ;          height
      ;          :pointer
      ;          :pointer
      ;          ;shorts
      ;          ;shorts
      ;          int
      ;          int
      ;          int
      ;          int))
      (reset! *function
              (kernel/kernel-function
                "cuda/test_bgr_8u.cu"
                "test"
                width
                height
                Pointer
                int
                int
                int))
      )))

(deftype CudaDelta [*prev]
  FrameDelta
  (delta [_ input]
    (init (.cols input) (.rows input))
    (if (nil? @*prev)
      (do (reset! *prev input) input)
      (let [out (render input @*prev)]
        (reset! *prev input)
        out))))

(defn create-cuda-delta
  {:static true
   :tag    FrameDelta}
  []
  (->CudaDelta (atom nil)))