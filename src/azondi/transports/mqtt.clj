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

(defn supported-protocol?
  [^String protocol-name ^long protocol-version]
  (and (= protocol-name    supported-mqtt-protocol)
       (= protocol-version supported-mqtt-version)))

(defn disconnect-client
  [^ChannelHandlerContext ctx]
  (doto ctx
    (.writeAndFlush {:type :disconnect})
    .close))

(defn accept-connection
  [^ChannelHandlerContext ctx {:keys [username]
                               :as   msg} connections]
  (let [conn {:username username
              :will-qos (:will-qos msg)}]
    (dosync
     (alter connections assoc ctx conn))
    (.writeAndFlush ctx {:type :connack})
    conn))

;;
;; Handlers
;;

(defn handle-connect
  [^ChannelHandlerContext ctx {:keys [protocol-name
                                      protocol-version
                                      has-username
                                      has-password
                                      username
                                      password] :as msg} connections]
  ;; example msg:
  ;; {:dup false,
  ;;  :has-will false,
  ;;  :has-username true,
  ;;  :qos 0,
  ;;  :client-id antares.1391193099727,
  ;;  :clean-session true,
  ;;  :will-retain false,
  ;;  :username michael@example.org,
  ;;  :has-password true,
  ;;  :keepalive 60,
  ;;  :type :connect,
  ;;  :retain false,
  ;;  :protocol-name MQIsdp,
  ;;  :protocol-version 3,
  ;;  :password michael-pwd,
  ;;  :will-qos 0}
  (if (supported-protocol? protocol-name protocol-version)
    (if (and has-username has-password
             (auth/authenticates? username password))
      (accept-connection ctx msg connections)
      (disconnect-client ctx))
    (do
      ;; TODO: logging
      (println (format "Unsupported protocol and/or version: %s v%d, disconnecting"
                       protocol-name
                       protocol-version))
      (disconnect-client ctx))))

(defn handle-subscribe
  [^ChannelHandlerContext ctx msg subs]
  (.writeAndFlush ctx {:type :suback})
  (dosync
   (alter subs (fn [subs]
                 (reduce #(update-in %1 [%2] conj ctx)
                         subs (map first (:topics msg)))))))

(defn handle-publish
  [^ChannelHandlerContext ctx msg connections channel]
  (println "Got message!" (pr-str ctx) (pr-str msg))
  (let [conn (get @connections ctx)]
    (when conn
      (when channel
        (println "putting on core.async channel")
        (put! channel msg))
      (println "Sending response to conn: " conn)
      ;; Handle subscribers, although this should probably happen somewhere else now
      (doseq [ctx (get @subs (:topic msg))]
        (.writeAndFlush ctx msg)))))

(defn handle-pingreq
  [^ChannelHandlerContext ctx]
  (.writeAndFlush ctx {:type :pingresp}))

(defn handle-disconnect
  [^ChannelHandlerContext ctx]
  (.close ctx))

;;
;; Netty glue
;;

(defn make-channel-handler [{:keys [subs connections channel]}]
  (proxy [ChannelHandlerAdapter] []
    (channelRead [ctx msg]
      (case (:type msg)
        :connect    (handle-connect ctx msg connections)
        :subscribe  (handle-subscribe ctx msg subs)
        :publish    (handle-publish ctx msg connections channel)
        :pingreq    (handle-pingreq ctx)
        :disconnect (handle-disconnect ctx)))
    (exceptionCaught [ctx cause]
      (try (throw cause)
           (finally (.close ctx))))))

(deftype NettyMqttHandler [config]
  Lifecycle
  (init [_ system] system)
  (start [_ system]
    (let [ch (some (comp :channel system) (:jig/dependencies config))]
      (assoc-in system
                [(:jig/id config) :jig.netty/handler-factory]
                #(make-channel-handler {:subs (ref {})
                                        :connections (ref {})
                                        :channel ch}))))
  (stop [_ system] system))
