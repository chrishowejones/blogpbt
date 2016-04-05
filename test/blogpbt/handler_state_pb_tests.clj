(ns blogpbt.handler-state-pb-tests
  "Stateful property based tests"
  (:require [blogpbt.generators :refer [customer]]
            [clojure.test :refer [deftest is]]
            [clojure.test.check.generators :as gen]
            [blogpbt.test-utils :refer [extract-location-id post-resource-json get-resource-json]]
            [stateful-check.core :refer [specification-correct?]]))

(defn new-queue []
  (atom clojure.lang.PersistentQueue/EMPTY))
(defn push-queue [queue value]
  (swap! queue conj value))
(defn pop-queue [queue]
  (let [value (peek @queue)]
    (swap! queue pop)
    value))


(def push-queue-specification
  {:model/args (fn [state]
                 [(:queue state) gen/nat])
   :real/command #'push-queue
   :next-state (fn [state [_ val] _]
                 (update-in state [:elements] conj val))})

(def pop-queue-specification
  {:model/requires (fn [state]
                     (seq (:elements state)))
   :model/args (fn [state]
                 [(:queue state)])
   :real/command #'pop-queue
   :next-state (fn [state _ _]
                 (update-in state [:elements] (comp vec next)))
   :real/postcondition (fn [prev-state _ _ val]
                         (= (-> prev-state :elements first)
                            val))})

(def queue-spec
  {:commands {:push #'push-queue-specification
              :pop #'pop-queue-specification}
   :real/setup #'new-queue
   :initial-state (fn [queue] {:queue queue, :elements []})})

(deftest test-queue-specification
  (is (specification-correct? queue-spec {:num-tests 100})))

;; stateful tests of customers api

;; model customer data in map - initial state is map of the model of customers

(def post-customer-specification
  {:model/args (fn [state]
                 [(gen/return "/customers") (gen/fmap (fn [cust] {:customer cust}) customer)])
   :real/command #'post-resource-json
   :next-state (fn [state _ response]
                 (let [new-state (update-in state [:customers] conj (:body response))]
                   (update-in new-state [:customer-ids] conj (:id (:body response)))))
   :real/postcondition (fn [_ _ args response]
                         (and
                          (= 201 (:status response))
                          (not (nil? (extract-location-id response)))
                          (= (:customer (second args))
                             (dissoc (:body response) :id))))})

(defn get-customer
  [id]
  (get-resource-json (str "/customers/" id)))

(def get-customer-specification
  {:model/args (fn [state]
                 (if (not-empty (:customers state))
                   [(gen/elements (:customer-ids state))]
                   [gen/int]))
   :real/command #'get-customer
   :real/postcondition (fn [prev-state _ args response]
                         (let [id (:id (:body response))]
                            (if (some #{id} (:customer-ids prev-state))
                             (= 200 (:status response))
                             (= 404 (:status response)))))})

(def customer-resource-specification
  {:commands {:post #'post-customer-specification
              :get  #'get-customer-specification}
   :initial-state (constantly {:customers [] :customer-ids []})})

(deftest check-customer-resource-specification
  (is (specification-correct? customer-resource-specification {:num-tests 10})))


(comment

  (clojure.test/run-tests 'blogpbt.handler-state-pb-tests)
  (:body (post-resource-json "/customers" {:customer {:name "Ã" :email nil, :age 94}}))

  (defn test-update [state _ response]
    (let [new-state (update-in state [:customers] conj (:body response))]
      (if-let [cust-id (extract-location-id response)]
        (update-in new-state [:customer-ids] conj cust-id)
        new-state)))

  (test-update {:customers [] :customer-ids []} nil (post-resource-json "/customers" {:customer {:name "Chris"}}))

  )
