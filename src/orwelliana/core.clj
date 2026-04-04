(ns orwelliana.core
  (:require
   [babashka.process :as process]
   [cheshire.core :as json]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(def required-keys
  [:ts :channel :event])

(def sample-trace
  [{:ts "2026-03-30T12:00:00Z"
    :channel "system"
    :event "agent_start"
    :agent "agent-parser"
    :repo "parser-lib"
    :summary "Agent booted with session-seeded ontology"}
   {:ts "2026-03-30T12:00:01Z"
    :channel "heartbeat"
    :event "loop_tick"
    :loop 1
    :status "alive"
    :details {:cpu 0.12 :mem_mb 220}}
   {:ts "2026-03-30T12:00:02Z"
    :channel "discovery"
    :event "repo_scan"
    :summary "Rust project with cargo tests"
    :details {:language "rust" :test_command "cargo test" :files_indexed 142}}
   {:ts "2026-03-30T12:00:03Z"
    :channel "discovery"
    :event "failure_surface"
    :summary "3 failing tests detected"
    :details {:tests ["tokenizer::edge_case_empty"
                      "parser::nested_expr"
                      "lexer::unicode"]}}
   {:ts "2026-03-30T12:00:04Z"
    :channel "planning"
    :event "plan_generated"
    :summary "Investigate tokenizer boundary conditions"
    :details {:hypotheses ["off-by-one index" "unicode handling issue"]
              :strategy "fix tokenizer first"}
    :confidence 0.72}
   {:ts "2026-03-30T12:00:06Z"
    :channel "execution"
    :event "read_file"
    :details {:path "src/tokenizer.rs"}}
   {:ts "2026-03-30T12:00:08Z"
    :channel "execution"
    :event "apply_patch"
    :summary "Adjust loop boundary condition"
    :details {:file "src/tokenizer.rs" :lines_changed 12}}
   {:ts "2026-03-30T12:00:10Z"
    :channel "test"
    :event "run"
    :command "cargo test"
    :result "fail"
    :details {:passed 47 :failed 2}}
   {:ts "2026-03-30T12:00:11Z"
    :channel "evaluation"
    :event "failure_analysis"
    :summary "unicode test still failing"
    :details {:remaining_failure "lexer::unicode"
              :likely_cause "incorrect byte-length assumption"}
    :confidence 0.66}
   {:ts "2026-03-30T12:00:12Z"
    :channel "agent"
    :event "reasoning_summary"
    :summary "Tokenizer fixed; lexer likely miscounts UTF-8 width"}
   {:ts "2026-03-30T12:00:15Z"
    :channel "execution"
    :event "apply_patch"
    :summary "Switch to char iteration instead of byte indexing"}
   {:ts "2026-03-30T12:00:18Z"
    :channel "test"
    :event "run"
    :result "pass"
    :details {:passed 49 :failed 0}}
   {:ts "2026-03-30T12:00:19Z"
    :channel "git"
    :event "commit"
    :summary "fix: tokenizer boundary + unicode handling"
    :details {:files 2 :insertions 34 :deletions 12}}
   {:ts "2026-03-30T12:00:20Z"
    :channel "metrics"
    :event "usage"
    :details {:tokens_total 18234 :cost_usd 0.42 :loops 2}}])

(defn parse-kv [arg]
  (let [[k v] (str/split arg #"=" 2)]
    [(keyword k) v]))

(defn cli-map [args]
  (->> args
       (map parse-kv)
       (into {})))

(defn usage []
  (println "Usage:")
  (println "  bb -m orwelliana.core simulate path=trace.jsonl")
  (println "  bb -m orwelliana.core emit path=trace.jsonl channel=execution event=apply_patch summary='...' [details='{\"file\":\"x\"}']")
  (println "  bb -m orwelliana.core query path=trace.jsonl [channel=execution] [event=apply_patch]")
  (println "  bb -m orwelliana.core dashboard path=trace.jsonl")
  (println "  bb -m orwelliana.core derive path=trace.jsonl")
  (println "  bb -m orwelliana.core inspect-repo target=/path/to/repo [path=trace.jsonl]")
  (println "  bb -m orwelliana.core health-check target=/path/to/repo [path=trace.jsonl]")
  (println "  bb -m orwelliana.core fleet path=ops/fleet.edn"))

(defn ensure-parent! [path]
  (let [parent (.getParentFile (io/file path))]
    (when parent
      (.mkdirs parent))))

(defn valid-event? [event]
  (every? #(contains? event %) required-keys))

(defn read-trace [path]
  (if (.exists (io/file path))
    (with-open [r (io/reader path)]
      (->> (line-seq r)
           (remove str/blank?)
           (map #(json/parse-string % true))
           doall))
    []))

(defn write-events! [path events]
  (ensure-parent! path)
  (with-open [w (io/writer path)]
    (doseq [event events]
      (.write w (json/generate-string event))
      (.write w "\n"))))

(defn append-event! [path event]
  (when-not (valid-event? event)
    (throw (ex-info "Event missing required keys" {:required required-keys :event event})))
  (ensure-parent! path)
  (with-open [w (io/writer path :append true)]
    (.write w (json/generate-string event))
    (.write w "\n")))

(defn parse-details [s]
  (when s
    (json/parse-string s true)))

(defn simulate! [{:keys [path]}]
  (write-events! (or path "traces/sample.jsonl") sample-trace)
  (println (str "wrote " (count sample-trace) " events to " (or path "traces/sample.jsonl"))))

(defn emit! [{:keys [path details] :as opts}]
  (let [event (cond-> {:ts (or (:ts opts) (str (java.time.Instant/now)))
                       :channel (:channel opts)
                       :event (:event opts)}
                (:agent opts) (assoc :agent (:agent opts))
                (:repo opts) (assoc :repo (:repo opts))
                (:loop opts) (assoc :loop (parse-long (:loop opts)))
                (:summary opts) (assoc :summary (:summary opts))
                (:confidence opts) (assoc :confidence (Double/parseDouble (:confidence opts)))
                (:result opts) (assoc :result (:result opts))
                (:command opts) (assoc :command (:command opts))
                details (assoc :details (parse-details details)))]
    (append-event! (or path "traces/session.jsonl") event)
    (println (str "appended " (:channel event) "." (:event event) " to " (or path "traces/session.jsonl")))))

(defn matches? [filters event]
  (every?
   (fn [[k v]]
     (= (str (get event k)) v))
   (dissoc filters :path)))

(defn query! [{:keys [path] :as filters}]
  (doseq [event (filter #(matches? filters %) (read-trace (or path "traces/sample.jsonl")))]
    (println (json/generate-string event {:pretty true}))))

(defn last-event [events channel]
  (last (filter #(= channel (:channel %)) events)))

(defn latest-confidence [events]
  (some->> events
           reverse
           (filter :confidence)
           first
           :confidence))

(defn dashboard! [{:keys [path]}]
  (let [events (read-trace (or path "traces/sample.jsonl"))
        latest (last events)
        tests (last-event events "test")
        metrics (last-event events "metrics")
        recent (->> events
                    (filter :summary)
                    (take-last 3)
                    (map :summary))]
    (println (format "%-18s [LOOP %s]  %s"
                     (or (:agent (first events)) "agent")
                     (or (:loop latest) (get-in metrics [:details :loops]) "?")
                     (if (= "pass" (:result tests)) "GREEN" "WORKING")))
    (println "--------------------------------")
    (println (format "FAILURES:     %s" (or (get-in tests [:details :failed]) 0)))
    (println (format "LAST ACTION:  %s" (or (:summary latest) (:event latest) "n/a")))
    (println (format "CONFIDENCE:   %s" (or (latest-confidence events) "n/a")))
    (println (format "COST:         $%s" (or (get-in metrics [:details :cost_usd]) "n/a")))
    (println)
    (println "RECENT:")
    (doseq [summary recent]
      (println (str "- " summary)))))

(defn trajectory [events]
  (->> events
       (filter #(and (= "execution" (:channel %))
                     (= "apply_patch" (:event %))))
       (map #(or (:summary %) (:event %)))
       vec))

(defn confidence-curve [events]
  (->> events
       (keep :confidence)
       vec))

(defn failure-manifold [events]
  (->> events
       (filter #(= "evaluation" (:channel %)))
       (reduce
        (fn [acc event]
          (if-let [failure (get-in event [:details :remaining_failure])]
            (assoc acc failure [(get-in event [:details :likely_cause])])
            acc))
        {})))

(defn derive! [{:keys [path]}]
  (let [events (read-trace (or path "traces/sample.jsonl"))
        derived {:trajectory (trajectory events)
                 :confidence_curve (confidence-curve events)
                 :failure_manifold (failure-manifold events)}]
    (println (json/generate-string derived {:pretty true}))))

(defn read-edn-file [path]
  (with-open [r (io/reader path)]
    (edn/read {:eof nil} (java.io.PushbackReader. r))))

(defn count-services [services status]
  (count (filter #(= status (:status %)) services)))

(defn preferred-target [fleet]
  (some #(when (:preferred %) %) (:targets fleet)))

(defn fleet-summary [fleet]
  (let [services (:services fleet)
        targets (:targets fleet)]
    {:deployment (:deployment fleet)
     :preferred_target (:name (preferred-target fleet))
     :targets (count targets)
     :services_total (count services)
     :live (count-services services :live)
     :stale (count-services services :stale)
     :dead (count-services services :dead)
     :local_only (count (filter #(= :local (:location %)) services))}))

(defn services-by-status [fleet status]
  (->> (:services fleet)
       (filter #(= status (:status %)))
       (map (fn [service]
              {:name (:name service)
               :port (:port service)
               :target (:target service)
               :summary (:summary service)}))
       vec))

(defn fleet! [{:keys [path]}]
  (let [fleet (read-edn-file (or path "ops/fleet.edn"))
        summary (fleet-summary fleet)
        preferred (preferred-target fleet)
        report {:summary summary
                :preferred_target preferred
                :dead (services-by-status fleet :dead)
                :stale (services-by-status fleet :stale)}]
    (println (json/generate-string report {:pretty true}))))

(defn run-command [dir cmd]
  (let [result (process/shell {:out :string :err :string :dir dir :continue true} "bash" "-lc" cmd)]
    {:command cmd
     :exit (:exit result)
     :out (str/trim (:out result))
     :err (str/trim (:err result))}))

(defn detect-test-command [dir]
  (cond
    (.exists (io/file dir "mix.exs")) "mix lint"
    (.exists (io/file dir "bb.edn")) "bb -m test-runner"
    (.exists (io/file dir "Cargo.toml")) "cargo test"
    (.exists (io/file dir "package.json")) "npm test"
    :else nil))

(defn append-events! [path events]
  (doseq [event events]
    (append-event! path event)))

(defn inspect-repo! [{:keys [target path agent] :as _opts}]
  (let [trace-path (or path "traces/session.jsonl")
        repo-name (.getName (io/file target))
        git-status (run-command target "git status --short")
        manifests (->> ["mix.exs" "bb.edn" "Cargo.toml" "package.json" "pyproject.toml"]
                       (filter #(.exists (io/file target %)))
                       vec)
        test-command (detect-test-command target)
        now (str (java.time.Instant/now))]
    (append-events!
     trace-path
     [{:ts now
       :channel "system"
       :event "target_attached"
       :agent (or agent "orwelliana")
       :repo repo-name
       :summary "Attached operator to target repo"
       :details {:target (.getCanonicalPath (io/file target))}}
      {:ts now
       :channel "discovery"
       :event "repo_scan"
       :repo repo-name
       :summary "Inspected target repository"
       :details {:manifests manifests
                 :test_command test-command
                 :git (:exit git-status)
                 :dirty (not (str/blank? (:out git-status)))}}])
    (println (json/generate-string {:repo repo-name
                                    :target (.getCanonicalPath (io/file target))
                                    :manifests manifests
                                    :test_command test-command
                                    :git (:exit git-status)
                                    :dirty (not (str/blank? (:out git-status)))}
                                   {:pretty true}))))

(defn health-check! [{:keys [target path agent] :as _opts}]
  (let [trace-path (or path "traces/session.jsonl")
        repo-name (.getName (io/file target))
        test-command (detect-test-command target)]
    (when-not test-command
      (throw (ex-info "Could not detect test command for target repo" {:target target})))
    (let [started (str (java.time.Instant/now))
          result (run-command target test-command)
          ended (str (java.time.Instant/now))
          event {:ts ended
                 :channel "test"
                 :event "run"
                 :agent (or agent "orwelliana")
                 :repo repo-name
                 :command test-command
                 :result (if (zero? (:exit result)) "pass" "fail")
                 :summary (if (zero? (:exit result))
                            "Target repo health check passed"
                            "Target repo health check failed")
                 :details {:exit (:exit result)
                           :started_at started
                           :stdout (:out result)
                           :stderr (:err result)}}]
      (append-event! trace-path event)
      (println (json/generate-string event {:pretty true})))))

(defn -main [& args]
  (let [[command & rest] args
        opts (cli-map rest)]
    (case command
      "simulate" (simulate! opts)
      "emit" (emit! opts)
      "query" (query! opts)
      "dashboard" (dashboard! opts)
      "derive" (derive! opts)
      "fleet" (fleet! opts)
      "inspect-repo" (inspect-repo! opts)
      "health-check" (health-check! opts)
      (usage))))
