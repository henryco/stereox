(ns stereox.utils.cmacros
  (:gen-class))

;(defmacro fun->
;  "Macro for Function lambda:
;
;  (reify
;    java.util.function.Function
;    (apply [_ arg#]
;      (~f arg#)))"
;  [f]
;  `(reify java.util.function.Function
;     (apply [_ arg#]
;       (~f arg#))))
;
;(defmacro con->
;  "Macro for Consumer lambda:
;
;  (reify
;    java.util.function.Consumer
;    (accept [_ arg#]
;      (~f arg#)))"
;  [f]
;  `(reify java.util.function.Consumer
;     (accept [_ arg#]
;       (~f arg#))))

(defmacro sup->
  "Macro for Supplier lambda:

  (reify
    java.util.function.Supplier
    (get [_]
      (~f)))"
  [f]
  `(reify java.util.function.Supplier
     (get [_] ~f)))