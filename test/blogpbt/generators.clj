(ns blogpbt.generators
  (:require [clojure.string :as str]
            [clojure.test.check.generators :as gen]))

;; Generator for email
(def domain (gen/elements ["gmail.com" "hotmail.com" "googlemail.com" "yahoo.com" "microsoft.com" "zoho.com"]))

(def email-gen (gen/frequency
                [[90 (gen/fmap (fn [[name domain]] (str name "@" domain))
                               (gen/tuple (gen/not-empty gen/string-alphanumeric) domain))]
                 [5 (gen/return "")]
                 [5 (gen/return nil)]]))

(def concat-uppercase
  (fn [[a b]] (str/upper-case (str a b))))

(def postcode-alpha-part (gen/fmap concat-uppercase (gen/tuple gen/char-alpha gen/char-alpha)))

(def postcode-alpha-part2 (gen/fmap concat-uppercase (gen/tuple gen/char-alpha (gen/frequency [[7 gen/char-alpha] [3 (gen/return "")]]))))

;; Generator for postcode
(def postcode-gen (gen/fmap (fn [[fst-part-alpha fst-part-int snd-part-int snd-part-alpha]]
                              (str fst-part-alpha fst-part-int " " snd-part-int snd-part-alpha))
                            (gen/tuple postcode-alpha-part (gen/choose 1 99) (gen/choose 1 99) postcode-alpha-part2)))

;; Generator for customer resource
(def customer
  (gen/hash-map :name gen/string :email email-gen :age (gen/choose 10 100)))

;; Generators for addresses
(defn single-line-address [customer-id]
  (gen/hash-map :customer-id (gen/return customer-id)
                :number gen/pos-int
                :line1 gen/string-alphanumeric
                :postcode postcode-gen))

(defn two-line-address [customer-id]
  (gen/hash-map :customer-id (gen/return customer-id)
                :number gen/pos-int
                :line1 gen/string-alphanumeric
                :line2 gen/string-alphanumeric
                :postcode postcode-gen))

(defn address [customer-id]
  (gen/frequency [[7 (single-line-address customer-id)] [3 (two-line-address customer-id)]]))
