(ns blogpbt.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.json :refer [wrap-json-params wrap-json-response wrap-json-body]]
            [ring.util.response :as resp]))

(def datastore (atom {:customers {}}))

(defn- store-customer
  [customer]
  (let [uuid (str (java.util.UUID/randomUUID))
        cust-with-id (assoc customer :id uuid)]
    (swap! datastore assoc-in [:customers uuid] cust-with-id)
    cust-with-id))

(defroutes app-routes
  (GET "/customers/:id" [id]
       (let [customer-found (get-in @datastore [:customers id])]
         (if customer-found
           (resp/content-type (resp/response customer-found) "application/json")
           (resp/status (resp/response "Not found") 404))))
  (POST "/customers" [customer]
        (let [stored-customer (store-customer customer)]
          (-> (resp/created (str "/customers/" (:id stored-customer)) stored-customer)
              (resp/content-type  "application/json"))))
  (route/not-found "Not Found"))

(def app
  (-> (handler/api app-routes)
      wrap-json-params
      wrap-json-response))

(comment

  (-> (resp/created "/customers" (store-customer {:name "jim"}))
      (resp/content-type "application/json"))
  (swap! datastore assoc-in [:customers "1"] {:name "fred"})

  (app (ring.mock.request/request :get "/customers/1"))
  (app (ring.mock.request/content-type (ring.mock.request/request :post "/customers" (cheshire.core/generate-string {:customer {:name "bob"}})) "application/json"))




  )
