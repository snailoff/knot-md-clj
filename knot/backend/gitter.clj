(ns knot.backend.gitter
  (:require [knot.backend.mapper :as data]
            [environ.core :refer [env]]
            [clj-jgit.internal :refer :all]
            [clj-jgit.porcelain :as jgit]
            [clj-jgit.querying :refer :all]))







(def memo (atom {:git-commit-id-save? false}))
(defn memo-set [key val] (swap! memo assoc-in [key] val))
(defn memo-set-git-commit-id-save? [val] (swap! memo assoc-in [:git-commit-id-save?] val))

(def META-GIT-COMMIT-ID "GIT-COMMIT-ID")

(def git-config {:login (System/getenv "KNOT_GIT_USER")
                 :pw    (System/getenv "KNOT_GIT_PASSWORD")
                 :repo  (System/getenv "KNOT_GIT_REPOSITORY")})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; git

(defn git-changes []
  (jgit/with-credentials git-config
                         (let [repo (jgit/load-repo "temp")
                               latest-commit (get-head-commit repo)
                               saved-commit (data/load-meta-content META-GIT-COMMIT-ID)
                               since-commit (if (nil? saved-commit)
                                              ((last (jgit/git-log repo :all? true)) :id)
                                              (resolve-object saved-commit repo))]
                           (if (not= (.name since-commit) (.name latest-commit))
                             (do
                               (if (@memo :git-commit-id-save?)
                                 (data/save-meta META-GIT-COMMIT-ID (.name latest-commit)))
                               (changed-files-between-commits repo
                                                              since-commit
                                                              latest-commit))))))

(defn git-clone []
  (jgit/with-credentials git-config
                         (jgit/git-clone (git-config :repo)
                                         :branch "main"
                                         :dir "temp")))

(defn git-pull []
  (try
    (jgit/with-credentials git-config
                           (jgit/git-pull (jgit/load-repo "temp")))
    (catch Exception _
      (git-clone))))

(defn except-md? [path]
  (every? nil? [(re-find #"^\." path)
                (re-find #"^files/" path)]))


(defn git-parse []
  (doseq [[path action] (git-changes)]
    (println "---> " path action)
    (if (except-md? path)
      (cond
        (or (= action :add)
            (= action :edit)) (data/knot-save path action)
        (= action :delete) (data/knot-remove path action)
        :else (throw (Exception. (str "unknown action - " action))))
      (println "** ignored - " path))))


(defn reload-md []
  (git-pull)
  (git-parse))


(comment

  (memo-set :git-commit-id-save? true)
  (memo-set :git-commit-id-save? false)

  (let [subject "nana"
        _ (save-piece db-config subject "su" "co")
        p (load-piece subject)]
    (save-link-piece db-config (p :id) 0)
    (save-link-piece db-config 99 (p :id))
    (remove-piece subject))

  (remove-piece "lala")
  (remove-link-piece-children db-config 25))


