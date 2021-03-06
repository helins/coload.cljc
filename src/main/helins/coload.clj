;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at https://mozilla.org/MPL/2.0/.


(ns helins.coload

  "Provides a Shadow-CLJS [[hook]] which synchronizes Clojure code with Clojurescript code in a
   CLJC context.
  
   See README."

  {:author "Adam Helinski"}

  (:require [clojure.set]
            [clojure.tools.namespace.dir]
            [clojure.tools.namespace.file]
            [clojure.tools.namespace.reload]
            [clojure.tools.namespace.track]
            [hawk.core                       :as hawk]
            [taoensso.timbre                 :as log])
  (:import java.io.File))


;;;;;;;;;; State


(defn- -state

  ;; Provides a new state for [[-*state]].

  []

  {:tracker (delay
              (clojure.tools.namespace.track/tracker))})



(def ^:private -*state

  ;; State holding file tracker and watcher.

  (atom (-state)))


;;;;;;;;;; Private helpers for Hawk (file watching library)


(defn- -hawk-handler

  ;; Handler for Hawk watcher which updates the file tracker.

  [_hawk-ctx {:keys [file
                     kind]}]

  (swap! -*state
         update
         :tracker
         (fn [tracker]
           (delay
             ((if (identical? kind
                              :delete)
                clojure.tools.namespace.file/remove-files
                clojure.tools.namespace.file/add-files)
              @tracker
              [file]))))
  nil)



(defn- -hawk-clojure-file?

  ;; Is the given file a Clojure file?
  ;;
  ;; Important. For instance, a file editor might create temporary files.

  [_ctx {:keys [file]}]

  (clojure.tools.namespace.file/clojure-file? file))


;;;;;;;;;; Shadow-CLJS hook - Private helpers


(defn- -exec-plugin-hook+

  ;; For a given Shadow-CLJS compiling `stage`, executes a plugin function if its
  ;; metadata request executation for that `stage`.
  ;;
  ;; `param+` is the map provided to the plugin in the **shadow-cljs.edn** config file.

  [stage plugin+ param+]

  (doseq [[plugin-sym
           plugin-config] plugin+]
    (if-some [var-plugin (try
                           (requiring-resolve plugin-sym)
                           (catch Throwable e
                             (log/error e
                                        (format "While requiring and resolving plugin hook: %s for %s"
                                                plugin-sym
                                                stage))
                             nil))]
      (let [meta-plugin (meta var-plugin)]
        (when (or (identical? (:shadow.build/stage meta-plugin)
                              stage)
                  (contains? (:shadow.build/stages meta-plugin)
                             stage))
          (try
            (@var-plugin (-> (merge param+
                                    plugin-config)
                             (assoc :shadow.build/stage
                                    stage)))
            (catch Throwable e
              (log/error e
                         (format "While executing plugin  hook: %s for %s"
                                 plugin-sym
                                 stage))))))
      (log/error (format "Unable to resolve: %s"
                         plugin-sym))))
  nil)



(defn- -reload

  ;; Process of unloading and reloading relevant namespaces.

  [tracker stage plugin+]

  (let [load+    (into #{}
                       (tracker :clojure.tools.namespace.track/load))
        unload+  (into #{}
                       (tracker :clojure.tools.namespace.track/unload))
        remove+  (clojure.set/difference unload+
                                         load+)]
    (when (seq remove+)
      (log/info (format "Will unload: %s"
                        remove+)))
    (when (seq load+)
      (log/info (format "Will load: %s"
                        load+)))
    (-exec-plugin-hook+ stage
                        plugin+
                        {:coload/load+   load+
                         :coload/stage   stage
                         :coload/unload+ remove+}))
  (let [tracker-2 (clojure.tools.namespace.reload/track-reload tracker)]
    (when-some [err (tracker-2 :clojure.tools.namespace.reload/error)]
      (log/fatal err
                 (format "Error while reloading Clojure namespace: %s during %s"
                         (tracker-2 :clojure.tools.namespace.reload/error-ns)
                         stage))
      tracker)
    tracker-2))


;;;;;;;;;; Shadow-CLJS hook - Compiler steps


(defn- -shadow-configure

  ;; During the **configure** compilation stage in Shadow-CLJS, setup a watcher for the files
  ;; requested by plugins.

  [plugin+]

  (let [path+       (not-empty (into #{}
                                     (comp (map second)
                                           (mapcat :src)
                                           (map #(.getCanonicalPath (File. %))))
                                     plugin+))
        [state-old
         state-new] (swap-vals! -*state
                                (fn [state]
                                  (if (= path+
                                         (state :path+))
                                    state
                                    (if path+
                                      (-> (assoc state
                                                 :path+   path+
                                                 :watcher (delay
                                                            (hawk/watch! [{:filter  -hawk-clojure-file?
                                                                           :handler -hawk-handler
                                                                           :paths   path+}])))
                                          (update :tracker
                                                  (fn [tracker]
                                                    (delay
                                                      (-> @tracker
                                                          (clojure.tools.namespace.dir/scan-dirs path+)
                                                          (-reload :configure
                                                                   plugin+))))))
                                      (-state)))))
        watcher-old (state-old :watcher)
        watcher-new (state-new :watcher)]
    (if (identical? watcher-new
                    watcher-old)
      (-exec-plugin-hook+ :configure
                          plugin+
                          nil)
      (do
        (if path+
          (log/info (format "Watching: %s"
                            path+))
          (log/warn "Watching nothing: no path specified"))
        (-> state-new
            :tracker
            deref)
        (some-> watcher-old
                (-> deref
                    hawk/stop!))
        (some-> watcher-new
                deref))))
  nil)



(defn- -shadow-compile-prepare

  ;; During the **compile-prepare** compilation stage in Shadow-CLJS

  [plugin+]

  (-> (swap! -*state
             update
             :tracker
             (fn [tracker]
               (delay
                 (-reload @tracker
                          :compile-prepare
                          plugin+))))
      :tracker
      deref)
  nil)


;;;;;;;;;; Shadow-CLJS hook - Core implementation


(defn hook

  "Shadow-CLJS hook.
  
   See README."

  {:shadow.build/stages #{:compile-finish
                          :compile-prepare
                          :configure}}

  [{:as                    build
    :shadow.build/keys     [stage]
    :shadow.build.api/keys [compile-cycle]}
   plugin+]

  (try
    (some->> compile-cycle
             (swap! -*state
                    assoc
                    :compile-cycle))
    (case stage
      :compile-finish  (-exec-plugin-hook+ stage
                                           plugin+
                                           nil)
      :compile-prepare (-shadow-compile-prepare plugin+)
      :configure       (-shadow-configure plugin+))
    (catch Throwable e
      (log/fatal e
                 (format "During compilation stage %s"
                         stage))))
  build)


;;;;;;;;;; Rest of API


(defn clear!

  "Clear the current tracker.
  
   In other words, all watched files will be reloaded during next recompilation, whether they were
   modified or not."

  []

  (-> (swap! -*state
             (fn [{:as   state
                   :keys [path+
                          tracker]}]
               (cond->
                 state
                 (seq path+)
                 (assoc :tracker
                        (delay
                          (-> @tracker
                              (dissoc :clojure.tools.namespace.dir/time)
                              (clojure.tools.namespace.dir/scan-dirs path+)))))))
      :tracker
      deref)
  nil)



(defn compile-cycle

  "Returns the number designating the current compile cycle.
  
   Increments on each cycle."

  []

  (-> -*state
      deref
      :compile-cycle))
