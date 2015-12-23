(ns cochlea.past-test
  (:require [clojure.test :refer :all]
            [cochlea.past :refer :all]
            [me.raynes.conch :as conch]
            ))
;what to understand about clojure tests so that these can be verified?
(deftest pg-list-test
  (testing "pg-list"
    (is (= (pg-list [1 2 3] true) "'1', '2', '3'"))
    (is (= (pg-list [1 2 3] false) "1, 2, 3"))))

(def running-expectation true)
(deftest check-pg-running
  (testing "pg-running?"
    (is (= running-expectation (pg-running?)))))

(def existing-expectation true)
(deftest check-valid-db
  (testing "valid-db?"
    (conch/with-programs [psql]
        (is (= existing-expectation (valid-db? (psql "-l")))))))

(deftest check-program-passes
  (testing "program-passes?"
    (conch/with-programs [df]
      (is (= true (program-passes? df)))
      (is (= false (program-passes? df "-alskdjr"))))))
