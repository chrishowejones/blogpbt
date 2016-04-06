(ns blogpbt.handler-test
  (:require [blogpbt
             [generators :refer [customer]]
             [handler :refer :all]
             [test-utils :refer [delete-resource-json extract-address-location-id extract-customer-location-id get-resource-json post-resource-json]]]
            [clojure.test :refer :all]
            [clojure.test.check
             [clojure-test :refer [defspec]]
             [generators :as gen]
             [properties :as prop]]
            [com.gfredericks.test.chuck.clojure-test :as chuck]
            [ring.mock.request :as mock]))

(deftest test-app
  (testing "customer post route"
    (let [response (post-resource-json "/customers" {:customer {:name "Fred"}})]
      (is (= (:status response) 201))
      (is (= (into {:id (extract-customer-location-id response)} {:name "Fred"}) (:body response)))))

  (testing "customer get route"
    (let [id (->
              (post-resource-json "/customers" {:customer {:name "Fred"}})
              (extract-customer-location-id))
          response (get-resource-json (str "/customers/" id))]
      (is (= (:status response) 200))
      (is (= (:body response) {:id id :name "Fred"}))))

  (testing "customer delete route"
    (let [id (->
              (post-resource-json "/customers" {:customer {:name "Fred"}})
              (extract-customer-location-id))]
      (is (= (:status (delete-resource-json "/customers/" id)) 204))))

  (testing "not-found route"
    (let [response (app (mock/request :get "/invalid"))]
      (is (= (:status response) 404)))))

(deftest test-customer-address
  (testing "Add an address to a customer"
    (let [address {:number 10 :line1 "Downing St" :postcode "SW1A 2AA"}
          response (-> (post-resource-json "/customers" {:customer {:name "Fred"}})
                       extract-customer-location-id
                       (#(str "/customers/" %  "/addresses"))
                       (post-resource-json {:address address}))]
      (is (= (:status response) 201))
      (is (= (extract-address-location-id response)))
      (is (= (dissoc (:body response) :id) address)))))

(deftest test-customer
  (let [response (post-resource-json "/customers" {:customer {:name "", :email "p9@googlemail.com", :age 48}})]
    (is (= 201 (:status response)))
    (is (not (nil? (extract-location-id response))))))

;; Property based tests

(defspec test-post-customer-status-created
  1000
  (prop/for-all [cust customer]
                (let [response (post-resource-json "/customers" {:customer cust})]
                  (= 201 (:status response))))) ;; status should be 'created'

(defspec test-post-customer-location-created
  1000
  (prop/for-all [cust customer]
                (let [response (post-resource-json "/customers" {:customer cust})
                      location-id (extract-location-id response)]
                  (and
                   (not (nil? location-id))
                   (= (:id (:body response))
                      location-id)))))



(defspec test-post-customer-already-created
  1000
  (prop/for-all [cust customer]
                (let [response (post-resource-json "/customers" {:customer cust})
                      id (extract-location-id response)]
                  (let [snd-response (post-resource-json "/customers" {:customer cust})]
                    (and (= 201 (:status snd-response))
                         (not= id (extract-location-id snd-response)))) ; post is not idempotent
                  )))

(deftest test-get-customer-exists
  (chuck/checking "checking that customer exists" 1000
                  [cust customer]
                  (let [id (extract-location-id (post-resource-json "/customers" {:customer cust}))
                        customer-retrieved (get-resource-json (str "/customers/" id))]
                    (is (= 200 (:status customer-retrieved)))
                    (is (= cust (dissoc (:body customer-retrieved) :id))))))

(deftest test-get-customer-not-exists
  (chuck/checking "checking that customer doesn't exist"
                  1000
                  [id gen/int]
                  (let [response (get-resource-json (str "/customers/" id))]
                    (is (= 404 (:status response)) (str "Expected status 404 got " (:status response))))))


(comment

  (post-helper-json "/customers" {:customer {:name "Fred"}})
  (app (mock/content-type (mock/request :post "/customers" (generate-string {:customer {:name "Fred"}})) "application/json"))

  (-> (post-resource-json "/customers" {:customer {:name "Fred"}})
                       extract-location-id
                       (#(str "/customers/" %  "/addresses"))
                       (post-resource-json {:address {:number 10 :line1 "Downing St" :postcode "SW1A 2AA"}}))

  )
