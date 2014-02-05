;; Subscription lookup

(ns azondi.transports.mqtt.subs
  (:refer-clojure :exclude [remove empty]
                  :rename {empty? null?})
  (:use [clojure.set :only [union]]))

(deftype Node [children anytail empty])

(def EMPTY (Node. {} #{} #{}))

(defn empty [] EMPTY)

(defn empty? [tree] (= tree EMPTY))


(defn- make-node [children anytail empty]
  (if (and (null? children) (null? anytail) (null? empty))
    EMPTY
    (Node. children anytail empty)))

(defn- node-without [node prefix]
  (make-node (dissoc (.children node) prefix)
             (.anytail node) (.empty node)))

(defn- node-without-empty [node value]
  (make-node (.children node) (.anytail node)
             (disj (.empty node) value)))

(defn- node-without-anytail [node value]
  (make-node (.children node)
             (disj (.anytail node) value) (.empty node)))

(defn- node-with [node prefix child]
  (Node. (assoc (.children node) prefix child)
         (.anytail node) (.empty node)))


(defn matches [tree topic]
  (loop [node tree
         topic topic
         backtrack '()
         result #{}]
    (cond
     (nil? node)
     (if (null? backtrack)
       result
       (recur (first backtrack) (second backtrack)
              (drop 2 backtrack) result))
     
     (null? topic)
     ;; simple way to backtrack
     (recur nil [] backtrack (union (.anytail node) (.empty node) result))
     
     :else
     (let [children (.children node)
           anytail  (.anytail node)
           tail (rest topic)]
       (recur (children (first topic)) tail
              (conj backtrack tail (children "+"))
              (union result anytail))))))

(defn insert [tree topic value]

  (defn zipup [tail path]
    (loop [node tail
           path path]
      (if (null? path)
        node
        (let [prefix (first path)
              parent (second path)]
          (recur (node-with parent prefix node) (drop 2 path))))))

  (loop [node tree
         topic topic
         path '()]
    (cond
     ;; if there's no such child
     (nil? node)
     (let [revtopic (reverse topic)
           tail (reduce (fn [node prefix]
                          (Node. {prefix node} #{} #{}))
                        (cond
                         (null? revtopic) (Node. {} #{} #{value})
                         (= "#" (first revtopic)) (Node. {} #{value} #{})
                         :else (Node. {(first revtopic)
                                       (Node. {} #{} #{value})} #{} #{}))
                        (rest revtopic))]
       (zipup tail path))
     
     (= ["#"] topic)
     (zipup (Node. (.children node)
                   (conj (.anytail node) value)
                   (.empty node)) path)
     
     (null? topic)
     (zipup (Node. (.children node)
                   (.anytail node)
                   (conj (.empty node) value)) path)
     
     :else
     (recur ((.children node) (first topic))
            (rest topic) (conj path node (first topic))))))


(defn remove [tree topic value]

  (defn zipup [child path]
    (loop [node child
           path path]
      (cond
       (null? path)
       node
       
       (= EMPTY node)
       (recur (node-without (second path) (first path)) (drop 2 path))

       :else
       (let [prefix    (first path)
             parent    (second path)
             newparent (node-with parent prefix node)]
         (recur newparent (drop 2 path))))))

  (loop [node tree
         topic topic
         path '()]
    (cond
     (nil? node)
     tree

     (null? topic)
     (zipup (node-without-empty node value) path)

     (= ["#"] topic)
     (zipup (node-without-anytail node value) path)

     :else
     (recur ((.children node) (first topic))
            (rest topic) (conj path node (first topic))))))
