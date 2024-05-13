(ns app.main
  (:require
   [app.utils :as utils]
   [promesa.core :as p]
   [beicon.v2.core :as rx]))

(def active-subs (atom []))

(def active-stream? #{"tap"})

(def streams
  {
   ;; Basic cold stream
   "basic"
   (rx/of 1 2 3)

   ;; From timeout
   "timer"
   (rx/timer 0 100)

   ;; Create a promise from a function
   "constructor"
   (rx/create
    (fn [observer]
      ;; p/let works like async/await in javascript
      (p/let [_ (utils/timeout 1000)
              _ (rx/push! observer "Hello")
              _ (utils/timeout 1000)
              _ (rx/push! observer "World")]
        (rx/end! observer))))

   ;; From mouseevent
   "mousemove"
   (utils/stream-from-event js/document "mousemove" utils/event->coord)

   "mouseclick"
   (utils/stream-from-event js/document "mousedown" utils/event->coord)

   ;; Making stream async
   "async"
   (->> (rx/of 1 2 3)
        (rx/observe-on :async))

   "map+filter+take"
   (->> (utils/stream-from-event js/document "mousemove")
        (rx/map utils/event->coord)
        (rx/filter (fn [[x y]] (and (< 100 x 500) (< 100 y 500))))
        (rx/take 1))

   "concat"
   (rx/concat
    (->> (utils/stream-from-event js/document "mousedown" utils/event->coord)
         (rx/take 2))
    (utils/stream-from-event js/document "mousemove" utils/event->coord))

   "merge"
   (rx/merge
    (utils/stream-from-event js/document "mousedown" utils/event->coord)
    (utils/stream-from-event js/document "mousemove" utils/event->coord))

   "debounce"
   (->> (utils/stream-from-event js/document "mousemove" utils/event->coord)
        (rx/debounce 100))

   "tap+ignore"
   (->> (rx/of 1 2 3)
        (rx/tap #(println "A:" %))
        (rx/map #(* % 10))
        (rx/tap #(println "B:" %))
        (rx/ignore))

   }
  )


(defn hot-vs-cold
  []
  ;; Cold vs hot
  ;; cold - values are not "emitted" until somebody is subscribed
  ;; hot - values are emited externaly and if nobody is listening will be lost

  (let [s1 (rx/of 1 2 3 4)]
    (->> s1 (rx/subs! println))
    (prn "===")
    (->> s1 (rx/subs! println)))

  (let [s1 (->> (utils/stream-from-event (.-body js/document) "mousedown" utils/event->coord)
                (rx/take 4))]
    (->> s1 (rx/subs!
             println
             nil
             (fn []
               (prn "====")
               (->> s1 (rx/subs! println)))))))

(defn sync-vs-async
  []

  (prn ">START S1")
  (let [s1 (rx/of 1 2 3 4)]
    (->> s1 (rx/subs! println))
    (prn "===")
    (->> s1 (rx/subs! println)))
  (prn ">STOP S1")

  
  (prn ">START S2")
  (let [s2 (->> (rx/of 1 2 3 4) (rx/observe-on :async) (rx/share))]
    (->> s2 (rx/subs! println))
    (prn "===")
    (->> s2 (rx/subs! println)))
  (prn ">STOP S2")

  )


;; start is called by init and after code reloading finishes
(defn ^:dev/after-load start []
  (doseq [[name stream] (filter (fn [[name _]] (active-stream? name)) streams)]
    (let [subid (->> stream
                     (rx/subs!
                      #(println (str "[" name "] " %))
                      #(println (str "[" name "] ERROR: " %))
                      #(println (str "[" name "] COMPLETED"))
                      ))]
      (swap! active-subs conj subid)))


  #_(hot-vs-cold)
  #_(sync-vs-async)
  
  )

(defn init []
  ;; init is called ONCE when the page loads
  ;; this is called in the index.html and must be exported
  ;; so it is available even in :advanced release builds
  (start))

;; this is called before any code is reloaded
(defn ^:dev/before-load stop []
  (doseq [sub @active-subs]
    (rx/dispose! sub))
  (reset! active-subs []))

