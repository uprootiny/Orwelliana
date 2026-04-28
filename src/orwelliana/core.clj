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

(def sample-conversation
  [{:ts "2026-03-30T12:00:00Z"
    :channel "conversation"
    :event "message"
    :session "demo"
    :summary "You are attached to parser-lib. Keep notes terse."
    :details {:role "system"
              :content "You are attached to parser-lib. Keep notes terse."
              :content_chars 48}}
   {:ts "2026-03-30T12:00:01Z"
    :channel "conversation"
    :event "message"
    :session "demo"
    :summary "Find the current failures."
    :details {:role "user"
              :content "Find the current failures."
              :content_chars 26}}
   {:ts "2026-03-30T12:00:02Z"
    :channel "conversation"
    :event "message"
    :session "demo"
    :summary "Three tests are failing in tokenizer, parser, and lexer."
    :details {:role "assistant"
              :content "Three tests are failing in tokenizer, parser, and lexer."
              :content_chars 57}}
   {:ts "2026-03-30T12:00:03Z"
    :channel "conversation"
    :event "message"
    :session "demo"
    :summary "Focus on the tokenizer first and keep the context small."
    :details {:role "user"
              :content "Focus on the tokenizer first and keep the context small."
              :content_chars 58}}
   {:ts "2026-03-30T12:00:04Z"
    :channel "conversation"
    :event "message"
    :session "demo"
    :summary "I will patch tokenizer.rs and rerun cargo test."
    :details {:role "assistant"
              :content "I will patch tokenizer.rs and rerun cargo test."
              :content_chars 48}}])

(declare detect-test-command)

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
  (println "  bb -m orwelliana.core emit-message path=trace.jsonl session=ops role=user content='Investigate the failing tests'")
  (println "  bb -m orwelliana.core query path=trace.jsonl [channel=execution] [event=apply_patch]")
  (println "  bb -m orwelliana.core convo path=trace.jsonl [session=ops] [limit=6] [chars=4000]")
  (println "  bb -m orwelliana.core dashboard path=trace.jsonl")
  (println "  bb -m orwelliana.core derive path=trace.jsonl")
  (println "  bb -m orwelliana.core inspect-repo target=/path/to/repo [path=trace.jsonl]")
  (println "  bb -m orwelliana.core health-check target=/path/to/repo [path=trace.jsonl]")
  (println "  bb -m orwelliana.core deploy-doctor [target=.] [repo=owner/name] [verify_tests=true|false] [remote_checks=true|false]")
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

(defn parse-int [value default]
  (if (some? value)
    (parse-long value)
    default))

