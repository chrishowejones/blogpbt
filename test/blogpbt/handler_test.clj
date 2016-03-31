(ns blogpbt.handler-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [blogpbt.handler :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [com.gfredericks.test.chuck.clojure-test :as chuck]
            [clojure.test.check.properties :as prop]
            [clojure.string :as str]
            [blogpbt.generators :refer [customer]]
            [ring.mock.request :as mock]
            [cheshire.core :refer [generate-string parse-string]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check :as tc]))

(defn- post-helper-json [url entity]
             (let [request (mock/content-type (mock/request :post url (generate-string entity)) "application/json")]
               (app request)))

(defn- get-helper-json [url]
  (-> (mock/request :get url)
      (assoc-in [:headers "Accept"] "application/json")
      app))

(defn- extract-location-id
  [response]
  (second (re-find #"customers/([0-9|-[a-f]]+)" (get-in response [:headers "Location"]))))

(deftest test-app
  (testing "customer post route"
    (let [response (post-helper-json "/customers" {:customer {:name "Fred"}})]
      (is (= (:status response) 201))
      (is (= (into {:id (extract-location-id response)} {:name "Fred"}) (parse-string (:body response) true)))))

  (testing "customer get route"
    (let [id (->
              (post-helper-json "/customers" {:customer {:name "Fred"}})
              (extract-location-id))
          response (get-helper-json (str "/customers/" id))]
      (is (= (:status response) 200))
      (is (= (parse-string (:body response) true) {:id id :name "Fred"}))))

  (testing "not-found route"
    (let [response (app (mock/request :get "/invalid"))]
      (is (= (:status response) 404)))))

(deftest test-customer
  (let [response (post-helper-json "/customers" {:customer {:name "", :email "p9@googlemail.com", :age 48}})]
    (is (= 201 (:status response)))
    (is (not (nil? (second (re-find #"customers/([0-9|-[a-f]]+)" (get-in response [:headers "Location"]))))))))

;; Property based tests

(defspec test-post-customer-status-created
  1000
  (prop/for-all [cust customer]
                (let [response (post-helper-json "/customers" {:customer cust})]
                  (= 201 (:status response))))) ;; status should be 'created'

(defspec test-post-customer-location-created
  1000
  (prop/for-all [cust customer]
                (let [response (post-helper-json "/customers" {:customer cust})
                      location-id (second (re-find #"customers/([0-9|-[a-f]]+)" (get-in response [:headers "Location"])))]
                  (and
                   (not (nil? location-id))
                   (= (-> (:body response)
                          (parse-string true)
                          :id)
                      location-id)))))



(defspec test-post-customer-already-created
  1000
  (prop/for-all [cust customer]
                (let [response (post-helper-json "/customers" {:customer cust})
                      id (extract-location-id response)]
                  (let [snd-response (post-helper-json "/customers" {:customer cust})]
                    (= 201 (:status snd-response))
                    (not= id (extract-location-id snd-response))) ; post is not idempotent
                  )))

(deftest test-get-customer-exists
  (chuck/checking "checking that customer exists" 1000
                  [cust customer]
                  (let [id (extract-location-id (post-helper-json "/customers" {:customer cust}))]
                    (is (= 200 (:status (get-helper-json (str "/customers/" id))))))))

(defspec test-get-customer-not-exists
  1000
  (prop/for-all [id gen/int]
                (let [response (get-helper-json (str "/customers/" id))]
                  (is (= 404 (:status response)) (str "Expected status 404 got " (:status response))))))


(comment

  (post-helper-json "/customers" {:customer {:name "Fred"}})
  (app (mock/content-type (mock/request :post "/customers" (generate-string {:customer {:name "Fred"}})) "application/json"))


  )
