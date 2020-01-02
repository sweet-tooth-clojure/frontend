(ns sweet-tooth.frontend.form.flow
  (:require [re-frame.core :refer [reg-event-db reg-event-fx trim-v reg-sub subscribe] :as rf]
            [sweet-tooth.frontend.handlers :as sth]
            [sweet-tooth.frontend.core.flow :as c]
            [sweet-tooth.frontend.core.utils :as u]
            [sweet-tooth.frontend.sync.flow :as stsf]
            [sweet-tooth.frontend.paths :as p]
            [meta-merge.core :refer [meta-merge]]
            [taoensso.timbre :as timbre]))

;;------
;; Form subs
;;------

(reg-sub ::form
  (fn [db [_ partial-form-path]]
    (p/get-path db :form partial-form-path)))

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

(rf/reg-sub ::state-success?
  form-signal
  (fn [form _]
    (= :success (:state form))))

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

;; sync states
(defn sync-state
  [db [_ [endpoint action entity]]]
  (stsf/sync-state db [action endpoint {:route-params entity}]))

(rf/reg-sub ::sync-state sync-state)

(rf/reg-sub ::sync-active?
  (fn [db args]
    (= (sync-state db args) :active)))

(rf/reg-sub ::sync-success?
  (fn [db args]
    (= (sync-state db args) :success)))

(rf/reg-sub ::sync-fail?
  (fn [db args]
    (= (sync-state db args) :fail)))

;;------
;; Interacting with forms
;;------

(defn set-attr-facet
  [db [partial-form-path attr-path facet val]]
  (assoc-in db (p/full-path :form partial-form-path facet (u/path attr-path)) val))

(sth/rr reg-event-db ::update-attr-buffer
  [trim-v]
  (fn [db [partial-form-path attr-path val]]
    (set-attr-facet db [partial-form-path attr-path :buffer val])))

(sth/rr reg-event-db ::update-attr-errors
  [trim-v]
  (fn [db [partial-form-path attr-path validation-fn]]
    (let [attr-path (u/path attr-path)
          form-data (p/get-path db :form partial-form-path :buffer)]
      (assoc-in db
                (p/full-path :form partial-form-path :errors attr-path)
                (validation-fn form-data attr-path (get-in form-data attr-path))))))

