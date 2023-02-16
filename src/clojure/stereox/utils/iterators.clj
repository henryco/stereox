(ns stereox.utils.iterators)

(defprotocol SoxIterator
  (>>> [_]))

(deftype Iterator [^{:volatile-mutable true} coll]
  SoxIterator
  (>>> [_]
    (let [first (first coll)]
      (set! coll (if (seq? coll)
                   (next coll)
                   (next (seq coll))))
      first)))