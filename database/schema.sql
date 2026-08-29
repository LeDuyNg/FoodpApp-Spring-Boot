-- FOODpApp MySQL schema — Spring Boot / JPA migration
--
-- This is the "improved" relational design referenced in
-- SPRING_BOOT_MIGRATION_GUIDE.md §7: it fixes several things the original
-- SQLite/SQLAlchemy schema didn't enforce (unique username/email, a real
-- Comment/Rating relationship instead of string-encoded IDs, tags as real
-- columns instead of a JSON blob, cascading deletes instead of manual
-- cleanup loops).
--
-- Your JPA @Entity classes (see the guide, §7) must match these tables
-- exactly: same table names, column names, and nullability. If you let
-- Hibernate generate the schema instead (spring.jpa.hibernate.ddl-auto=update),
-- you don't strictly need to run this file — but running it first and then
-- setting ddl-auto=validate is the more disciplined way to work, and it's
-- what this script assumes.
--
-- Run this via database/init-db.sh (recommended), or manually:
--   mysql -u foodapp_user -p foodapp < database/schema.sql

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(32)  NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email    UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- recipes
-- The six tag columns replace the original `tags` JSON blob (models.py's
-- Recipe.tags). They're fixed, single-valued categories (see the
-- RadioField choices in forms.py), so plain columns are simpler than JSON
-- and let you filter with ordinary WHERE clauses / Spring Data query
-- methods instead of func.json_extract(...).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS recipes (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    title        VARCHAR(80)  NOT NULL,
    description  TEXT         NOT NULL,
    ingredients  TEXT         NOT NULL,
    instructions TEXT         NOT NULL,
    created      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id      BIGINT       NOT NULL,
    temperature  VARCHAR(20)  NULL,
    dish_type    VARCHAR(20)  NULL,
    dairy        VARCHAR(20)  NULL,
    sweetness    VARCHAR(20)  NULL,
    meat         VARCHAR(20)  NULL,
    seafood      VARCHAR(20)  NULL,
    CONSTRAINT fk_recipes_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_recipes_user_id (user_id),
    INDEX idx_recipes_title (title),
    INDEX idx_recipes_tags (temperature, dish_type, dairy, sweetness, meat, seafood)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- comments
-- Replaces the original Recipe.comment_ids string column (models.py:74,
-- 150-167). ON DELETE CASCADE means deleting a recipe or a user
-- automatically deletes their comments — no manual cleanup loop needed
-- (compare to delete_all_comments() in the original models.py:169-177).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS comments (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT   NOT NULL,
    recipe_id  BIGINT   NOT NULL,
    comment    TEXT     NOT NULL,
    created    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_comments_user
        FOREIGN KEY (user_id)   REFERENCES users(id)   ON DELETE CASCADE,
    CONSTRAINT fk_comments_recipe
        FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE CASCADE,
    INDEX idx_comments_recipe_id (recipe_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- ratings
-- Replaces the original Recipe.ratings string column (models.py:79,
-- 99-116). The UNIQUE constraint on (user_id, recipe_id) is what
-- guarantees "one rating per user per recipe" — the original only
-- enforced that by scanning a string in a loop at write time.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ratings (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT  NOT NULL,
    recipe_id  BIGINT  NOT NULL,
    value      TINYINT NOT NULL,
    CONSTRAINT fk_ratings_user
        FOREIGN KEY (user_id)   REFERENCES users(id)   ON DELETE CASCADE,
    CONSTRAINT fk_ratings_recipe
        FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE CASCADE,
    CONSTRAINT uq_ratings_user_recipe UNIQUE (user_id, recipe_id),
    CONSTRAINT chk_ratings_value CHECK (value BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- favorite_recipes
-- Direct equivalent of the original favorite_recipes join table in
-- models.py:11-15 — this one didn't need redesigning.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS favorite_recipes (
    user_id   BIGINT NOT NULL,
    recipe_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, recipe_id),
    CONSTRAINT fk_favorite_recipes_user
        FOREIGN KEY (user_id)   REFERENCES users(id)   ON DELETE CASCADE,
    CONSTRAINT fk_favorite_recipes_recipe
        FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
