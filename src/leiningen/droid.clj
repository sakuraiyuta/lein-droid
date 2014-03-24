;; ## Clojure is simple. Android should also be.
;; This plugin is intended to make your Clojure/Android development as
;; seamless and efficient as when developing ordinar Clojure JVM programs.
;;
(ns leiningen.droid
  (:refer-clojure :exclude [compile doall repl])
  (:use [leiningen.core.project :only [merge-profiles unmerge-profiles]]
        [leiningen.core.main :only [abort]]
        [leiningen.help :only (subtask-help-for)]
        [leiningen.droid.compile :only (compile-files compile clean-compile-dir code-gen)]
        [leiningen.droid
         [classpath :only [init-hooks]]
         [build :only [create-dex create-obfuscated-dex
                       crunch-resources package-resources create-apk
                       sign-apk zipalign-apk apk build jar]]
         [deploy :only [install run forward-port repl deploy]]
         [new :only [new init]]
         [compatibility :only [gather-dependencies]]
         [utils :only [proj wrong-usage android-parameters ensure-paths]]]))

(defn help
  "Shows the list of possible `lein droid` subtasks."
  ([]) ([droid-var]
          (println "lein-droid is a plugin for Clojure/Android development."
                   (subtask-help-for nil droid-var))))

(defn foo
  "This function just prints the project map."
  [project & args]
  (println project))

(defn doall
  "Metatask. Performs all Android tasks from compilation to the deployment."
  [{{:keys [library]} :android :as project} & device-args]
  (if library
    (build project)
    (do (doto project
          build apk)
        (apply deploy project device-args))))

(declare execute-subtask)

(defn transform-into-release
  "Takes a project map and replaces `:dev` profile with `:release` profile."
  [project]
  (-> project
      (unmerge-profiles [:dev :base])
      (merge-profiles [:release])
      android-parameters))

(def ^{:doc "Default set of tasks to create an application release."}
  release-routine ["clean-compile-dir" "build" "apk" "deploy"])

(defn execute-release-routine
  "Takes a release project map and executes tasks that create a
  release version of the application."
  [release-project & [adb-args]]
  (doseq [task release-routine]
    (execute-subtask release-project task adb-args)))

(defn release
  "Metatask. Builds, packs and deploys the release version of the project.

  Can also take optional list of subtasks to execute (instead of
  executing all of them) and arguments to `adb` for deploying."
  [project & args]
  (let [;; adb-args should be in the end of the argument list.
        [subtasks adb-args] (split-with #(not (.startsWith % "-")) args)
        subtasks (if (seq subtasks)
                   subtasks release-routine)
        release-project (transform-into-release project)]
    (doseq [task subtasks]
      (execute-subtask release-project task adb-args))))

(defn ^{:no-project-needed true
        :subtasks [#'new #'init #'code-gen #'compile
                   #'compile-files
                   #'create-dex #'create-obfuscated-dex
                   #'crunch-resources #'package-resources
                   #'create-apk #'sign-apk #'zipalign-apk
                   #'install #'run #'forward-port #'repl
                   #'build #'apk #'deploy #'doall #'release #'help
                   #'gather-dependencies #'jar]}
  droid
  "Supertask for Android-related tasks (see `lein droid` for list)."
  ([project]
     (help #'droid))
  ([project & [cmd & args]]
     (init-hooks)
     (some-> project
             (unmerge-profiles [:base])
             android-parameters   ;; Poor man's middleware here
             (execute-subtask cmd args))))

(defn execute-subtask
  "Executes a subtask defined by `name` on the given project."
  [project name args]
  (when (and (nil? project) (not (#{"new" "help" "init"} name)))
    (abort "Subtask" name "should be run from the project folder."))
  (case name
    ;; Standalone tasks
    "new" (if (< (count args) 2)
            (abort (wrong-usage "lein droid new" #'new))
            (apply new args))
    "init" (init (.getAbsolutePath (clojure.java.io/file ".")))
    "code-gen" (code-gen project)
    "clean-compile-dir" (clean-compile-dir project)
    "compile" (compile project)
    "compile-files" (compile-files project args)
    "create-dex" (create-dex project)
    "create-obfuscated-dex" (create-obfuscated-dex project)
    "crunch-resources" (crunch-resources project)
    "package-resources" (package-resources project)
    "create-apk" (create-apk project)
    "sign-apk" (sign-apk project)
    "zipalign-apk" (zipalign-apk project)
    "install" (apply install project args)
    "run" (apply run project args)
    "forward-port" (apply forward-port project args)
    "repl" (repl project)
    "gather-dependencies" (apply gather-dependencies project args)

    ;; Meta tasks
    "build" (build project)
    "apk" (apk project)
    "deploy" (apply deploy project args)
    "doall" (apply doall project args)
    "release" (apply release project args)
    "jar" (jar project)

    ;; Help tasks
    "foo" (foo project)
    "help" (help #'droid)

    (println "Subtask is not recognized:" name
             (subtask-help-for nil #'droid))))
