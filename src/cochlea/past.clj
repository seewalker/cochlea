;for now, the user whose information is getting stored is just the database user; the best solution for multiple useres remains to be seen.
(ns cochlea.past
  (:use [clojure.java.shell :only [sh]]
        [environ.core :only [env]]
        [incanter.core :only [view]]
        [incanter.stats :only [mean]]
        [incanter.charts :only [scatter-plot]])
  (:require [clojure.string :as string]
            [clojure.java.jdbc :as sql]
            [clj-time.core :as t]
            [cochlea.sounds :as sound]
            [me.raynes.conch :as conch]))

;to move forward, I must get good with manipulating querysets.
(defrecord LogItem [correct? practice_mode level timestamp])
(def using-db (atom (contains? #{"postgres" "sqlite"} (:db-choice env)) ))
(def db-name (:db-user env))
(def db-user (:db-name env))
(def relation "cochlea")
(def user (:username env)) ;this is the user playing the game.
(def host (:db-host env))
(def sess-id (atom 0))

(def db
    (case (:db-choice env)
    "postgres" { :classname "org.postgresql.Driver"
                 :subprotocol "postgresql"
                 :subname (format "//%s:5432/%s" host db-name)
                 :user db-user }
    "sqlite" {:classname "org.sqlite.JDBC"
              :subprotocol "sqlite"
              :subname "resources/cochlea_sqlite.db" }))
(defn pg-list
  "Takes a "
  [vec is-string?]
  (let [element (if is-string? "\"%s\"" "%s")]
    (str "'{"
         (string/join ", " (map (fn [item] (format element (str item))) vec ))
         "}'")))

(defn sequence-of
  [username]
  (format "session_%s" username))

(def query-options
    (let [diff (:default-difficulty env )
          mode (:default-mode env ) ]
        (atom { :default-level diff
                :levels [diff]
                :default-mode mode
                :modes [mode]
                :window-N (:window-N env)
                :current-N (:current-N env)})))

(def queries
  (atom {:by-session {:id "by-session"
                      :descr "Shows proportion of correct answers by session"
                      :query-str #(format "SELECT avg(isCorrect :: int), sessionID
FROM %s
WHERE (level = ANY (%s)) AND (mode = ANY (%s))
GROUP BY sessionID
HAVING count(*) > %s"
                                         relation (pg-list (:levels @query-options) true) (pg-list (:modes @query-options) true) (:threshold env))
                     :rator (fn [s] (sql/query db [s]))
                     :x-y (fn [data] [(map :sessionid data) (map :avg data)])}
         :windows  {:id "windows"
                    :descr "Shows proportion of correct answers per window-size block of answers"
                    :query-str #(format "SELECT isCorrect :: Integer
FROM %s
WHERE (level = ANY (%s)) AND (mode = ANY (%s))
ORDER BY sessionID, t"
                                       relation (pg-list (@query-options :levels) true) (pg-list (@query-options :modes) true))
                    :rator (fn [s] (map mean (partition (:window-N @query-options) (map :iscorrect (sql/query db [s])))))
                    :x-y (fn [means] [(range (count means)) means])
                    }
         :current {:id "current"
                   :descr "Shows proportion of correct answers within slices of the current session"
                   :query-str #(format "SELECT isCorrect :: Integer
FROM %s
WHERE (level = ANY (%s)) AND (mode = ANY (%s)) AND sessionID = %s
ORDER BY sessionID, t"
                                      relation (pg-list (@query-options :levels) true) (pg-list (@query-options :modes) true) sess-id)
                   :rator (fn [s] (map mean (partition (:current-N @query-options) (sql/query db [s]))))
                   :x-y (fn [means] [(range (count means)) means])
                   }
         :which    {:id "which"
                   :descr "shows which things often confused for each other."
                   :query-str #(format "")
                   :rator identity
                   :x-y identity
                   }}))
;this is an initialization.
(def query (atom (:by-session @queries)))
(defn run-query
  ([] ((:rator @query) ((:query-str @query))))
  ([qstr] (sql/query db [qstr])))
(defn visualize
  "A plotting function that operates on queries that observe the accuracy-ish, time-ish format.
  This is a scatter plot of"
   []
   (let [data (run-query)
         [x1 x2] ((:x-y @query) data)
         xlabel (case (:id @query)
                  "by-session" "Session ID"
                  "windows" (str "Blocks of " (:window-N @query-options) " guesses")
                  "within-current" (str "Blocks of " (:current-N @query-options) " guesses"))]
     (view (scatter-plot x1 x2
                         :x-label xlabel
                         :y-label "Proportion Correct"
                         ))
     (-> data vec str)))

