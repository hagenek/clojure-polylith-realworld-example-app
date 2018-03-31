(ns clojure.org.realworld.article.store-test
  (:require [clj-time.core :as t]
            [clojure.java.jdbc :as jdbc]
            [clojure.org.realworld.database.interface :as database]
            [clojure.org.realworld.article.store :as store]
            [clojure.test :refer :all]))

(defn test-db
  ([] {:classname   "org.sqlite.JDBC"
       :subprotocol "sqlite"
       :subname     "test.db"})
  ([_] (test-db)))

(defn prepare-for-tests [f]
  (with-redefs [database/db test-db]
    (let [db (test-db)]
      (database/generate-db db)
      (f)
      (database/drop-db db))))

(use-fixtures :each prepare-for-tests)

(deftest find-by-slug--test
  (let [_       (jdbc/insert! (test-db) :article {:slug "this-is-slug"})
        res1    (store/find-by-slug "this-is-slug")
        article {:body        nil
                 :createdAt   nil
                 :description nil
                 :id          1
                 :slug        "this-is-slug"
                 :title       nil
                 :updatedAt   nil
                 :userId      nil}
        res2    (store/find-by-slug "wrong-slug")]
    (is (= article res1))
    (is (nil? res2))))

(deftest insert-article!--test
  (let [now     (t/now)
        article {:slug        "slug"
                 :title       "title"
                 :description "description"
                 :body        "body"
                 :createdAt   now
                 :updatedAt   now
                 :userId      1}
        _       (store/insert-article! article)
        res     (store/find-by-slug "slug")]
    (is (= (assoc article :id 1
                          :createdAt (-> article :createdAt str)
                          :updatedAt (-> article :updatedAt str))
           res))))

(deftest tags-with-names--test
  (let [_ (jdbc/insert-multi! (test-db) :tag [{:name "tag1"}
                                              {:name "tag2"}
                                              {:name "tag3"}
                                              {:name "tag4"}
                                              {:name "tag5"}
                                              {:name "tag6"}])
        res (store/tags-with-names ["tag1" "tag2" "tag3"])]
    (is (= [{:id   1
              :name "tag1"}
            {:id   2
             :name "tag2"}
            {:id   3
             :name "tag3"}
            res]))))

(deftest add-tags-to-article!--test
  (let [_ (jdbc/insert-multi! (test-db) :tag [{:name "tag1"}
                                              {:name "tag2"}
                                              {:name "tag3"}
                                              {:name "tag4"}
                                              {:name "tag5"}
                                              {:name "tag6"}])
        _ (store/add-tags-to-article! 1 ["tag1" "tag2" "tag3"])
        res (store/article-tags 1)]
    (is (= ["tag1" "tag2" "tag3"] res))))

(deftest article-tags--test
  (let [_ (jdbc/insert-multi! (test-db) :tag [{:name "tag1"}
                                              {:name "tag2"}
                                              {:name "tag3"}
                                              {:name "tag4"}
                                              {:name "tag5"}
                                              {:name "tag6"}])
        _ (jdbc/insert-multi! (test-db) :articleTags [{:articleId 1 :tagId 1}
                                                      {:articleId 1 :tagId 2}
                                                      {:articleId 1 :tagId 3}
                                                      {:articleId 2 :tagId 3}
                                                      {:articleId 2 :tagId 4}
                                                      {:articleId 2 :tagId 5}])
        res (store/article-tags 1)]
    (is (= ["tag1" "tag2" "tag3"] res))))

(deftest insert-tag!--tag-exists--do-nothing
  (let [_ (jdbc/insert! (test-db) :tag {:name "tag1"})
        res (store/insert-tag! "tag1")]
    (is (nil? res))))

(deftest insert-tag!--tag-does-not-exist--insert-tag
  (let [_ (store/insert-tag! "tag1")
        tags (store/tags-with-names "tag1")]
    (is (= [{:id   1
             :name "tag1"}]
           tags))))

(deftest favorited?--not-favorited--return-false
  (let [favorited? (store/favorited? 1 1)]
    (is (false? favorited?))))

(deftest favorited?--favorited--return-true
  (let [_ (jdbc/insert! (test-db) :favoriteArticles {:articleId 1 :userId 1})
        favorited? (store/favorited? 1 1)]
    (is (true? favorited?))))

(deftest favorites-count--not-favorited--return-0
  (let [favorites-count (store/favorites-count 1)]
    (is (= 0 favorites-count))))

(deftest favorites-count--favorited--return-favorites-count
  (let [_ (jdbc/insert-multi! (test-db) :favoriteArticles [{:articleId 1 :userId 1}
                                                           {:articleId 1 :userId 2}
                                                           {:articleId 1 :userId 3}])
        favorites-count (store/favorites-count 1)]
    (is (= 3 favorites-count))))

(deftest update-article!--test
  (let [now (t/now)
        _    (store/insert-article! {:slug        "slug"
                                     :title       "title"
                                     :description "description"
                                     :body        "body"
                                     :createdAt   now
                                     :updatedAt   now
                                     :userId      1})
        update-time (t/now)
        article {:slug        "updated-slug"
                 :title       "updated-title"
                 :description "description"
                 :body        "body"
                 :updatedAt   update-time}
        _    (store/update-article! 1 article)
        res  (store/find-by-slug "updated-slug")]
    (is (= (assoc article :id 1
                          :createdAt (str now)
                          :updatedAt (str update-time)
                          :userId 1)
           res))))

(deftest delete-article!--test
  (let [now (t/now)
        _    (store/insert-article! {:slug        "slug"
                                     :title       "title"
                                     :description "description"
                                     :body        "body"
                                     :createdAt   now
                                     :updatedAt   now
                                     :userId      1})
        _    (jdbc/insert-multi! (test-db) :tag [{:name "tag1"} {:name "tag2"} {:name "tag3"}])
        _    (jdbc/insert-multi! (test-db) :articleTags [{:tagId 1 :articleId 1}
                                                         {:tagId 2 :articleId 1}
                                                         {:tagId 3 :articleId 1}])
        _    (jdbc/insert-multi! (test-db) :favoriteArticles [{:userId 1 :articleId 1}
                                                              {:userId 2 :articleId 1}
                                                              {:userId 3 :articleId 1}])
        article-before (store/find-by-slug "slug")
        favorites-count-before (store/favorites-count 1)
        tags-before (store/article-tags 1)
        _ (store/delete-article! 1)
        article-after (store/find-by-slug "slug")
        favorites-count-after (store/favorites-count 1)
        tags-after (store/article-tags 1)]
    (is (not (nil? article-before)))
    (is (= 3 favorites-count-before))
    (is (= ["tag1" "tag2" "tag3"] tags-before))
    (is (nil? article-after))
    (is (= 0 favorites-count-after))
    (is (= [] tags-after))))
