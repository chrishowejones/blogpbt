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

(defn extract-location-id
  [response]
  (second (re-find #"customers/([0-9|-[a-f]]+)" (get-in response [:headers "Location"]))))
