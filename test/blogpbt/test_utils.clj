(ns blogpbt.test-utils
  (:require [blogpbt
             [handler :refer :all]]
            [cheshire.core :refer [generate-string parse-string]]
            [ring.mock.request :as mock]))

(defn- parse-json-body
  [response]
  (let [body (:body response)]
    (if (and (not= 404 (:status response))
            body
            (not (empty? body)))
      (assoc response :body (parse-string body true))
      response)))

(defn post-resource-json [url resource]
  (let [request (mock/content-type (mock/request :post url (generate-string resource)) "application/json")
        response (app request)]
    (parse-json-body response)))

(defn get-resource-json [url]
  (-> (mock/request :get url)
      (assoc-in [:headers "Accept"] "application/json")
      app
      (parse-json-body)))

(defn- parse-location-id
  [re response]
  (second (re-find re (get-in response [:headers "Location"]))))

(defn extract-customer-location-id
  [response]
  (parse-location-id #"customers/([0-9|-[a-f]]+)" response))

(defn extract-address-location-id
  [response]
  (parse-location-id #"customers/[0-9|-[a-f]]+/addresses/([0-9|-[a-f]]+)" response))

(defn delete-resource-json [url id]
  (app (mock/request :delete (str url id))))

(comment

  (delete-resource-json "/customers" 123)
  (extract-customer-location-id (post-resource-json "/customers" {:name "fred"}))

  )
