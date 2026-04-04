(ns test.orwelliana.core-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [orwelliana.core :as core]))

(deftest sample-trace-shape
  (testing "sample events satisfy the required schema"
    (is (= 14 (count core/sample-trace)))
    (is (every? core/valid-event? core/sample-trace))))

(deftest derived-views
  (testing "derived views are computed from the session-seeded trace"
    (is (= [0.72 0.66]
           (core/confidence-curve core/sample-trace)))
    (is (= ["Adjust loop boundary condition"
            "Switch to char iteration instead of byte indexing"]
           (core/trajectory core/sample-trace)))
    (is (= {"lexer::unicode" ["incorrect byte-length assumption"]}
           (core/failure-manifold core/sample-trace)))))

(deftest fleet-views
  (testing "fleet summary highlights preferred targets and degraded services"
    (let [fleet (core/read-edn-file "ops/fleet.edn")]
      (is (= "gce-primary" (:preferred_target (core/fleet-summary fleet))))
      (is (= 2 (:dead (core/fleet-summary fleet))))
      (is (= 4 (:stale (core/fleet-summary fleet))))
      (is (= 13 (:services_total (core/fleet-summary fleet)))))))
