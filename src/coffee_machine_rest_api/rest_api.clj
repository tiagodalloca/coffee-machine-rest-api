(ns coffee-machine-rest-api.rest-api
  (:require [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]))

(defn app [handler]
  (ring/ring-handler handler))

(defn start-server [{:keys [handler port] :as deps}]
  (let [app-instance (app handler)]
    (jetty/run-jetty app-instance {:port port :join? false})))

