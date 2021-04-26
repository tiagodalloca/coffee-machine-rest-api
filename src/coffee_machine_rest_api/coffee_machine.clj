(ns coffee-machine-rest-api.coffee-machine)

(defn create-coffee [name price]
  {:name name
   :price price})

(defn coffee-id-from-name [name]
  (let [normalized-name (java.text.Normalizer/normalize
                         name java.text.Normalizer$Form/NFD)]
    (-> #"[a-zA-Z0-9]+"
        (re-seq normalized-name)
        (->> (clojure.string/join "-"))
        .toLowerCase
        keyword)))

(defn create-coffee-machine [{:keys [coffees available-coins] :as opts}]
  (let [coffees-map (reduce
                     (fn [m [name price]]
                       (assoc m
                              (coffee-id-from-name name)
                              (create-coffee name price)))
                     {} coffees)]
    {:coffees coffees-map
     :available-coins (into (sorted-set-by >) available-coins)}))

(comment
  (create-coffee-machine
   {:coffees (into [] {"Affogato" 1.00
                       "Caffè Latte" 1.50
                       "Caffè Mocha" 2.00})
    :available-coins [0.50 1.00 0.10 0.25]}))

(defn- round-decimal [number decimal-precision]
  (let [base-10-rounding-factor (Math/pow 10 decimal-precision)]
    (/ (Math/round (* number base-10-rounding-factor))
       base-10-rounding-factor)))

(comment
  (round-decimal 6.05 1) ;; => 6.1
  )

(defn- calc-coins-exchange
  ([expected-change available-coins]
   (calc-coins-exchange expected-change (into (sorted-set-by >) available-coins) {}))
  ([expected-change available-coins current-change]
   (let [expected-change (round-decimal expected-change 2)]
     (if (or (empty? available-coins) (-> expected-change (<= 0)))
       current-change
       (let [[coin & coins] available-coins
             coin-ammount  (-> expected-change (/ coin) int)]
         (recur (-> expected-change (- (-> coin (* coin-ammount))))
                coins
                (assoc current-change coin coin-ammount)))))))

(comment
  (calc-coins-exchange
   2.42
   [0.01 1.00 0.10]))

(defn create-coffee-instance [coffee]
  (merge coffee
         {:created-at (str (java.time.Instant/now))}))

(defn request-coffee [coffee-machine coffee-id money]
  (let [{:keys [coffees available-coins]} coffee-machine
        coffee (get coffees coffee-id)
        coffee-name (or (:name coffee) ::missing-coffee-name)
        coffee-price (or (:price coffee) 0)
        expected-change (some-> money (- coffee-price) (round-decimal 2))
        change (some-> expected-change (calc-coins-exchange available-coins))
        change-value (some->
                      (some->> change
                               (reduce-kv
                                (fn [value coin amount]
                                  (-> value (+ (-> coin (* amount))))) 0))
                      (round-decimal 2))]

    (cond (nil? coffee)
          (-> (ex-info "Unavailable coffee."
                       {:cause ::unavailable-coffee
                        :coffee-id coffee-id
                        :available-coffees coffees})
              throw)
          
          (or (nil? money) (-> expected-change (< 0)))
          (-> (ex-info "Insufficient money."
                       {:cause ::insufficient-money
                        :coffee coffee
                        :money money})
              throw)

          (-> change-value (< expected-change))
          (-> (ex-info "Insufficient change."
                       {:cause ::insufficient-change
                        :coffee coffee
                        :available-coins available-coins
                        :money money
                        :change change
                        :change-value change-value
                        :expected-change expected-change})
              throw)

          :default
          {:coffee-instance (create-coffee-instance coffee)
           :change change
           :change-value change-value})))

(comment
  (let [coffee-machine (create-coffee-machine
                        {:coffees {"Affogato" 1.00
                                   "Caffè Latte" 1.50
                                   "Caffè Mocha" 2.00}
                         :available-coins [0.50 1.00 0.10 0.25]})]
    (request-coffee coffee-machine :caffe-latte 2.10)))

