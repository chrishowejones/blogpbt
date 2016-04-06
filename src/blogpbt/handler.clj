(ns blogpbt.handler
  (:require [compojure
             [core :refer :all]
             [route :as route]]
            [ring.middleware
             [defaults :refer [api-defaults wrap-defaults]]
             [json :refer [wrap-json-params wrap-json-response]]]
            [ring.util.response :as resp]
            [taoensso.timbre :as timbre :refer [debug]]
            [taoensso.timbre.appenders.core :as core-appenders]))

(def timbre-config
  {:level     :info
   :appenders
   {:println (core-appenders/println-appender {:stream :auto})}})

(timbre/set-config! timbre-config)

(def datastore (atom {:customers {}}))

(def not-found
  (resp/status (resp/response "Not found") 404))

(defn- store-customer
  [customer]
  (let [uuid (str (java.util.UUID/randomUUID))
        cust-with-id (assoc customer :id uuid)]
    (swap! datastore assoc-in [:customers uuid] cust-with-id)
    cust-with-id))

(defn- get-customer
  [id]
  (let [customer-found (get-in @datastore [:customers id])]
         (if customer-found
           (resp/content-type (resp/response customer-found) "application/json")
           not-found)))

(defn- delete-customer
  [id]
  (let [customer (get-in @datastore [:customers id])]
    (if customer
      (do
        (swap! datastore assoc-in [:customers id] nil)
        (resp/status (resp/response nil) 204))
      not-found)))

(defroutes app-routes
  (GET "/customers/:id" [id]
       (debug "Get customer for id:" id)
       (get-customer id))
  (POST "/customers" [customer]
        (let [stored-customer (store-customer customer)]
          (debug "Customer posted for id:" (:id stored-customer))
          (-> (resp/created (str "/customers/" (:id stored-customer)) stored-customer)
              (resp/content-type  "application/json"))))
  (DELETE "/customers/:id" [id]
          (debug "Delete customer for id:" id)
          (delete-customer id))
  (route/not-found "Not Found"))

(def app
  (-> (wrap-defaults app-routes api-defaults)
      wrap-json-params
      wrap-json-response))

(comment

  (-> (resp/created "/customers" (store-customer {:name "jim"}))
      (resp/content-type "application/json"))
  (swap! datastore assoc-in [:customers "1"] {:name "fred" :age 30})

  (app (ring.mock.request/request :get "/customers/1"))
  (app (ring.mock.request/content-type (ring.mock.request/request :post "/customers" (cheshire.core/generate-string {:customer {:name "bob"}})) "application/json"))




  )
