(ns app.utils
  (:require
   [promesa.core :as p]
   [beicon.v2.core :as rx]))

(defn timeout
  [ms]
  (p/create
   (fn [resolve _]
     (let [refid (atom nil)
           cbf
           (fn []
             (resolve)
             (.clearTimeout js/window @refid))]
       (reset! refid (.setTimeout js/window cbf ms))))))

(defn stream-from-event
  ([elem event-type]
   (stream-from-event elem event-type identity))

  ([elem event-type trfn]
   (rx/create
    (fn [observer]
      (let [listener #(rx/push! observer (trfn %))]
        (.addEventListener elem event-type listener)
        (fn []
          (.removeEventListener elem event-type listener)))))))

(defn event->coord
  [event]
  [(.-clientX event) (.-clientY event)])
