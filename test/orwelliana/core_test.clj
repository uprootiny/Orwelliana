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

(deftest conversation-views
  (testing "conversation history is summarized and bounded for reuse"
    (let [view (core/conversation-view core/sample-conversation {:session "demo"
                                                                 :limit 2
                                                                 :chars 120})
          window (get-in view [:window :messages])]
      (is (= ["demo"] (:sessions view)))
      (is (= 5 (:messages_count view)))
      (is (= {"assistant" 2 "system" 1 "user" 2} (:roles view)))
      (is (= 2 (count window)))
      (is (= "system" (:role (first window))))
      (is (= ["I will patch tokenizer.rs and rerun cargo test."]
             (mapv :content (rest window))))
      (is (<= (:chars (:window view)) 120)))))

(deftest git-and-deploy-doctor-logic
  (testing "git status parsing captures branch state"
    (is (= {:branch "main"
            :tracking "origin/main"
            :dirty true
            :changed_files 2
            :ahead 1
            :behind 0}
           (core/git-state "## main...origin/main [ahead 1]\n M README.md\n?? .codex"))))
  (testing "deployment verdicts collapse into a useful summary"
    (let [doctor {:slug "uprootiny/Orwelliana"
                  :head "abc123"
                  :git {:branch "main"
                        :tracking "origin/main"
                        :dirty false
                        :changed_files 0
                        :ahead 0
                        :behind 0}
                  :github {:repo {:json {:full_name "uprootiny/Orwelliana"
                                         :has_pages true}}
                           :pages {:json {:html_url "https://uprootiny.github.io/Orwelliana/"}}}
                  :runs [{:workflowName "CI" :conclusion "success" :status "completed"}
                         {:workflowName "Pages" :conclusion "success" :status "completed"}]
                  :http {:status_code 200}}]
      (is (= :ready (:status (core/deploy-summary doctor))))
      (is (= "https://uprootiny.github.io/Orwelliana/"
             (:public_url (core/deploy-summary doctor))))
      (is (empty? (:verdicts (core/deploy-summary doctor))))))
  (testing "deployment summary blocks on missing pages and bad public health"
    (let [doctor {:slug "uprootiny/Orwelliana"
                  :head "abc123"
                  :git {:branch "main"
                        :tracking "origin/main"
                        :dirty true
                        :changed_files 3
                        :ahead 1
                        :behind 0}
                  :github {:repo {:json {:full_name "uprootiny/Orwelliana"
                                         :has_pages false}}
                           :pages {:json nil}}
                  :runs [{:workflowName "CI" :conclusion "success" :status "completed"}
                         {:workflowName "Pages" :conclusion "failure" :status "completed" :url "https://example.test/pages"}]
                  :http {:status_code 404}}
          summary (core/deploy-summary doctor)]
      (is (= :blocked (:status summary)))
      (is (= 5 (count (:verdicts summary))))
      (is (= #{:warn :error}
             (set (map :severity (:verdicts summary))))))))

(deftest parse-bool-defaults
  (testing "boolean parsing supports default and common truthy forms"
    (is (true? (core/parse-bool nil true)))
    (is (false? (core/parse-bool nil false)))
    (is (true? (core/parse-bool "true" false)))
    (is (true? (core/parse-bool "YES" false)))
    (is (false? (core/parse-bool "false" true)))))
