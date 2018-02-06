(ns sweet-tooth.frontend.form.flow
  (:require [re-frame.core :refer [reg-event-db reg-event-fx trim-v reg-sub subscribe]]
            [ajax.core :refer [GET PUT POST DELETE]]
            [sweet-tooth.frontend.core.flow :as c]
            [sweet-tooth.frontend.core.utils :as u]
            [sweet-tooth.frontend.remote.flow :as strf]
            [sweet-tooth.frontend.paths :as p]
            [taoensso.timbre :as timbre]))

;;------
;; Form subs
;;------

(reg-sub ::form
  (fn [db [_ partial-form-path]]
    (get-in db (p/full-form-path partial-form-path))))

(defn form-signal
  [[_ partial-form-path]]
  (subscribe [::form partial-form-path]))

(doseq [[sub-name attr] {::state    :state
                         ::ui-state :ui-state
                         ::errors   :errors
                         ::buffer   :buffer
                         ::base     :base
                         ::touched  :touched}]
  (reg-sub sub-name
    form-signal
    (fn [form _]
      (get form attr))))

;; Value for a specific form attribute
(defn attr-facet-sub
  [facet]
  (fn [[_ partial-form-path]]
    (subscribe [facet partial-form-path])))

(reg-sub ::attr-buffer
  (attr-facet-sub ::buffer)
  (fn [buffer [_ _partial-form-path attr-path]]
    (get-in buffer (u/path attr-path))))

(reg-sub ::attr-errors
  (attr-facet-sub ::errors)
  (fn [errors [_ _partial-form-path attr-path]]
    (get-in errors (u/path attr-path))))

;; Has the user interacted with the input that corresponds to this
;; attr?
(reg-sub ::attr-touched?
  (attr-facet-sub ::touched)
  (fn [touched [_ _partial-form-path attr-path]]
    (contains? touched (u/path attr-path))))

(reg-sub ::form-dirty?
  (fn [[_ partial-form-path]]
    [(subscribe [::base partial-form-path])
     (subscribe [::buffer partial-form-path])])
  (fn [[base data]]
    (not= base data)))

;;------
;; Interacting with forms
;;------

(reg-event-db ::update-attr-buffer
  [trim-v]
  (fn [db [partial-form-path attr-path val]]
    (assoc-in db (p/full-form-path partial-form-path :buffer (u/path attr-path)) val)))

(reg-event-db ::update-attr-errors
  [trim-v]
  (fn [db [partial-form-path attr-path validation-fn]]
    (let [attr-path (u/path attr-path)
          form-data (get-in db (p/full-form-path partial-form-path :buffer))]
      (assoc-in db
                (p/full-form-path partial-form-path :errors attr-path)
                (validation-fn form-data attr-path (get-in form-data attr-path))))))

