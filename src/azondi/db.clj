(ns azondi.db
  "Database connectivity."
  (:require [clojurewerkz.cassaforte.client :as cc]
            [clojurewerkz.cassaforte.cql :as cql])
  (:import jig.Lifecycle))


(deftype Database [config]
  Lifecycle
  (init [_ system]
    (println config)
    system)
  (start [_ system]
    (let [host (:hosts config)
          ks   (:keyspace config)]
      (cc/connect! host)
      (cql/use-keyspace ks)))
  (stop [_ system]
    system))
