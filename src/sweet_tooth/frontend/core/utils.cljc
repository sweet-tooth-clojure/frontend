(ns sweet-tooth.frontend.core.utils
  (:require [clojure.string :as str]
            [clojure.set :as set]))

;;*** DOM utils TODO move to ui.cljs
#?(:cljs (defn prevent-default
           [f]
           (fn [e]
             (.preventDefault e)
             (f e))))

#?(:cljs (defn el-by-id [id]
           (.getElementById js/document id)))

#?(:cljs (defn scroll-top
           []
           (aset (js/document.querySelector "body") "scrollTop" 0)))

(defn tv
  [e]
  (aget e "target" "value"))

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