(reg-event-db ::touch-attr
  [trim-v]
  (fn [db [partial-form-path attr-path]]
    (update-in db
               (p/full-form-path partial-form-path :touched)
               (fn [touched-attrs]
                 (conj (or touched-attrs #{}) attr-path)))))

;;------
;; Building and submitting forms
;;------
(reg-event-db ::reset-form
  [trim-v]
  (fn [db [partial-form-path]]
    (let [path (p/full-form-path partial-form-path)]
      (update-in db path (fn [{:keys [data base] :as form}]
                           (assoc form :buffer base))))))

(reg-event-db ::initialize-form
  [trim-v]
  (fn [db [partial-form-path {:keys [data] :as form}]]
    (assoc-in db
              (p/full-form-path partial-form-path)
              (update form :base #(if % % data)))))

;; TODO spec set of possible actions
;; TODO spec out form map, keys :buffer :state :ui-state etc

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

(def form-states #{nil :submitting :success :sleeping})

(defn submit-form
  "Returns a config that ajax.core methods can use to send a request.  

  - `success` and `error` are the handlers for request completion.
  - `form-spec` is a way to pass on whatevs data to the request 
    completion handler.
  - the `:request-opts` key of form spec can customize the ajax request"
  [full-form-path data
   {:keys [success error]
    :or {success ::submit-form-success
         error ::submit-form-error}
    :as form-spec}]
  (let [[_ endpoint action] full-form-path
        [method url] (method-url endpoint action data)]
    (merge
      {:method method
       :url url
       :params data
       :on-success [success full-form-path form-spec]
       :on-fail [error full-form-path form-spec]}
      (:request-opts form-spec))))

;; update db to indicate form's submitting, clear old errors
;; build form request
(reg-event-fx ::submit-form
  [trim-v]
  (fn [{:keys [db]} [partial-form-path & [form-spec]]]
    (let [full-form-path (p/full-form-path partial-form-path)]
      {:db (-> db
               (assoc-in (conj full-form-path :state) :submitting)
               (assoc-in (conj full-form-path :errors) nil))
       :dispatch [::strf/http (submit-form full-form-path
                                           (merge (:buffer form-spec) (get-in db (conj full-form-path :buffer)))
                                           form-spec)]})))

(defn success-base
  "Produces a function that can be used for handling form submission success. 
  It handles the common behaviors:
  - updating the form state to :success
  - populating the form's `:response` key with the returned data
  - calls callback specified by `callback` in `form-spec`
  - clears form keys specified by `:clear` in `form-spec`
  
  You customize success-base by providing a `db-update` function which
  will e.g. `merge` or `deep-merge` values from the response."
  [db-update]
  (fn [db args]
    (let [[data full-form-path form-spec] args]
      (if-let [callback (:callback form-spec)]
        (callback db args))
      (let [updated-db (db-update db args)]
        (if (= :all (:clear form-spec))
          (assoc-in updated-db full-form-path {})
          (update-in updated-db full-form-path merge
                     {:state :success :response data}
                     (zipmap (:clear form-spec) (repeat nil))))))))

(def submit-form-success
  (success-base (fn success-deep-merge [db [data]] (u/deep-merge db data))))

(reg-event-db ::submit-form-success
  [trim-v]
  submit-form-success)

(reg-event-db ::submit-form-error
  [trim-v]
  (fn [db [errors full-form-path form-spec]]
    (timbre/info "form error:" errors full-form-path)
    (-> (assoc-in db (conj full-form-path :errors) (or errors {:cause :unknown}))
        (assoc-in (conj full-form-path :state) :sleeping))))

;; for cases where you can edit or manipulate many items in a list
(reg-event-fx ::submit-item
  [trim-v]
  (fn [{:keys [db]} [item-path {:keys [data id] :as item-spec}]]
    (let [item-path (u/flatv :item-submissions item-path (get data id))]
      {:db (-> db
               (assoc-in (conj item-path :state) :submitting)
               (assoc-in (conj item-path :errors) nil))
       :dispatch [::strf/http (submit-form item-path
                                           data
                                           (dissoc item-spec :buffer))]})))

(reg-event-db ::delete-item-success
  [trim-v]
  (fn [db [data full-form-path form-spec]]
    (let [[_ type _ id] full-form-path]
      (-> (if (get-in data [:buffer type id])
            (c/replace-ents db data)
            (update-in db [:buffer type] dissoc id))
          ;; TODO why is this called twice?
          (submit-form-success [data full-form-path form-spec])
          (submit-form-success [data (assoc full-form-path 2 :update) form-spec])))))

(reg-event-fx ::delete-item
  [trim-v]
  (fn [{:keys [db]} [type data & [form-spec]]]
    (let [full-form-path (p/full-form-path [type :delete (:db/id data)])]
      {:db db
       :dispatch [::strf/http (submit-form full-form-path
                                           data
                                           (merge {:success ::delete-item-success}
                                                  form-spec))]})))

(reg-event-fx ::undelete-item
  [trim-v]
  (fn [{:keys [db]} [type data & [form-spec]]]
    (let [full-form-path (p/full-form-path [type :update (:db/id data)])]
      {:db db
       :dispatch [::strf/http (submit-form full-form-path
                                           data
                                           (merge {:success ::delete-item-success}
                                                  form-spec))]})))
