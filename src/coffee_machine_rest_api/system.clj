(ns coffee-machine-rest-api.system
  (:require [coffee-machine-rest-api.coffee-machine :as coffee-machine]
            [coffee-machine-rest-api.events :as events]
            [coffee-machine-rest-api.rest-api :as api]
            [coffee-machine-rest-api.rest-api.handler :as api-handler]

            [integrant.core :as ig]))

(def config
  {::emitter  {:opts {:pool-size 4
                      :chan-buf-size 10
                      :immediately-start? true}}

   ::coffee-machine {:opts {:coffees {"Affogato" 1.00
                                      "Caffè Latte" 1.50
                                      "Caffè Mocha" 2.00}
                            :available-coins [0.50 1.00 0.10 0.25]}}
   
   ::server {:opts {:port 6942}
             :handler (ig/ref ::router)}

   ::handler {:emitter (ig/ref ::emitter)}

   ::api-events-handlers
   {:emitter (ig/ref ::emitter)
    :coffee-machine-instance (ig/ref ::coffee-machine)
    :opts
    {::api-handler/brew-coffee
     (fn [{:keys [coffee-machine-instance]}]
       (fn [[coffee-id money] p]
         (->> (coffee-machine/request-coffee coffee-machine-instance coffee-id money)
              (deliver p))))}}})

(defmethod ig/init-key ::emitter [_ {:keys [opts]}]
  (events/create-emitter opts))

(defmethod ig/halt-key! ::emitter [_ emitter]
  (when emitter
    (events/stop-listening emitter)))

(defmethod ig/init-key ::coffee-machine [_ {:keys [opts]}]
  (coffee-machine/create-coffee-machine opts))

(defmethod ig/init-key ::coffee-machine [_ {:keys [opts]}]
  (coffee-machine/create-coffee-machine opts))

(defmethod ig/init-key ::server [_ {:keys [handler] {:keys [port]} :opts}]
  (api/start-server {:handler handler :port port}))

(defmethod ig/halt-key! ::server [_ server]
  (when server (.stop server)))

(defmethod ig/init-key ::handler [_ {:keys [emitter]}]
  (api-handler/get-handler {:emitter emitter}))

(defmethod ig/init-key ::api-events-handlers [_ {:keys [emitter
                                                        coffee-machine-instance
                                                        opts] :as args}]
  (let [handlers-deps {:coffee-machine-instance coffee-machine-instance}]
    (doseq [[event-t f] opts]
      (events/add-handler emitter event-t (f handlers-deps))))
  args)

(defmethod ig/halt-key! ::api-events-handlers [_ {:keys [emitter opts] :as args}]
  (doseq [[event-t _] opts]
    (events/remove-handler emitter event-t))
  args)

