(ns test-runner
  (:require
   [clojure.test :as t]
   test.orwelliana.core-test))

(defn -main []
  (let [{:keys [fail error]} (t/run-tests 'test.orwelliana.core-test)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
