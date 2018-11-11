(ns demo.sync.dispatch.local.data
  (:require [clojure.spec.alpha :as s]
            [clojure.test.check.generators :as gen :include-macros true]
            [reifyhealth.specmonstah.core :as rs]
            [reifyhealth.specmonstah.spec-gen :as rsg]
            [loom.attr :as lat]
            [clojure.spec.alpha :as s])
  (:import [goog.date Date]))

;;------
;; specs
;;------

(s/def :db/id pos-int?)
(s/def :db/ref :db/id)
(s/def :common/not-empty-string (s/and string? not-empty))
(s/def :common/keyword (s/with-gen keyword?
                         #(gen/fmap (fn [x] (keyword x))
                                    (gen/string-alphanumeric))))

(defn ^:private is-date? [x]
  (instance? Date x))

(s/def :common/date
  (s/with-gen is-date? #(gen/let [] (Date.))))

(s/def :meta/owner :db/ref)
(s/def :meta/created-at :common/date)

(s/def :user/email    (s/and string? not-empty #(let [c (count %)] (> 20 c 3))))
(s/def :user/username (s/and string? not-empty #(let [c (count %)] (> 20 c 3))))
(s/def :user/user
  (s/keys :req [:db/id
                :user/username
                :user/email]))

(s/def :topic/count nat-int?)
(s/def :topic-category/name #{"topic category"})
(s/def :topic-category/topic-category
  (s/keys :req [:db/id :topic-category/name :topic/count]))

(s/def :topic/post-count nat-int?)
(s/def :topic/title #{"topic title"})
(s/def :topic/topic (s/keys :req [:db/id :topic/title :topic/post-count :meta/created-at]))

(s/def :post/content #{"post content yay"})
(s/def :post/post (s/keys :req [:db/id :post/content :meta/created-at]))


(s/def :watch/watched :db/ref)
(s/def :watch/scope #{:new-topic :new-post})
(s/def :watch/level #{:watch :ignore})
(s/def :watch/watch
  (s/keys :req [:db/id
                :watch/watched
                :watch/scope
                :watch/level
                :meta/owner]))

(s/def :notification/watch :db/ref)
(s/def :notification/summary-seen boolean?)
(s/def :notification/entities (s/coll-of :db/ref))
(s/def :notification/pushed-entities (s/coll-of :db/ref))
(s/def :notification/visited-at :common/date)
(s/def :notification/notified-at :common/date)
(s/def :notification/notification
  (s/keys :req [:db/id
                :notification/summary-seen
                :notification/entities]
          :opt [:notification/visited-at
                :notification/notified-at]))


;;------
;; specmonstah
;;------
(def schema
  {:topic-category {:spec   :topic-category/topic-category
                    :prefix :tc}
   :topic          {:spec      :topic/topic
                    :relations {:topic/parent     [:topic-category :db/id]
                                :topic/first-post [:post :db/id]
                                :meta/owner       [:user :db/id]}
                    :prefix    :t}
   :post           {:spec      :post/post
                    :relations {:post/topic [:topic :db/id]
                                :meta/owner [:user :db/id]}
                    :prefix    :p}
   :user           {:spec     :user/user
                    :prefix   :u}
   :watch          {:spec      :watch/watch
                    :relations {:watch/watched #{[:topic :db/id]
                                                 [:topic-category :db/id]}
                                :meta/owner    [:user :db/id]}
                    :ref-types {:watch/watched :topic}
                    :prefix    :w}
   :notification   {:spec        :notification/notification
                    :relations   {:notification/watch           [:watch :db/id]
                                  :notification/entities        #{[:topic :db/id]
                                                                  [:post :db/id]}
                                  :notification/pushed-entities [:topic :db/id]
                                  :meta/owner                   [:user :db/id]}
                    :ref-types   {:notification/entities :topic}
                    :constraints {:notification/entities        #{:coll}
                                  :notification/pushed-entities #{:coll}
                                  :notification/watch           #{:uniq}}
                    :prefix      :n}})

(def db (atom (rs/build-ent-db {:schema schema} {})))

(defn gen1
  [spec]
  (gen/generate (s/gen spec)))

(defn spec-gen-map
  [db]
  (rs/attr-map db rsg/spec-gen-ent-attr-key))

(defn populate
  [query]
  (swap! db #(rsg/ent-db-spec-gen % query)))

(defn ent-gen
  [type]
  (let [db @db]
    {type (->> (rs/ents-by-type db)
               type
               (mapv (fn [ent] (:spec-gen (rs/ent-attrs db ent))))
               (reduce (fn [m ent]
                         (assoc m (:db/id ent) ent))
                       {}))}))
