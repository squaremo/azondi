;; Subscription lookup

(ns azondi.transports.mqtt.subs
  (:refer-clojure :exclude [remove empty])
  (:use [clojure.set :only [union]]))

;; A tree is a nested map with sets at the leaves. To avoid
;; overwriting leaves when inserting longer topics (e.g., inserting
;; ["foo" "bar"] after inserting ["foo"]), the end of a topic is
;; represented by nil.

(defn empty [] {})

(defn insert [tree topic value]
  (update-in tree (conj topic nil) (fnil conj #{}) value))

(defn matches [tree topic]
  (loop [node tree
         topic topic
         backtrack '()
         result #{}]
    (cond
     ;; Dead end. We got here because a key didn't exist (or the whole
     ;; tree is empty).
     (nil? node)
     (if (empty? backtrack)
       result
       (recur (first backtrack) (second backtrack)
              (drop 2 backtrack) result))

     ;; No more things to match. However, there's the empty
     ;; transition, keyed by nil, and the "all remaining" transition,
     ;; keyed by "#", to consider.
     (empty? topic)
     (recur (node "#") topic backtrack
            (union result (node nil)))

     ;; Still topic and node to go; can backtrack to a "+" or "#" from
     ;; here.
     :else
     (recur (node (first topic)) (rest topic)
            (conj backtrack
                  (rest topic) (node "+")
                  [] (node "#")) result))))

;; Removal is a bit trickier than insertion.
(defn remove [tree topic value]

  (defn- zipup [child path]
    (loop [node child
           path path]
      (cond
       (empty? path)
       node
       (empty? node)
       (recur (dissoc (first path) (second path)) (drop 2 path))
       :else
       (recur (assoc (first path) (second path) node) (drop 2 path)))))

  (loop [node tree
         topic topic
         path '()]
    (cond
     ;; Topic not found, we can leave (ha) the tree alone.
     (nil? node)
     tree
     
     (empty? topic)
     (if-let [leaf (node nil)]
       (let [newleaf (disj leaf value)
             without (if (empty? newleaf) (dissoc node nil) node)]
         (zipup without path)))
     
     :else
     (recur (node (first topic)) (rest topic)
            (conj path (first topic) node)))))
