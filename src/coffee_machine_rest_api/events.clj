(ns coffee-machine-rest-api.events
  (:require [clojure.core.async :as async])
  (:import [java.util.concurrent Executors]))

(defn create-thread-pool [pool-size]
  (Executors/newFixedThreadPool pool-size))

(defn execute-async
  ([pool f args]
   (.submit pool (fn [] (apply f args)))
   true))

(defn- handle-dispach-async [{:keys [observers
                                     handlers
                                     pool] :as emitter}
                             {:keys [event-t
                                     args
                                     handler-promise
                                     enforce-handler]}]
  (let [event-obs (get @observers event-t)
        event-handler (get @handlers event-t)
        vargs (vector args)
        exceptions-wrapper (fn [f ex-handler]
                             (fn [& args]
                               (try
                                 (apply f args)
                                 (catch Exception e
                                   (ex-handler e)))))]
    (if event-handler
      (execute-async
       pool
       (exceptions-wrapper event-handler (fn [ex] (deliver handler-promise ex)))
       (conj vargs handler-promise))
      (deliver handler-promise
               (if enforce-handler
                 (ex-info
                  (str "No handler found for event " event-t)
                  {:cause ::no-handler-found
                   :event-t event-t
                   :args args})
                 nil)))
    (when event-obs
      (doseq [[id f] event-obs]
        (execute-async
         pool
         (exceptions-wrapper f identity)
         vargs)))))

(defn start-listening [{:keys [observers chan exit-chan running?] :as emitter}]
  (async/go-loop []
    (async/alt!
      chan ([event-map]
            (handle-dispach-async emitter event-map)
            (recur))
      exit-chan (reset! running? false)))
  (reset! (:running? emitter) true))

(defn create-emitter
  ([{:keys [pool pool-size chan-buf-size chan-buf immediately-start?] :as opts}]
   (let [pool-size (or pool-size 8)
         pool (or pool (Executors/newFixedThreadPool pool-size))
         chan (async/chan (or chan-buf chan-buf-size 8))
         emitter {:observers (atom {})
                  :handlers (atom {})
                  :chan chan
                  :exit-chan (async/chan)
                  :pool pool
                  :running? (atom false)}]
     (when immediately-start?
       (start-listening emitter))
     emitter))
  ([] (create-emitter {})))

(defn stop-listening [{:keys [exit-chan] :as emitter}]
  (async/put! exit-chan true))

(defn add-observer [emitter event-t observer-id observer]
  (letfn [(add-observer-to-event-t [m]
            (if m
              (assoc m observer-id observer)
              (array-map observer-id observer)))
          (add-observer-to-observers [observers]
            (update observers event-t add-observer-to-event-t))]
    (update emitter :observers
            (fn [obs-atom] (swap! obs-atom add-observer-to-observers)))))

(defn remove-observer [emitter event-t observer-id]
  (update
   emitter :observers
   (fn [obs-atom]
     (swap! obs-atom
            (fn [obs]
              (update obs event-t
                      (fn [m] (when m (dissoc m observer-id)))))))))

(defn add-handler [emitter event-t handler]
  (update emitter :handlers
          (fn [handlers-atom]
            (swap! handlers-atom
                   (fn [handlers-map]
                     (assoc handlers-map event-t handler))))))

(defn remove-handler [emitter event-t]
  (update emitter :handlers
          (fn [handlers-atom]
            (swap! handlers-atom
                   (fn [handlers-map]
                     (dissoc handlers-map event-t))))))

(defn dispatch-event
  ([{:keys [chan] :as emitter}
    [event-t & args :as event]
    {:keys [enforce-handler] :as opts}]
   (let [handler-promise (promise)]
     (async/put! chan {:event-t event-t
                       :args args
                       :handler-promise handler-promise
                       :enforce-handler enforce-handler})
     handler-promise))
  ([emitter event]
   (dispatch-event emitter event {})))

(comment
  (let [test-emitter (create-emitter {:pool-size 1
                                      :chan-buf-size 10
                                      :immediately-start? true})]
    (add-observer test-emitter :hi :println-obs println)
    (dispatch-event test-emitter :hi "ola")))

(comment
  (def test-emitter (create-emitter {:pool-size 10
                                     :chan-buf-size 10
                                     :immediately-start? false}))
  (start-listening test-emitter)
  (add-observer test-emitter :hi :println-obs println)

  (deref (dispatch-event test-emitter [:hi "hi"] {:enforce-handler true}))
  
  (add-handler
   test-emitter :hi
   (fn [[hi] handler-promise]
     (Thread/sleep 1000)
     (when handler-promise (deliver handler-promise "hello there!"))))

  (deref (dispatch-event test-emitter [:hi "hi"] {:enforce-handler true}))

  (time
   (dotimes [_ 100]
     (dispatch-event test-emitter :hi "hi")))

  (remove-observer test-emitter :hi :println-obs)
  (remove-handler test-emitter :hi)
  (stop-listening test-emitter)

  test-emitter)

