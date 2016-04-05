(defproject blogpbt "0.1.0-SNAPSHOT"
  :description "Project for Blog post about Property Based Testing."
  :url ""
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [compojure "1.4.0"]
                 [ring/ring-defaults "0.1.5"]
                 [ring/ring-json "0.4.0"]
                 [com.taoensso/timbre "4.3.1"]]
  :plugins [[lein-ring "0.9.7"]]
  :ring {:handler blogpbt.handler/app}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.0"]
                        [cheshire "5.5.0"]
                        [org.clojure/test.check "0.9.0"]
                        [com.gfredericks/test.chuck "0.2.6"]
                        [mdrogalis/stateful-check "0.3.2"]]}})
