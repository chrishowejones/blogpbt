(ns blogpbt.handler-state-pb-tests
  "Stateful property based tests"
  (:require [blogpbt
             [generators :refer [customer]]
             [test-utils :refer [delete-resource-json extract-location-id get-resource-json post-resource-json]]]
            [clojure.test :refer [deftest is]]
            [clojure.test.check.generators :as gen]
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

;; helper functions to call post and get with a single argument
(defn- get-customer
  [id]
  (get-resource-json (str "/customers/" id)))

(defn- post-customer
  [customer]
  (post-resource-json (str "/customers") customer))

(defn- delete-customer
  [id]
  (delete-resource-json "/customers/" id))

;; model server side customer state data in a map
;; The map will contain a vector of generated customer maps and a vector of customer ids
;; returned from calls to POST on the server.
;; i.e.
;; {:customers [{:name "Jane" :email "Jane@yahoo.com" :age 44} {:name "Fred" :email "fred@gmail.com" :age 31}]
;;  :customer-ids ["f64dcd17-e5d8-4219-b101-eba94f1ddaff" "d26c9ed4-dbf3-4525-9946-aaa794d1fe6e"]

(def post-customer-specification
  {:model/args (fn [_]
                 [(gen/fmap (fn [cust] {:customer cust}) customer)])
   :real/command #'post-customer
   :next-state (fn [state _ {:keys [body]}]
                 (let [id (:id body)]
                   (-> state
                       (update-in [:customers] assoc id body)
                       (update-in [:all-customer-ids] conj id))))
   :real/postcondition (fn [_ _ args {:keys [status body] :as response}]
                         (and
                          (= 201 status)
                          (not (nil? (extract-location-id response)))
                          (= (:customer (first args))
                             (dissoc body :id))))})

(def get-customer-specification
  {:model/args (fn [state]
                 (if (seq (:all-customer-ids state))
                   [(gen/frequency [[9 (gen/elements (:all-customer-ids state))]
                                    [1 gen/int]])]
                   [gen/int]))
   :real/command #'get-customer
   :real/postcondition (fn [{:keys [customers]} _ args {:keys [status body]}]
                         (let [id (first args)]
                           (if (customers id)
                             (and
                              (= 200 status)
                              (= body (customers id)))
                             (= 404 status))))})

(def delete-customer-specification
  {:model/args (fn [state]
                 (if (seq (:all-customer-ids state))
                   [(gen/frequency
                     [[9 (gen/elements (:all-customer-ids state))]
                      [1 gen/int]])]
                   [gen/int]))
   :real/command #'delete-customer
   :next-state (fn [state args _]
                 (let [id (first args)]
                   (update-in state [:customers] dissoc id)))
   :real/postcondition (fn [{:keys [customers]} _ args {:keys [status]}]
                         (let [id (first args)]
                           (if (customers id)
                             (= 204 status)
                             (= 404 status))))})

(def customer-resource-specification
  {:commands {:post #'post-customer-specification
              :get  #'get-customer-specification
              :delete #'delete-customer-specification}
   :initial-state (constantly {:customers {} :all-customer-ids []})})

(deftest check-customer-resource-specification
  (is (specification-correct? customer-resource-specification {:num-tests 300})))


(comment

  (clojure.test/run-tests 'blogpbt.handler-state-pb-tests)
  (:body (post-resource-json "/customers" {:customer {:name "Ã" :email nil, :age 94}}))

  (defn test-update [state _ response]
    (let [new-state (update-in state [:customers] conj (:body response))]
      (if-let [cust-id (extract-location-id response)]
        (update-in new-state [:customer-ids] conj cust-id)
        new-state)))

  (test-update {:customers [] :customer-ids []} nil (post-resource-json "/customers" {:customer {:name "Chris"}}))
  (post-customer {:customer {:name "Chris"}})
  (delete-customer "547ae821-85df-4a53-bd0e-805b63ab93f8")
  (def test-state {:customer-ids ["1" "2" "3"]})
  (assoc test-state :customer-ids (filter #(not= % "2") (:customer-ids test-state)))

  )
