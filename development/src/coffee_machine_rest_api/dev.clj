(ns coffee-machine-rest-api.dev
  (:require [coffee-machine-rest-api.system :as sys]

            [integrant.core :as ig]
            [integrant.repl :refer [clear go halt init prep reset reset-all]]
            [ring.mock.request :as ring-mock]))

(integrant.repl/set-prep! (constantly sys/config))

(comment
  ;; let's try out
  (let [handler (::sys/handler integrant.repl.state/system)]
    (-> (ring-mock/request :post "/api/brew-coffee")
        (ring-mock/json-body {:coffee-id "olá de volta"})
        (ring-mock/header "Accept" "application/json")
        (->> (def mock-request) deref)
        handler
        (->> (def resp) deref))))

