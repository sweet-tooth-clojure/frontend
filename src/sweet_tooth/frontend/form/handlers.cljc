(ns sweet-tooth.frontend.form.handlers
  (:require [re-frame.core :refer [reg-event-db reg-event-fx trim-v]]
            [ajax.core :refer [GET PUT POST DELETE]]
            [sweet-tooth.frontend.core.handlers :as c]
            [sweet-tooth.frontend.core.utils :as u]
            [sweet-tooth.frontend.remote.handlers :as strh]))

;; TODO spec set of possible actions
;; TODO spec out form map, keys :data :state :ui-state etc

(defmulti url-prefix
  "Customize url prefix, e.g. \"/api/v1\""
  (fn [endpoint action] [endpoint action]))

(defmulti data-id
  "URL fragment to use when doing PUT and DELETE requests"
  (fn [endpoint action data] [endpoint action]))

(defn method-url
  [endpoint action data]
  (let [multi (str (url-prefix endpoint action) "/" (name endpoint))
        single (str multi "/" (data-id endpoint action data))]
    (case action
      :create [POST multi]
      :update [PUT single]
      :query  [GET multi]
      :get    [GET single]
      :delete [DELETE single])))

(defn submit-form [form-path data token
                   {:keys [success error]
                    :or {success ::submit-form-success
                         error ::submit-form-error}
                    :as form-spec}]
  (let [[_ endpoint action] form-path
        [method url] (method-url endpoint action data)]
    {:method method
     :url url
     :data data
     :on-success [success form-path form-spec]
     :on-fail [error form-path form-spec]
     :token token}))

(reg-event-fx ::submit-form
  [trim-v]
  (fn [{:keys [db]} [form-path & [form-spec]]]
    {:db (-> db
             (assoc-in (u/flatv :forms form-path :state) :submitting)
             (assoc-in (u/flatv :forms form-path :errors) nil))
     ::strh/http (submit-form (u/flatv :forms form-path)
                              (merge (:data form-spec)
                                     (get-in db (u/flatv :forms form-path :data)))
                              (:token db)
                              form-spec)}))

(defn success-base
  [db-update]
  (fn [db args]
    (let [[data form-path form-spec] args
          form-state-path (conj form-path :state)
          form-response-path  (conj form-path :response)]
      (if-let [callback (:callback form-spec)]
        (callback db args))
      (-> (db-update db args)
          (assoc-in form-state-path :success)
          (assoc-in form-response-path data)))))

(defn clear
  [db [data form-path form-spec]]
  (-> db
      (assoc-in (conj form-path :data) {})
      (assoc-in (conj form-path :errors) {})
      (assoc-in (conj form-path :ui-state) nil)))

(def submit-form-success
  (success-base (fn [db [data]] (u/deep-merge db data))))

(defn clear-on-success
  [db args]
  (-> (submit-form-success db args)
      (clear args)))

(reg-event-db ::submit-form-success
  [trim-v]
  submit-form-success)

(reg-event-db ::clear-on-success
  [trim-v]
  clear-on-success)



(reg-event-db ::submit-form-error
  [trim-v]
  (fn [db [errors form-path form-spec]]
    (pr "error!" errors form-path)
    (-> (assoc-in db (conj form-path :errors) (or errors {:cause :unknown}))
        (assoc-in (conj form-path :state) :sleeping))))

;; for cases where you can edit or manipulate many items in a list
(reg-event-fx ::submit-item
  [trim-v]
  (fn [{:keys [db]} [item-path {:keys [data id] :as item-spec}]]
    (let [item-path (u/flatv :item-submissions item-path (get data id))]
      {:db (-> db
               (assoc-in (conj item-path :state) :submitting)
               (assoc-in (conj item-path :errors) nil))
       ::strh/http (submit-form item-path
                                data
                                (:token db)
                                (dissoc item-spec :data))})))

(reg-event-db ::delete-item-success
  [trim-v]
  (fn [db [data form-path form-spec]]
    (let [[_ type _ id] form-path]
      (-> (if (get-in data [:data type id])
            (c/replace-ents db data)
            (update-in db [:data type] dissoc id))
          (clear-on-success [data form-path form-spec])
          (clear-on-success [data (assoc form-path 2 :update) form-spec])))))

(reg-event-fx ::delete-item
  [trim-v]
  (fn [{:keys [db]} [type data & [form-spec]]]
    (let [form-path [:forms type :delete (:db/id data)]]
      {:db db 
       ::strh/http (submit-form form-path
                                data
                                (:token db)
                                (merge {:success ::delete-item-success}
                                       form-spec))})))

(reg-event-fx ::undelete-item
  [trim-v]
  (fn [{:keys [db]} [type data & [form-spec]]]
    (let [form-path [:forms type :update (:db/id data)]]
      {:db db 
       ::strh/http (submit-form form-path
                                data
                                (:token db)
                                (merge {:success ::delete-item-success}
                                       form-spec))})))