(defn dbstore
  "Does persistent storage of a user's guess, whether using db or plaintext."
    [opts cache status]
    (let [level (@opts :level)
          mode (@opts :practice-mode)
          insert (case (:db-choice env)
                    "postgres" (format "INSERT INTO %s VALUES('%s', %s, '%s', '%s', now(), %s);" relation user status mode level @sess-id)
                    "sqlite" (format "INSERT INTO %s VALUES('%s', '%s', '%s', '%s', date('now'), %s);" relation user (str status) (str mode) (str level) @sess-id)
                    "" "")]
        (if @using-db
                (sql/execute! db [insert])
                (spit "resources/history.log"
                    (prn-str
                      (LogItem. status mode level (t/now)))
                    :append true))))

(defn program-passes?
  "executes the conch program"
  [prog & args]
  (let [out (apply prog (conj (vec args) {:verbose true :throw false}))] ;transforming 'args' to vector so that conj places hash at tail.
    (if (= 0 (:exit-code out))
      true
      (do
        (println (:stderr out))
        false))))

(defn pg-running?
  []
  (try
    (do
       (sql/query db (format "SELECT * FROM %s WHERE username = '%s'" relation user))
       true)
    (catch Exception e false)))

(defn tap-db
  "Attempts to start database if database is desired, using plaintext logging as failover.
   This must be run exactly once per session in order for the sessionID value to be coherant."
    []
    (let [counter (sequence-of db-user)]
      (println counter)
      (case (:db-choice env)
          "postgres" (if-not pg-running?
                          (swap! using-db not) ;because postgres runs as a server process, it can't be used if it is down.
                          (if (some true? ;checking if the sequence exists
                                     (map #(= (:relname %) counter)
                                           (sql/query db ["SELECT * FROM pg_class WHERE relkind = 'S'"])))
                                (swap! sess-id (fn [junk] (:nextval (first (sql/query db [(format "SELECT nextval('%s')" counter)]))))) 
                                (sql/execute! db [(format "CREATE SEQUENCE %s" counter)])))
          "sqlite" (do (println (sql/query db ["SELECT max(sessionid)+1 AS newid FROM Sessionid"]))
                       (swap! sess-id (fn [junk] (:newid (first (sql/query db ["SELECT max(sessionid)+1 AS newid FROM Sessionid"])))))))))

(defn valid-db?
  "takes output from 'psql -l'"
  [tabular-out]
  (let [pattern (format ".*%s.*%s.*" db-name db-user)]
    (some true?
          (map (fn [row] (-> (re-matches (re-pattern pattern) row) nil? not))
               (string/split tabular-out #"\n")))))
(defn establish-db
    "preconditions to database setup working:
        postgres is installed.
        The user associated with the app process has permission to create a database for cochlea.
        there is a database role for the user associated with the app process.
        this role has the ability to create databases."
    []
    (let [pg-create-types (format "CREATE TYPE modeT AS ENUM (%s); CREATE TYPE levelT AS ENUM (%s) ;" (pg-list (keys sound/choices) true) (pg-list sound/levels true))
          pg-create-table (format "CREATE TABLE %s (username varchar, isCorrect boolean, mode modeT, level levelT, t time, sessionID int);" relation)
          lite-create-table (format "CREATE TABLE %s (username varchar, isCorrect varchar, mode varchar, level varchar, t varchar, sessionID integer);" relation)
          db-description "This database dbstores users' performance with the cochlea ear training application."
          pg-initialize #(do ( sql/execute! db [pg-create-types] )
                          ( sql/execute! db [pg-create-table] ))]
                     (case (:db-choice env)
                        "postgres" (conch/with-programs [createdb psql]
                                     (do
                                        (println "trying to create the postgres database now.")
                                        (if-not (valid-db? (psql "-l"))
                                            (if-not (program-passes? createdb (str db-name) db-description)
                                              (swap! using-db not)
                                              (pg-initialize))
                                            (pg-initialize))))
                        "sqlite" (do
                                    (println "trying to create sqlite database now.")
                                    (sql/execute! db [lite-create-table])
                                    (sql/execute! db ["CREATE TABLE Sessionid(sessionid integer);"])
                                    (sql/execute! db ["INSERT INTO Sessionid VALUES(0);"])))
      (println "done initializing")))
