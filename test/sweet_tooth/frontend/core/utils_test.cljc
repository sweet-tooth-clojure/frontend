(ns sweet-tooth.frontend.core.utils-test
  (:require [sweet-tooth.frontend.core.utils :as u]
            #?(:clj [clojure.test :as t :refer [is deftest]]
               :cljs [cljs.test :as t :include-macros true :refer [is deftest]])))

(deftest capitalize-words
  (is (= (u/capitalize-words "one two three")
         "One Two Three")))

(deftest kw-str
  (is (= (u/kw-str :topic-title)
         "Topic Title")))

(deftest strk
  (is (= (u/strk :topic "-title")
         :topic-title)))

(deftest kebab
  (is (= (u/kebab "Foo Bar")
         "Foo-Bar")))

(deftest toggle
  (is (= (u/toggle :foo :foo :bar)
         :bar))

  (is (= (u/toggle :foo :bar :foo)
         :bar)))

(deftest flatv
  (is (= (u/flatv :forms [:topic :post])
         [:forms :topic :post])))


(deftest slugify
  (is (= (u/slugify "a b c d e f g h i j k")
         "a-b-c-d-e-f-g-h-i-j-k"))
  (is (= (u/slugify "a b c d e f g h i j k" 3)
         "a-b-c")))

(deftest pluralize
  (is (= (u/pluralize "cat" 1)
         "cat"))
  (is (= (u/pluralize "cat" 2)
         "cats")))

(deftest id-num
  (is (= (u/id-num "123-abc")
         "123")))

(deftest set-toggle
  (is (= (u/set-toggle #{:a :b} :a)
         #{:b})
      (= (u/set-toggle #{:b} :a)
         #{:a :b})))

(deftest dissoc-in
  (is (= (u/dissoc-in {:a {:b {:c :d}}} [:a :b :c])
         {:a {:b {}}})))
