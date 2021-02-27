(ns sweet-tooth.frontend.core.utils
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [clojure.walk :as walk]
            [clojure.data :as data]
            #?(:cljs [ajax.url :as url])
            #?(:cljs [goog.object :as go])
            #?(:cljs [reagent.core :as r])
            #?(:cljs [reagent.ratom :as ratom]))
  #?(:cljs (:import [goog.async Debouncer])))

;;*** DOM utils
#?(:cljs
   (do (defn prevent-default
         [f]
         (fn [e]
           (.preventDefault e)
           (f e)))

       (defn el-by-id [id]
         (.getElementById js/document id))

       (defn scroll-top
         []
         (aset (js/document.querySelector "body") "scrollTop" 0))

       (defn go-get
         "Google Object Get - Navigates into a javascript object and gets a nested value"
         [obj ks]
         (let [ks (if (string? ks) [ks] ks)]
           (reduce go/get obj ks)))

       (defn go-set
         "Google Object Set - Navigates into a javascript object and sets a nested value"
         [obj ks v]
         (let [ks (if (string? ks) [ks] ks)
               target (reduce (fn [acc k]
                                (go/get acc k))
                              obj
                              (butlast ks))]
           (go/set target (last ks) v))
         obj)

       (defn params-to-str
         [m]
         (->> m
              (map (fn [[k v]]
                     [(if (keyword? k) (subs (str k) 1) k)
                      (if (keyword? v) (subs (str v) 1) v)]))
              (into {})
              (url/params-to-str :java)))

       (defn expiring-reaction
         "Produces a reaction A' over a given reaction A that reverts
         to `expired-val` or nil after `timeout` ms"
         [sub timeout & [expired-val]]
         (let [default     (or expired-val nil)
               sub-tracker (r/atom default)
               state       (r/atom default)
               debouncer   (Debouncer. #(reset! state default)
                                       timeout)]
           (ratom/make-reaction #(let [sub-val  @sub
                                       subt-val @sub-tracker]
                                   (when (not= sub-val subt-val)
                                     (reset! sub-tracker sub-val)
                                     (reset! state sub-val)
                                     (.fire debouncer))
                                   @state))))

       (defn tv
         [e]
         (go-get e ["target" "value"]))))

(defn capitalize-words
  "Capitalize every word in a string"
  [s]
  (->> (str/split (str s) #"\b")
       (map str/capitalize)
       (str/join)))

(defn kw-str
  "Turn a keyword into a capitalized string"
  [kw]
  (-> (name kw)
      (str/replace #"-" " ")
      capitalize-words))

(defn path
  [scalar-or-vec]
  (if (vector? scalar-or-vec) scalar-or-vec [scalar-or-vec]))

(defn strk
  "Create a new keyword by appending strings to it"
  [key & args]
  (keyword (apply str (name key) args)))

(defn kebab
  [s]
  (str/replace s #"[^a-zA-Z]" "-"))

(defn toggle [v x y]
  (if (= v x) y x))

(defn flatv
  [& args]
  (into [] (flatten args)))

(defn now
  []
  #?(:cljs (js/Date.)
     :clj  (java.util.Date.)))

(defn slugify
  [txt & [seg-count]]
  (cond->> (-> txt
               str/lower-case
               (str/replace #"-+$" "")
               (str/split #"[^a-zA-Z0-9]+"))
    seg-count (take seg-count)
    true (str/join "-")))

(defn pluralize
  [s n]
  (if (= n 1) s (str s "s")))

(defn id-num
  "Extracts the integer part of a string. Useful for SEO-friendly urls
  that combine text with an id."
  [id-str]
  (re-find #"^\d+" id-str))

(defn deep-merge-with
  "Like merge-with, but merges maps recursively, applying the given fn
  only when there's a non-map at a particular level.
  (deepmerge + {:a {:b {:c 1 :d {:x 1 :y 2}} :e 3} :f 4}
               {:a {:b {:c 2 :d {:z 9} :z 3} :e 100}})
  -> {:a {:b {:z 3, :c 3, :d {:z 9, :x 1, :y 2}}, :e 103}, :f 4}"
  [f & maps]
  (apply
    (fn m [& maps]
      (if (every? map? maps)
        (apply merge-with m maps)
        (apply f maps)))
    maps))

(defn update-vals
  "Takes a map to be updated, x, and a map of
  {[k1 k2 k3] update-fn-1
   [k4 k5 k6] update-fn-2}
  such that such that k1, k2, k3 are updated using update-fn-1
  and k4, k5, k6 are updated using update-fn-2"
  [x update-map]
  (let [keys-to-update (set (keys x))]
    (reduce (fn [x [ks update-fn]]
              (reduce (fn [x k]
                        (if (contains? keys-to-update k)
                          (update x k update-fn)
                          x))
                      x
                      (set/intersection keys-to-update (set ks))))
            x
            update-map)))

(defn deep-merge
  "Like merge, but merges maps recursively"
  [db m]
  (deep-merge-with (fn [_ x] x) db m))

(defn set-toggle
  "Toggle `val`'s membership in set `s`"
  [s val]
  (let [s (or s #{})]
    ((if (contains? s val) disj conj) s val)))

(defn dissoc-in
  "Remove value in `m` at `p`"
  [m p]
  (update-in m (butlast p) dissoc (last p)))

(defn projection?
  "Is every value in x present in y?"
  [x y]
  {:pre [(and (seqable? x) (seqable? y))]}
  (let [diff (second (data/diff y x))]
    (->> (walk/postwalk (fn [x]
                          (when-not (and (map? x)
                                         (nil? (first (vals x))))
                            x))
                        diff)
         (every? nil?))))>

(defn move-keys
  "if keys are present at top level, place them in a nested map"
  [m ks path]
  (reduce (fn [m k]
            (if (contains? m k)
              (-> m
                  (assoc-in (conj path k) (get m k))
                  (dissoc k))
              m))
          m
          ks))

(defn key-by
  [k xs]
  (into {} (map (juxt k identity) xs)))
