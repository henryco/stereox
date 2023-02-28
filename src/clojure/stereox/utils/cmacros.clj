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

(defmacro in [v & params]
  `(or ~@(map (fn [p] `(= ~v ~p)) params)))

(defmacro with-timer [func & args]
  (if (or (nil? func) (= nil func))
    `(do ~@(map identity args))
    (let [t0 (gensym 't0)]
      `(let [~t0 (System/nanoTime)]
         ~@(map identity args)
         (~func (- (System/nanoTime) ~t0))))))