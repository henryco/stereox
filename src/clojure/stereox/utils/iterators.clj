(ns stereox.utils.iterators)

(defprotocol SoxIterator
  (>>> [_])
  (>>- [_ n]))

(deftype Iterator [^{:volatile-mutable true} coll]
  SoxIterator
  (>>- [this n]
    (if (= n 0)
      (>>> this)
      (do (>>> this)
          (recur (- n 1)))))
  (>>> [_]
    (let [first (first coll)]
      (set! coll (if (seq? coll)
                   (next coll)
                   (next (seq coll))))
      first)))