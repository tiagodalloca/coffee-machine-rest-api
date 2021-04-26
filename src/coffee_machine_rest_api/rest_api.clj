(ns coffee-machine-rest-api.rest-api
  (:require [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]))

(defn start-server [{:keys [handler port] :as deps}]
  (jetty/run-jetty handler {:port port :join? false}))