(sth/rr reg-event-db ::touch-attr
  [trim-v]
  (fn [db [partial-form-path attr-path]]
    (update-in db
               (p/full-path :form partial-form-path :touched)
               (fn [touched-attrs]
                 (conj (or touched-attrs #{}) (u/path attr-path))))))

;;------
;; Building and submitting forms
;;------

;; Reset buffer to value when form was initialized
(sth/rr reg-event-db ::reset-form-buffer
  [trim-v]
  (fn [db [partial-form-path]]
    (let [path (p/full-path :form partial-form-path)]
      (update-in db path (fn [{:keys [base] :as form}]
                           (assoc form :buffer base))))))

(defn initialize-form
  [db [partial-form-path {:keys [buffer] :as form}]]
  (assoc-in db
            (p/full-path :form partial-form-path)
            (update form :base #(or % buffer))))

;; Populate form initial state
(sth/rr reg-event-db ::initialize-form
  [trim-v]
  initialize-form)

;; Populate form initial state
(sth/rr reg-event-db ::initialize-form-from-path
  [trim-v]
  (fn [db [partial-form-path {:keys [data-path data-fn]
                              :or {data-fn identity}
                              :as form}]]
    (initialize-form db [partial-form-path (-> form
                                               (assoc :buffer (data-fn (get-in db (u/path data-path))))
                                               (dissoc :data-path :data-fn))])))

;; nils out form
(defn clear-form
  [db partial-form-path]
  (let [path (p/full-path :form partial-form-path)]
    (assoc-in db path nil)))

(sth/rr reg-event-db ::clear-form
  [trim-v]
  (fn [db [partial-form-path]]
    (clear-form db partial-form-path)))

;; TODO spec set of possible actions
;; TODO spec out form map, keys :buffer :state :ui-state etc
(def form-states #{nil :submitting :success :sleeping})

(defn form-sync-opts
  "Returns a request that the sync handler can use

  - `success` and `fail` are the handlers for request completion.
  - `form-spec` is a way to pass on whatevs data to the request
    completion handler.
  - the `:sync` key of form spec can customize the sync request"
  [full-form-path data {:keys [sync]
                        :as   form-spec}]
  (let [[_ endpoint action route-params] full-form-path]
    [action
     (get form-spec :route-name endpoint)
     (-> (merge {:params       data
                 :route-params (or route-params data)}
                sync)
         (update :on (partial merge {:success [::submit-form-success :$ctx]
                                     :fail    [::submit-form-fail :$ctx]}))
         (update :on meta-merge {:$ctx {:full-form-path full-form-path
                                        :form-spec      form-spec}}))]))

;; update db to indicate form's submitting, clear old errors
;; build form request
(sth/rr reg-event-fx ::submit-form
  [trim-v]
  (fn [{:keys [db]} [partial-form-path & [form-spec]]]
    (let [full-form-path (p/full-path :form partial-form-path)]
      {:db       (-> db
                     (assoc-in (conj full-form-path :state) :submitting)
                     (assoc-in (conj full-form-path :errors) nil))
       :dispatch [::stsf/sync (form-sync-opts full-form-path
                                              (merge (:data form-spec)
                                                     (get-in db (conj full-form-path :buffer)))
                                              form-spec)]})))

;;--------------------
;; submit variations: many items in a list
;;--------------------

(sth/rr reg-event-fx ::submit-item
  [trim-v]
  (fn [{:keys [db]} [item-path {:keys [data id] :as item-spec}]]
    (let [item-path (u/flatv :item-submissions item-path (get data id))]
      {:db (-> db
               (assoc-in (conj item-path :state) :submitting)
               (assoc-in (conj item-path :errors) nil))
       :dispatch [::stsf/sync (form-sync-opts item-path
                                              data
                                              (dissoc item-spec :buffer))]})))

(sth/rr reg-event-fx ::delete-item
  [trim-v]
  (fn [{:keys [db]} [type data & [form-spec]]]
    (let [full-form-path (p/full-path :form [type :delete (:db/id data)])]
      {:db db
       :dispatch [::stsf/sync (form-sync-opts full-form-path
                                              data
                                              (merge {:success ::delete-item-success}
                                                     form-spec))]})))

(sth/rr reg-event-fx ::undelete-item
  [trim-v]
  (fn [{:keys [db]} [type data & [form-spec]]]
    (let [full-form-path (p/full-path :form [type :update (:db/id data)])]
      {:db db
       :dispatch [::stsf/sync (form-sync-opts full-form-path
                                              data
                                              (merge {:success ::delete-item-success}
                                                     form-spec))]})))

;;--------------------
;; handle form success/fail
;;--------------------

(defn success-base
  "Produces a function that can be used for handling form submission success.
  It handles the common behaviors:
  - updating the form state to :success
  - populating the form's `:response` key with the returned data
  - calls callback specified by `callback` in `form-spec`
  - clears form keys specified by `:clear` in `form-spec`

  You customize success-base by providing a `db-update` function which
  will e.g. `merge` or `deep-merge` values from the response.

  TODO investigate using the `after` interceptor"
  [db-update]
  (fn [{:keys [db]} [{:keys [full-form-path form-spec]
                      {:keys [response-data]} :resp
                      :as args}]]
    (if-let [callback (:callback form-spec)]
      (callback db args))
    (cond-> {:db (let [updated-db (db-update db response-data)]
                   (if (= :all (:clear form-spec))
                     (assoc-in updated-db full-form-path {})
                     (update-in updated-db full-form-path merge
                                {:state :success :response response-data}
                                (zipmap (:clear form-spec) (repeat nil)))))}
      (:expire form-spec) (assoc ::c/debounce-dispatch (map (fn [[k v]]
                                                              {:ms       v
                                                               :id       [:expire full-form-path k]
                                                               :dispatch [::c/dissoc-in (conj full-form-path k)]})
                                                            (:expire form-spec))))))

(def submit-form-success
  (success-base c/update-db))

(sth/rr reg-event-fx ::submit-form-success
  [trim-v]
  submit-form-success)

(defn submit-form-fail
  [db [{:keys [full-form-path resp]
        {:keys [response-data]} :resp}]]
  (timbre/info "form submit fail:" resp full-form-path)
  (-> (assoc-in db (conj full-form-path :errors) (or (get-in response-data [0 1]) {:cause :unknown}))
      (assoc-in (conj full-form-path :state) :sleeping)))

(sth/rr reg-event-db ::submit-form-fail
  [trim-v]
  submit-form-fail)

(sth/rr reg-event-db ::delete-item-success
  [trim-v]
  (fn [db {:keys [full-form-path form-spec]
           {:keys [response-data]} :resp}]
    (let [[_ type _ id] full-form-path]
      (-> (if (get-in response-data [:buffer type id])
            (c/replace-ents db response-data)
            (update-in db [:buffer type] dissoc id))
          ;; TODO why is this called twice?
          (submit-form-success [response-data full-form-path form-spec])
          (submit-form-success [response-data (assoc full-form-path 2 :update) form-spec])))))

;;--------------------
;; form ui
;;--------------------

(defn toggle-form
  [db path data]
  (update-in db (p/full-path :form path)
             (fn [form]
               (let [{:keys [ui-state]} form]
                 (if ui-state
                   nil
                   {:buffer data
                    :base data
                    :ui-state true})))))

(rf/reg-event-db ::toggle-form
  [rf/trim-v]
  (fn [db [path data]] (toggle-form db path data)))
