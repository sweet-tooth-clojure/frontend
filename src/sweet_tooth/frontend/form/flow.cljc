(ns sweet-tooth.frontend.form.flow
  (:require [re-frame.core :as rf]
            [sweet-tooth.frontend.handlers :as sth]
            [sweet-tooth.frontend.core.flow :as c]
            [sweet-tooth.frontend.core.utils :as u]
            [sweet-tooth.frontend.sync.flow :as stsf]
            [sweet-tooth.frontend.paths :as p]
            [meta-merge.core :refer [meta-merge]]
            [taoensso.timbre :as timbre]
            [clojure.set :as set]))

;;------
;; Form subs
;;------

(rf/reg-sub ::form
  (fn [db [_ partial-form-path]]
    (p/get-path db :form partial-form-path)))

(defn form-signal
  [[_ partial-form-path]]
  (rf/subscribe [::form partial-form-path]))

(def sub-name->form-key
  {::state        :state
   ::ui-state     :ui-state
   ::errors       :errors
   ::buffer       :buffer
   ::base         :base
   ::input-events :input-events})

(def form-keys (set (vals sub-name->form-key)))

(doseq [[sub-name attr] sub-name->form-key]
  (rf/reg-sub sub-name
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
    (rf/subscribe [facet partial-form-path])))

(rf/reg-sub ::attr-buffer
  (attr-facet-sub ::buffer)
  (fn [buffer [_ _partial-form-path attr-path]]
    (get-in buffer (u/path attr-path))))

(rf/reg-sub ::attr-errors
  (attr-facet-sub ::errors)
  (fn [errors [_ _partial-form-path attr-path]]
    (get-in errors (u/path attr-path))))

(rf/reg-sub ::attr-input-events
  (attr-facet-sub ::input-events)
  (fn [input-events [_ _partial-form-path attr-path]]
    (get-in input-events (u/path attr-path))))

