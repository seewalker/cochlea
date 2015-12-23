(defproject cochlea "0.1.0-SNAPSHOT"
  :description "An interactive ear training GUI application"
  :url "http://github.com/seewalker/cochlea"
  :license {:name "GPL v3"
            :url "http://www.gnu.org/copyleft/gpl.html"}
  :repositories {"conjars" "http://conjars.org/repo"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [overtone "0.9.1"]
                 [org.clojure/tools.trace "0.7.5"]
                 [org.clojure/java.jdbc "0.3.7"]
                 [postgresql "9.1-901.jdbc4"]
                 [org.xerial/sqlite-jdbc "3.8.11"]
                 [environ "0.5.0"]
                 [incanter "1.9.0"]
                 [clj-time "0.9.0"]
                 [me.raynes/conch "0.8.0"]
                 [seewalker/philharmonia-samples "0.0.1-SNAPSHOT"]
                 [seesaw "1.4.2" :exclusions [org.clojure/clojure]]]
  ;:profiles { :uberjar  {:aot :all} }
  :repl-options { :timeout 120000}
  :main ^{:skip-aot true} cochlea.core
  :target-path "target/%s")
