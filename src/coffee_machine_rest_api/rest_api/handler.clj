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
      :handler (fn [{{{:keys [coffee-id money]} :body} :parameters :as request}]
                 (let [event-ret @(events/dispatch-event
                                   emitter
                                   [::brew-coffee coffee-id money]
                                   {:enforce-handler true})]
                   (if (instance? Exception event-ret)
                     (throw event-ret)
                     {:body event-ret})))}}]])

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
                   {clojure.lang.ExceptionInfo
                    (fn [ex request]
                      {:status 500
                       :body (ex-data ex)})
                    ::exception/wrap (fn [handler e request]
                                       (.printStackTrace e)
                                       (handler e request))}))
                 muuntaja/format-request-middleware
                 coercion/coerce-request-middleware
                 multipart/multipart-middleware]}})

(defn get-handler [deps]
  (let [routes (get-routes deps)]
    (ring/ring-handler
     (ring/router routes options))))