;; Has the user interacted with the input that corresponds to this
;; attr?
(rf/reg-sub ::form-dirty?
  (fn [[_ partial-form-path]]
    [(rf/subscribe [::base partial-form-path])
     (rf/subscribe [::buffer partial-form-path])])
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
;; Errors
;;------

(rf/reg-sub ::attr-visible-errors
  (fn [[_ & args]]
    [(rf/subscribe (into [::attr-input-events] args))
     (rf/subscribe (into [::input-events] args))
     (rf/subscribe (into [::attr-errors] args))])
  (fn [[attr-input-events input-events attr-errors] [_ _ _ show-errors-on ]]
    (when (not-empty (set/intersection show-errors-on (set (into attr-input-events (::form input-events)))))
      attr-errors)))

;;------
;; Interacting with forms
;;------

(defn validate-form
  [form validate]
  (cond-> form
    validate (assoc :errors (validate (:buffer form)))))

;; Meant to handle all input events: focus, blur, change, etc
(defn input-event
  [db [{:keys [partial-form-path attr-path validate val event-type] :as opts}]]
  (let [form-path     (partial p/full-path :form partial-form-path)
        validation-fn (or validate (get-in db (form-path :validate)))]
    (update-in db (p/full-path :form partial-form-path)
               (fn [form]
                 (cond-> form
                   true                  (update-in (u/flatv :input-events attr-path) (fnil conj #{}) event-type)
                   (contains? opts :val) (assoc-in (u/flatv :buffer attr-path) val)
                   validation-fn         (validate-form validation-fn))))))

(sth/rr rf/reg-event-db ::input-event
  [rf/trim-v]
  input-event)

;;------
;; Building and submitting forms
;;------

(defn reset-form-buffer
  "Reset buffer to value when form was initialized. Typically paired with a 'reset' button"
  [db [partial-form-path]]
  (update-in db (p/full-path :form partial-form-path) (fn [{:keys [base] :as form}]
                                                        (assoc form :buffer base))))

(sth/rr rf/reg-event-db ::reset-form-buffer
  [rf/trim-v]
  reset-form-buffer)

(defn initialize-form
  [db [partial-form-path {:keys [buffer validate] :as form}]]
  (assoc-in db
            (p/full-path :form partial-form-path)
            (-> form
                (update :base #(or % buffer))
                (validate-form validate))))

;; Populate form initial state
(sth/rr rf/reg-event-db ::initialize-form
  [rf/trim-v]
  initialize-form)

(defn initialize-form-from-path
  [db [partial-form-path {:keys [data-path data-fn]
                          :or   {data-fn identity}
                          :as   form}]]
  (initialize-form db [partial-form-path (-> form
                                             (assoc :buffer (data-fn (get-in db (u/path data-path))))
                                             (dissoc :data-path :data-fn))]))

;; Populate form initial state
(sth/rr rf/reg-event-db ::initialize-form-from-path
  [rf/trim-v]
  initialize-form-from-path)

;; nils out form
(defn clear-form
  [db [partial-form-path]]
  (let [path (p/full-path :form partial-form-path)]
    (assoc-in db path nil)))

(sth/rr rf/reg-event-db ::clear-form
  [rf/trim-v]
  clear-form)

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

(defn submit-form
  "build form request. update db to indicate form's submitting, clear
  old errors"
  [{:keys [db]} [partial-form-path & [form-spec]]]
  (let [full-form-path (p/full-path :form partial-form-path)]
    {:db       (-> db
                   (update-in full-form-path {:state :submitting, :errors nil})
                   (update-in (into full-form-path [:input-events ::form]) (fnil conj #{}) "submit"))
     :dispatch [::stsf/sync (form-sync-opts full-form-path
                                            (merge (:data form-spec)
                                                   (get-in db (conj full-form-path :buffer)))
                                            form-spec)]}))

(sth/rr rf/reg-event-fx ::submit-form
  [rf/trim-v]
  submit-form)

;; when user clicks submit on form that has errors
(sth/rr rf/reg-event-db ::register-form-submit
  [rf/trim-v]
  (fn [db [partial-form-path]]
    (update-in db (p/full-path :form partial-form-path :input-events ::form) (fnil conj #{}) "submit")))

;;--------------------
;; deleting
;;--------------------

;; TODO handle id-key in a universal manner
(defn delete-entity-optimistic-fn
  "Returns a handler that can be used to both send a delete sync and
  remove the entity from the ent db"
  [ent-type & [id-key]]
  (let [id-key (or id-key :id)]
    (fn [{:keys [db] :as cofx} [entity :as args]]
      (merge ((stsf/sync-fx [:delete ent-type]) cofx args)
             {:db (update-in db [:entity ent-type] dissoc (id-key entity))}))))

;;--------------------
;; handle form success/fail
;;--------------------

(defn success-base
  "Produces a function that can be used for handling form submission success.
  It handles the common behaviors:
  - updating the form state to `:success`
  - populating the form's `:response` key with the returned data
  - calls callback specified by `:callback`
  - clears form keys specified by `:clear`
  - `:expire` maps form keys to number of milliseconds to wait before clearing

  You customize success-base by providing a `db-update` function which
  will e.g. `merge` or `deep-merge` values from the response.

  TODO investigate using the `after` interceptor"
  [db-update]
  (fn [{:keys [db]} [{:keys [full-form-path], {:keys [response-data]} :resp, :as args}
                     {:keys [callback clear keep expire]}]]
    (when callback (callback db args))
    (cond-> {:db (-> (db-update db response-data)
                     (update-in full-form-path select-keys (cond keep           keep
                                                                 (= :all clear) #{}
                                                                 clear          (set/difference form-keys (set clear))
                                                                 :else          form-keys)))}
      expire (assoc ::c/debounce-dispatch (map (fn [[k v]]
                                                 {:ms       v
                                                  :id       [:expire full-form-path k]
                                                  :dispatch [::c/dissoc-in (conj full-form-path k)]})
                                               expire)))))

(def submit-form-success
  (success-base c/update-db))

(sth/rr rf/reg-event-fx ::submit-form-success
  [rf/trim-v]
  submit-form-success)

(defn submit-form-fail
  [db [{:keys [full-form-path resp]
        {:keys [response-data]} :resp}]]
  (timbre/info "form submit fail:" resp full-form-path)
  (-> (assoc-in db (conj full-form-path :errors) (or (get-in response-data [0 1]) {:cause :unknown}))
      (assoc-in (conj full-form-path :state) :sleeping)))

(sth/rr rf/reg-event-db ::submit-form-fail
  [rf/trim-v]
  submit-form-fail)

(sth/rr rf/reg-event-db ::delete-item-success
  [rf/trim-v]
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
