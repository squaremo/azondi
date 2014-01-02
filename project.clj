(defproject azondi "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :source-paths ["src" "src-cljs"]

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/core.async "0.1.256.0-1bf8cf-alpha"]

                 ;; Back-end

                 [clojurewerkz/machine_head "1.0.0-beta5"]
                 [clojurewerkz/cassaforte "1.3.0-beta7"]
                 [cheshire "5.2.0"]

                 ;; Front-end
                 [org.clojars.ideal-knee/dommy "0.1.3-SNAPSHOT"]
                 [io.pedestal/pedestal.app "0.2.2"]
                 [prismatic/dommy "0.1.1"]]
                )
