;; Copyright © 2014, OpenSensors.IO. All Rights Reserved.
(ns azondi.transports.mqtt
  (:require jig
            [jig.util :refer [satisfying-dependency]]
            [clojure.core.async :refer [put!]]
            [azondi.authentication :as auth])
  (:import  [io.netty.channel ChannelHandlerAdapter ChannelHandlerContext]
            jig.Lifecycle))

(def registered-topics
  {"/uk/gov/hackney/parking" #{"yodit" "paula"}
   "/juxt/big-red-button/malcolm" #{"malcolm"}
   "/juxt/big-red-button/yodit" #{"yodit"}})

(def ^:const supported-mqtt-protocol "MQIsdp")
(def ^:const supported-mqtt-version  3)

;;
;; CONNECT
;;

(defn supported-protocol?
  [^String protocol-name ^long protocol-version]
  (and (= protocol-name    supported-mqtt-protocol)
       (= protocol-version supported-mqtt-version)))

(defn disconnect-client
  [^ChannelHandlerContext ctx]
  (doto ctx
    (.writeAndFlush {:type :disconnect})
    .close))

(defn abort
  [^ChannelHandlerContext ctx]
  (.close ctx))

(defn valid-client-id?
  [^String client-id]
  ;; Section 3.1: valid client ids are between 1 and 23 characters
  (<= 1 (.length client-id) 23))

(defn ^:private maybe-disconnect
  "Disconnects existing client with the given client id, if any.

   See section 3.1."
  [^String client-id {:keys [connections-by-ctx connections-by-client-id]}]
  (if-let [state (get @connections-by-client-id client-id)]
    (let [ctx (:ctx state)]
      (println (format "Disconnecting existing connection with client id %s" client-id))
      (disconnect-client ctx)
      (dosync
       (alter connections-by-client-id dissoc client-id)
       (alter connections-by-ctx       dissoc ctx))
      ctx)))

(defn accept-connection
  [^ChannelHandlerContext ctx {:keys [username client-id has-will
                                      clean-session]
                               :as   msg}
   {:keys [connections-by-ctx connections-by-client-id] :as handler-state}]
  (let [conn {:username  username
              :client-id client-id
              :ctx       ctx
              :has-will  has-will
              :will-qos  (when has-will
                           (:will-qos msg))}]
    (maybe-disconnect client-id handler-state)
    (dosync
     (alter connections-by-ctx       assoc ctx conn)
     (alter connections-by-client-id assoc client-id conn))
    (.writeAndFlush ctx {:type :connack :return-code :accepted})
    conn))

(defn reject-connection
  [^ChannelHandlerContext ctx code]
  (doto ctx
    (.writeAndFlush {:type :connack :return-code code})
    .close))


;;
;; Handlers
;;

(defn handle-connect
  [^ChannelHandlerContext ctx {:keys [protocol-name
                                      protocol-version
                                      has-username
                                      has-password
                                      username
                                      password
                                      client-id] :as msg} connections]
  ;; example connect message:
  ;; {
  ;;  :type :connect,
  ;;  :client-id antares.1391193099727,
  ;;  :has-username true,
  ;;  :username michael@example.org,
  ;;  :has-password true,
  ;;  :password michael-pwd,
  ;;  :has-will false,
  ;;  :will-qos 0,
  ;;  :will-retain false,
  ;;  :qos 0,
  ;;  :clean-session true,
  ;;  :keepalive 60,
  ;;  :retain false,
  ;;  :protocol-name MQIsdp,
  ;;  :protocol-version 3,
  ;;  :dup false
  ;;  }
  (if (supported-protocol? protocol-name protocol-version)
    (if (and has-username has-password
             (auth/authenticates? username password))
      (if (valid-client-id? client-id)
        (accept-connection ctx msg connections)
        (reject-connection ctx :bad-username-or-password))
      (reject-connection ctx :bad-username-or-password))
    (do
      ;; TODO: logging
      (println (format "Unsupported protocol and/or version: %s v%d, disconnecting"
                       protocol-name
                       protocol-version))
      (reject-connection ctx :unacceptable-protocol-version))))

(defn handle-subscribe
  [^ChannelHandlerContext ctx {:keys [message-id
                                      topics] :as msg}
   {:keys [subscriptions] :as handler-state}]
  (dosync
   (alter subscriptions (fn [subs]
                          (reduce #(update-in %1 [%2] conj ctx)
                                  subs (map first (:topics msg))))))
  (.writeAndFlush ctx {:type :suback
                       :message-id message-id
                       :granted-qos (map (fn [[_ _]] 0) topics)}))

(defn handle-publish
  [^ChannelHandlerContext ctx
   {:keys [connections-by-ctx
           subscriptions
           channel] :as handler-state}]
  ;; example message:
  ;; {:payload #<byte[] [B@1503e6b>,
  ;;  :message-id 1,
  ;;  :topic a.topic,
  ;;  :type :publish,
  ;;  :dup false,
  ;;  :qos 1,
  ;;  :retain false}
  )

(defn handle-pingreq
  [^ChannelHandlerContext ctx _]
  (.writeAndFlush ctx {:type :pingresp}))

(defn handle-disconnect
  [^ChannelHandlerContext ctx _]
  (.close ctx))

;;
;; Netty glue
;;

(defn make-channel-handler
  [{:keys [channel
           connections-by-ctx
           connections-by-client-id
           subscriptions]
    :as handler-state}]
  (proxy [ChannelHandlerAdapter] []
    (channelRead [^ChannelHandlerContext ctx msg]
      (case (:type msg)
        :connect    (handle-connect    ctx msg handler-state)
        :subscribe  (handle-subscribe  ctx msg handler-state)
        :publish    (handle-publish    ctx msg handler-state)
        :pingreq    (handle-pingreq    ctx handler-state)
        :disconnect (handle-disconnect ctx handler-state)))
    (exceptionCaught [^ChannelHandlerContext ctx cause]
      (try (throw cause)
           (finally (abort ctx))))))

(deftype NettyMqttHandler [config]
  Lifecycle
  (init [_ system] system)
  (start [_ system]
    (let [ch (some (comp :channel system) (:jig/dependencies config))]
      (assoc-in system
                [(:jig/id config) :jig.netty/handler-factory]
                #(make-channel-handler {:subscriptions (ref {})
                                        :connections-by-ctx (ref {})
                                        :connections-by-client-id (ref {})
                                        :channel ch}))))
  (stop [_ system] system))
