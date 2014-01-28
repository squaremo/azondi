(ns azondi.development.seed
  "Seeds the database with data useful for development"
  (:require [clojurewerkz.cassaforte.client :as cc]
            [clojurewerkz.cassaforte.cql :refer :all]
            [clojurewerkz.cassaforte.query :refer :all])
  (:import jig.Lifecycle))

(defn seed-users
  [_]
  (let [t "users"]
    (insert t {:fname "Yodit"
               :sname "S"
               :id    "yods"
               :email "yods@example.org"
               :pword "yods-pwd"
               :role  "user"
               :publisher true}
            (if-not-exists))
    (insert t {:fname "Malcolm"
               :sname "S"
               :id    "malcolm"
               :email "malcolm@example.org"
               :pword "malcolm-pwd"
               :role  "user"
               :publisher true}
            (if-not-exists))
    (insert t {:fname "Michael"
               :sname "K"
               :id    "novemberain"
               :email "michael@example.org"
               :pword "michael-pwd"
               :role  "user"
               :publisher true}
            (if-not-exists))
    (insert t {:fname "John"
               :sname "Appleseed"
               :id    "appleseed"
               :email "appleseed@example.org"
               :pword "appleseed-pwd"
               :role  "user"
               :publisher false}
            (if-not-exists))))

(defn seed
  [system]
  (seed-users system))

(deftype Database [config]
  Lifecycle
  (init [_ system]
    system)
  (start [_ system]
    (seed system)
    system)
  (stop [_ system]
    system))