(defn parse-bool [value default]
  (if (some? value)
    (contains? #{"true" "1" "yes" "on"} (str/lower-case (str value)))
    default))

(defn preview-text [text]
  (let [trimmed (str/trim (or text ""))]
    (if (> (count trimmed) 96)
      (str (subs trimmed 0 93) "...")
      trimmed)))

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

(defn conversation-message? [event]
  (and (= "conversation" (:channel event))
       (= "message" (:event event))))

(defn message-role [event]
  (get-in event [:details :role]))

(defn message-content [event]
  (or (get-in event [:details :content]) ""))

(defn conversation-events
  ([events]
   (conversation-events events nil))
  ([events session]
   (cond->> events
     true (filter conversation-message?)
     session (filter #(= session (:session %))))))

(defn conversation-sessions [events]
  (->> events
       conversation-events
       (map :session)
       (remove str/blank?)
       distinct
       vec))

(defn latest-session [events]
  (some-> (last (conversation-events events))
          :session))

(defn normalize-message [event]
  {:ts (:ts event)
   :session (:session event)
   :role (message-role event)
   :content (message-content event)
   :summary (:summary event)
   :chars (count (message-content event))})

(defn clip-text [text max-chars]
  (let [text (or text "")]
    (cond
      (neg? max-chars) ""
      (<= (count text) max-chars) text
      (<= max-chars 3) (subs text 0 max-chars)
      :else (str (subs text 0 (- max-chars 3)) "..."))))

(defn clipped-message [event max-chars]
  (let [content (message-content event)
        clipped (clip-text content max-chars)]
    {:ts (:ts event)
     :session (:session event)
     :role (message-role event)
     :content clipped
     :summary (:summary event)
     :chars (count clipped)
     :truncated (< (count clipped) (count content))}))

(defn bounded-history-window [events {:keys [session limit chars]}]
  (let [resolved-session (or session (latest-session events))
        limit (or limit 6)
        chars (or chars 4000)
        session-events (vec (conversation-events events resolved-session))
        system-anchor (last (filter #(= "system" (message-role %)) session-events))
        reserved-chars (if system-anchor
                         (min chars (count (message-content system-anchor)))
                         0)
        non-system (remove #(= "system" (message-role %)) session-events)
        selected (loop [remaining (reverse non-system)
                        chosen []
                        used-chars 0]
                   (if (or (empty? remaining)
                           (>= (count chosen) limit))
                     chosen
                     (let [event (first remaining)
                           content-size (count (message-content event))
                           next-size (+ used-chars content-size)
                           include? (or (empty? chosen)
                                        (<= (+ reserved-chars next-size) chars))]
                       (recur (rest remaining)
                              (if include? (conj chosen event) chosen)
                              (if include? next-size used-chars)))))
        raw-window-events (cond->> (reverse selected)
                            system-anchor (cons system-anchor))
        messages (loop [remaining raw-window-events
                        acc []
                        remaining-chars chars]
                   (if (empty? remaining)
                     acc
                     (let [event (first remaining)
                           message (clipped-message event remaining-chars)
                           next-remaining (- remaining-chars (:chars message))]
                       (recur (rest remaining)
                              (conj acc message)
                              next-remaining))))]
    {:session resolved-session
     :messages (vec (remove #(zero? (:chars %)) messages))
     :message_count (count raw-window-events)
     :chars (reduce + 0 (map :chars messages))}))

(defn role-counts [events]
  (->> events
       conversation-events
       (map message-role)
       frequencies
       (into (sorted-map))))

(defn conversation-view [events {:keys [session limit chars]}]
  (let [messages (conversation-events events session)]
    {:sessions (conversation-sessions events)
     :sessions_count (count (conversation-sessions events))
     :messages_count (count messages)
     :roles (role-counts (if session messages events))
     :latest_session (latest-session events)
     :window (bounded-history-window events {:session session
                                             :limit limit
                                             :chars chars})}))

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
                 :failure_manifold (failure-manifold events)
                 :conversation (conversation-view events {:limit 6 :chars 4000})}]
    (println (json/generate-string derived {:pretty true}))))

(defn emit-message! [{:keys [path session role content] :as opts}]
  (when (str/blank? session)
    (throw (ex-info "Conversation messages require session=..." {:opts opts})))
  (when (str/blank? role)
    (throw (ex-info "Conversation messages require role=..." {:opts opts})))
  (when (str/blank? content)
    (throw (ex-info "Conversation messages require content=..." {:opts opts})))
  (let [event (cond-> {:ts (or (:ts opts) (str (java.time.Instant/now)))
                       :channel "conversation"
                       :event "message"
                       :session session
                       :summary (preview-text content)
                       :details {:role role
                                 :content content
                                 :content_chars (count content)}}
                (:agent opts) (assoc :agent (:agent opts))
                (:repo opts) (assoc :repo (:repo opts))
                (:model opts) (assoc-in [:details :model] (:model opts))
                (:provider opts) (assoc-in [:details :provider] (:provider opts))
                (:tokens opts) (assoc-in [:details :tokens] (parse-long (:tokens opts))))]
    (append-event! (or path "traces/session.jsonl") event)
    (println (str "appended conversation.message to " (or path "traces/session.jsonl")))))

(defn convo! [{:keys [path session limit chars]}]
  (let [events (read-trace (or path "traces/sample.jsonl"))
        view (conversation-view events {:session session
                                        :limit (parse-int limit 6)
                                        :chars (parse-int chars 4000)})]
    (println (json/generate-string view {:pretty true}))))

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

(defn run-json-command [dir cmd]
  (let [result (run-command dir cmd)]
    (assoc result
           :json
           (when (and (zero? (:exit result))
                      (not (str/blank? (:out result))))
             (json/parse-string (:out result) true)))))

(defn current-branch [status-line]
  (second (re-find #"^## ([^.\s]+)" (or status-line ""))))

(defn ahead-behind [status-line]
  {:ahead (if-let [[_ n] (re-find #"ahead (\d+)" (or status-line ""))]
            (parse-long n)
            0)
   :behind (if-let [[_ n] (re-find #"behind (\d+)" (or status-line ""))]
             (parse-long n)
             0)})

(defn tracking-branch [status-line]
  (second (re-find #"^## [^.\s]+\.\.\.([^ ]+)" (or status-line ""))))

(defn git-state [status-output]
  (let [[headline & body] (str/split-lines (or status-output ""))]
    (merge
     {:branch (current-branch headline)
      :tracking (tracking-branch headline)
      :dirty (boolean (seq body))
      :changed_files (count body)}
     (ahead-behind headline))))

(defn origin-url->slug [origin-url]
  (some-> origin-url
          str/trim
          (str/replace #"^git@github\.com:" "")
          (str/replace #"^https://github\.com/" "")
          (str/replace #"\.git$" "")))

(defn repo-slug [dir explicit-repo]
  (or explicit-repo
      (let [origin (run-command dir "git remote get-url origin")]
        (when (zero? (:exit origin))
          (origin-url->slug (:out origin))))))

(defn gh-run-list [dir slug]
  (if (str/blank? slug)
    {:error "No GitHub repo slug available"}
    (run-json-command dir
                      (str "gh run list --repo " slug
                           " --limit 10 --json databaseId,workflowName,status,conclusion,displayTitle,headBranch,headSha,url"))))

(defn pages-state [dir slug]
  (if (str/blank? slug)
    {:error "No GitHub repo slug available"}
    (let [repo (run-json-command dir (str "gh api repos/" slug))
          pages (run-json-command dir (str "gh api repos/" slug "/pages"))]
      {:repo repo
       :pages pages})))

(defn public-url [pages]
  (or (get-in pages [:pages :json :html_url])
      (get-in pages [:repo :json :homepage])))

(defn http-head [dir url]
  (if (str/blank? url)
    {:error "No public URL available"}
    (let [result (run-command dir (str "curl -I -L --max-time 10 -s " url))
          status-line (last (filter #(re-find #"^HTTP/" %) (str/split-lines (:out result))))
          status-code (some->> status-line
                               (re-find #"HTTP/\S+ (\d+)")
                               second
                               parse-long)]
      (assoc result :status_code status-code))))

(defn workflow-state [runs workflow-name]
  (first (filter #(= workflow-name (:workflowName %)) runs)))

(defn verdict [severity summary details]
  {:severity severity
   :summary summary
   :details details})

(defn deployment-verdicts [doctor]
  (let [git (:git doctor)
        remote? (if (contains? doctor :remote_checks)
                  (boolean (:remote_checks doctor))
                  true)
        repo (get-in doctor [:github :repo :json])
        pages (get-in doctor [:github :pages :json])
        http (:http doctor)
        runs (:runs doctor)
        ci (workflow-state runs "CI")
        pages-run (workflow-state runs "Pages")]
    (vec
     (concat
      (when (:dirty git)
        [(verdict :warn "Worktree is dirty"
                  {:changed_files (:changed_files git)})])
      (when (pos? (:ahead git))
        [(verdict :warn "Local branch is ahead of remote"
                  {:ahead (:ahead git) :tracking (:tracking git)})])
      (when (pos? (:behind git))
        [(verdict :error "Local branch is behind remote"
                  {:behind (:behind git) :tracking (:tracking git)})])
      (when (and remote? (not repo))
        [(verdict :error "GitHub repo metadata is unavailable"
                  {:error (get-in doctor [:github :repo :err])})])
      (when (and remote? repo (not (:has_pages repo)))
        [(verdict :error "GitHub Pages is not provisioned"
                  {:repo (:full_name repo)})])
      (when (and remote? repo (:has_pages repo) (nil? pages))
        [(verdict :error "Pages metadata is unavailable"
                  {:repo (:full_name repo)
                   :error (get-in doctor [:github :pages :err])})])
      (when (and remote? ci (not= "success" (:conclusion ci)))
        [(verdict :warn "Latest CI run is not green"
                  {:status (:status ci) :conclusion (:conclusion ci) :url (:url ci)})])
      (when (and remote? pages-run (not= "success" (:conclusion pages-run)))
        [(verdict :warn "Latest Pages run is not green"
                  {:status (:status pages-run) :conclusion (:conclusion pages-run) :url (:url pages-run)})])
      (when (and remote? (:status_code http) (not= 200 (:status_code http)))
        [(verdict :error "Public URL is not healthy"
                  {:url (public-url (:github doctor)) :status_code (:status_code http)})])))))

(defn deploy-score [verdicts]
  (let [errors (count (filter #(= :error (:severity %)) verdicts))
        warns (count (filter #(= :warn (:severity %)) verdicts))]
    (cond
      (pos? errors) :blocked
      (pos? warns) :degraded
      :else :ready)))

(defn deploy-summary [doctor]
  (let [verdicts (deployment-verdicts doctor)
        remote? (if (contains? doctor :remote_checks)
                  (boolean (:remote_checks doctor))
                  true)]
    {:repo (:slug doctor)
     :status (deploy-score verdicts)
     :branch (get-in doctor [:git :branch])
     :head (:head doctor)
     :remote_checks remote?
     :public_url (public-url (:github doctor))
     :pages_provisioned (when (contains? (or (get-in doctor [:github :repo :json]) {}) :has_pages)
                          (boolean (get-in doctor [:github :repo :json :has_pages])))
     :http_status (:status_code (:http doctor))
     :verdicts verdicts}))

(defn deploy-doctor-data [{:keys [target repo verify_tests remote_checks]}]
  (let [dir (.getCanonicalPath (io/file (or target ".")))
        status (run-command dir "git status --short --branch")
        head (run-command dir "git rev-parse HEAD")
        slug (repo-slug dir repo)
        remote-checks? (parse-bool remote_checks true)
        github (if remote-checks?
                 (pages-state dir slug)
                 {:skipped true})
        runs-result (if remote-checks?
                      (gh-run-list dir slug)
                      {:json [] :skipped true})
        public (if remote-checks?
                 (public-url github)
                 nil)
        http (if remote-checks?
               (http-head dir public)
               {:skipped true})
        verify-tests? (parse-bool verify_tests true)
        test-command (detect-test-command dir)
        tests (when (and verify-tests? test-command)
                (run-command dir test-command))
        doctor {:target dir
                :slug slug
                :head (when (zero? (:exit head)) (:out head))
                :git (git-state (:out status))
                :remote_checks remote-checks?
                :test_command test-command
                :verify_tests verify-tests?
                :tests (when tests
                         {:exit (:exit tests)
                          :result (if (zero? (:exit tests)) "pass" "fail")
                          :command test-command})
                :github github
                :runs (or (:json runs-result) [])
                :http http}]
    (assoc doctor :summary (deploy-summary doctor))))

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

(defn deploy-doctor! [opts]
  (println (json/generate-string (deploy-doctor-data opts) {:pretty true})))

(defn -main [& args]
  (let [[command & rest] args
        opts (cli-map rest)]
    (case command
      "simulate" (simulate! opts)
      "emit" (emit! opts)
      "emit-message" (emit-message! opts)
      "query" (query! opts)
      "convo" (convo! opts)
      "dashboard" (dashboard! opts)
      "derive" (derive! opts)
      "fleet" (fleet! opts)
      "inspect-repo" (inspect-repo! opts)
      "health-check" (health-check! opts)
      "deploy-doctor" (deploy-doctor! opts)
      (usage))))
