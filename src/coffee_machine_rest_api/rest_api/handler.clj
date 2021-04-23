(ns coffee-machine-rest-api.rest-api.handler
  (:require [coffee-machine-rest-api.events :as events]

            [ring.middleware.params :as params]
            [malli.util :as mu]
            [muuntaja.core :as m]
            reitit.coercion.malli
            [reitit.dev.pretty :as pretty]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as coercion]
            reitit.ring.malli
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.multipart :as multipart]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]))

(defn- get-routes [{:keys [emitter] :as deps}]
  ["/api"
   ["/brew-coffee"
    {:post
     {:parameters {:body [:map [:coffee-id keyword?] [:money double?]]}
      :responses {200 {:body
                       [:map
                        [:coffee-instance
                         [:map [:name string?] [:price double?] [:created-at string?]]]
                        [:change [:map-of double? int?]]
                        [:change-value double?]]}}
      :handler (fn [{{{:keys [coffee-id money]} :body} :parameters :as request}]
                 @(events/dispatch-event
                   emitter
                   [::brew-coffee coffee-id money]
                   {:enforce-handler true}))}}]])

(def ^:private options
  {:data
   {:coercion
    (reitit.coercion.malli/create
     {:error-keys
      #{:type :coercion :in :schema :value :errors :humanized :transformed}
      :compile mu/open-schema      
      :strip-extra-keys false
      :default-values true
      :options nil})
    
    :muuntaja m/instance
    :middleware [parameters/parameters-middleware
                 muuntaja/format-negotiate-middleware
                 muuntaja/format-response-middleware
                 (exception/create-exception-middleware
                  (merge
                   exception/default-handlers
                   {::exception/wrap (fn [handler e request]
                                       (.printStackTrace e)
                                       (handler e request))}))
                 muuntaja/format-request-middleware
                 coercion/coerce-request-middleware
                 multipart/multipart-middleware]}})

(defn get-handler [deps]
  (let [routes (get-routes deps)]
    (ring/router routes options)))

