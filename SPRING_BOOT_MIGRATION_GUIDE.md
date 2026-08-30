# FOODpApp: Flask → Spring Boot Migration Guide

This guide walks you from your existing Flask/SQLAlchemy/Jinja2 app to an
equivalent Spring Boot/JPA/Thymeleaf app backed by MySQL. It assumes you're
comfortable with the Flask version and with Python generally, but **new to
Spring Boot** — so it front-loads the framework concepts you need before
diving into the port, and gives you fully-worked code for the boilerplate
layers (entities, config, DTOs) so you can see correct, idiomatic Spring code
once, while leaving the actual feature logic (controllers, services) as
guided exercises with detailed comments telling you exactly what to write and
where to find the original logic in your Flask code.

A companion MySQL setup script ships with this guide at
`database/init-db.sh` (+ `database/schema.sql`) — see §6.

---

## Table of Contents

0. [Spring Boot Fundamentals](#0-spring-boot-fundamentals-read-this-first)
1. [How to Use This Guide](#1-how-to-use-this-guide)
2. [What You're Building](#2-what-youre-building)
3. [Concept Cheat Sheet](#3-concept-cheat-sheet-flask--spring-boot)
4. [Tech Stack & Dependencies](#4-tech-stack--dependencies)
5. [Project Structure](#5-project-structure)
6. [MySQL Setup](#6-mysql-setup)
7. [Entity Design (full code)](#7-entity-design-full-code)
8. [Repository Layer (full code)](#8-repository-layer-full-code)
9. [DTOs & Validation (full code)](#9-dtos--validation-full-code)
10. [Security (full code)](#10-security-full-code)
11. [Service Layer (guided)](#11-service-layer-guided)
12. [Controllers (guided) & Route Table](#12-controllers-guided--route-table)
13. [Thymeleaf Templates (already written for you)](#13-thymeleaf-templates-already-written-for-you)
14. [Static Resources](#14-static-resources)
15. [External API Integration (TheMealDB)](#15-external-api-integration-themealdb)
16. [Testing](#16-testing)
17. [Suggested Build Order (Milestones)](#17-suggested-build-order-milestones)
18. [Troubleshooting: Common Beginner Errors](#18-troubleshooting-common-beginner-errors)
19. [Reference Docs](#19-reference-docs)

---

## 0. Spring Boot Fundamentals (read this first)

If you already know these concepts, skip to §1. If Spring Boot is new to you,
read this section before touching code — the rest of the guide assumes you
understand these ideas.

### 0.1 What Spring Boot actually is

Flask is a small library: you import it, call `Flask(__name__)`, and wire
things together yourself (as `app/__init__.py` does explicitly). **Spring**
(the framework Spring Boot is built on) is different: it's a *container* that
creates and wires your objects for you, based on annotations you put on your
classes. Spring Boot adds auto-configuration on top of that so you don't have
to hand-write the wiring yourself for common cases (a web server, a database
connection, etc.) — you mostly just add a dependency and a few config lines,
and Spring Boot configures the matching beans automatically.

### 0.2 Inversion of Control & Dependency Injection

This is the single most important concept to understand before writing any
Spring code.

In Flask, when `routes.py` needs the database, it just imports `db` directly
from `app`:
```python
from app import db
```
Your code reaches out and grabs its dependency itself.

In Spring, it's inverted: you **declare what you need** (usually as a
constructor parameter), and the framework hands it to you. You never call
`new RecipeRepository()` yourself. For example:

```java
@Service
public class RecipeService {

    private final RecipeRepository recipeRepository;

    // Spring sees this constructor, notices RecipeService needs a
    // RecipeRepository, and automatically passes in the one it already
    // manages — you never construct it yourself.
    public RecipeService(RecipeRepository recipeRepository) {
        this.recipeRepository = recipeRepository;
    }
}
```

This is called **Dependency Injection (DI)**, and the objects Spring creates
and hands around like this are called **beans**. "The Spring container" is
just the runtime system that creates all your `@Component`/`@Service`/
`@Repository`/`@Controller`-annotated classes once at startup, wires them
together via their constructors, and keeps them alive as singletons for the
life of the application. **Always prefer constructor injection** (as above)
over field injection (`@Autowired private Foo foo;`) — it makes dependencies
explicit and is what modern Spring style guides recommend.

### 0.3 What annotations are doing

Annotations (`@Entity`, `@GetMapping`, `@Service`, etc.) are metadata, not
magic — Spring scans your classes at startup, reads these annotations, and
uses them to decide what to create and how to wire it. A rough mental model:

| Annotation | What it tells Spring |
|---|---|
| `@SpringBootApplication` | "This is the entry point; scan this package and below for other annotated classes." |
| `@Component`, `@Service`, `@Repository`, `@Controller` | "Create one instance of this class and manage it as a bean." (These four are near-identical; `@Service`/`@Repository`/`@Controller` are just more specific names for readability and, in `@Repository`'s case, extra exception translation.) |
| `@Autowired` (on a constructor, or field) | "Inject a bean of this type here." (Optional on constructors if there's only one constructor — Spring infers it — but many teams add it explicitly for clarity.) |
| `@Entity` | "This class maps to a database table." |
| `@Configuration` + `@Bean` | "Run this method once at startup and register its return value as a bean" — used for things you don't own the source of (e.g. a `PasswordEncoder`) or need to configure by hand (e.g. the security filter chain). |
| `@GetMapping`/`@PostMapping` | "Route HTTP requests matching this path/method to this Java method." |

### 0.4 Request lifecycle (what happens when a browser hits your app)

```
Browser
  │  HTTP request (e.g. GET /recipes/7)
  ▼
DispatcherServlet (Spring's front controller — you never write this)
  │  looks at the URL + HTTP method, finds the matching @Controller method
  ▼
Your @Controller method (e.g. RecipeController.view(Long id, Model model))
  │  calls into...
  ▼
Your @Service (business logic, e.g. RecipeService.findById(id))
  │  calls into...
  ▼
Your @Repository (Spring Data JPA — talks to the database via Hibernate)
  │  returns an @Entity object (or a list of them)
  ▲
Your @Controller adds the entity to the `Model` and returns a view name (a String)
  ▼
Thymeleaf's view resolver finds templates/recipe-view.html and renders it,
substituting in the Model data
  ▼
HTML response sent back to the browser
```

This is the direct analog of: Flask route function → SQLAlchemy query →
`render_template(...)` → Jinja2 fills in the HTML.

### 0.5 What JPA/Hibernate is

JPA (Jakarta Persistence API) is a *specification* for mapping Java objects to
relational database rows — the direct conceptual equivalent of what
Flask-SQLAlchemy does. **Hibernate** is the actual implementation Spring Boot
uses under the hood (you rarely interact with Hibernate directly — you write
JPA annotations and call Spring Data repository methods). "ORM" (Object-
Relational Mapping) is the general term for what both SQLAlchemy and
Hibernate/JPA do.

### 0.6 Maven basics

Your project has a `pom.xml` file instead of `requirements.txt`. It declares
dependencies (libraries) and lets Maven download and manage them. You'll
mostly interact with it by:
- Adding a `<dependency>` block when you need a new library
- Running the project via the **Maven wrapper** that Spring Initializr
  generates for you: `./mvnw spring-boot:run` (macOS/Linux) — this downloads
  the correct Maven version automatically, so you don't need Maven installed
  globally.
- Running tests: `./mvnw test`

### 0.7 Recommended IDE

Use **IntelliJ IDEA** (the free Community edition is enough) — it has strong
Spring Boot support (recognizes `@Entity`/`@Autowired` wiring, generates
getters/setters, navigates from a URL string to its `@GetMapping`, etc.).
VS Code with the "Extension Pack for Java" + "Spring Boot Extension Pack" also
works if you prefer it. Avoid hand-writing boilerplate (getters/setters) —
use your IDE's "Generate" feature (IntelliJ: right-click in a class →
Generate → Getters and Setters).

---

## 1. How to Use This Guide

1. Read §0 above if you haven't already.
2. §7–§10 give you **complete, working code** for the layers that are mostly
   structural/boilerplate (entities, repositories, DTOs, security
   configuration). Type these in yourself (don't copy-paste — you retain far
   more by typing), understand every annotation, then move on.
3. §11–§12 give you **method signatures and detailed step-by-step comments**,
   not full implementations, for the layers that contain your app's actual
   business logic. This is where the real learning — and the "coding it
   myself" you asked for — happens. Each stub tells you exactly which lines
   of the original `routes.py`/`models.py` to reference for the logic you're
   translating.
4. §17 gives you a milestone order — build incrementally, run the app after
   every milestone, don't try to write everything before running anything.
5. §18 is a troubleshooting section for the errors every Spring Boot beginner
   hits in their first week. Check there before assuming something's
   fundamentally wrong.

---

## 2. What You're Building

A recipe-sharing app with:
- Registration/login/logout (session-based auth)
- CRUD on recipes (title, description, ingredients, instructions, 6 category tags)
- Comments and 1–5 star ratings on recipes
- Search (title/ingredients) and tag-based filtering
- Favoriting recipes (many-to-many)
- Profile view/update/delete (cascades to delete the user's recipes/comments/ratings)
- "Recipe of the day" (deterministic random pick, seeded by date)
- Pulling a random recipe from the external TheMealDB API and importing it as
  your own recipe

---

## 3. Concept Cheat Sheet: Flask → Spring Boot

| Flask / Python world | Spring Boot / Java world |
|---|---|
| `Flask(__name__)` app object (`app/__init__.py`) | `@SpringBootApplication` class with a `main()` method |
| `requirements.txt` | `pom.xml` |
| `app.config.from_mapping(...)` | `application.properties` |
| `@app.route("/path", methods=["GET","POST"])` | `@GetMapping("/path")` / `@PostMapping("/path")` on a `@Controller` class |
| `render_template("x.html", ...)` | `return "x";` from a controller method, with data added to the `Model` parameter first |
| Jinja2 templates (`templates/*.html`) | Thymeleaf templates (`src/main/resources/templates/*.html`) |
| `static/` folder | `src/main/resources/static/` |
| `db.Model` (Flask-SQLAlchemy) | `@Entity` class |
| `Model.query.get(id)` | `repository.findById(id)` → returns `Optional<T>` |
| `Model.query.filter_by(x=y).all()` | Spring Data derived query method, e.g. `findByUserId(Long userId)` |
| `db.session.add(obj); db.session.commit()` | `repository.save(obj)` |
| `db.session.delete(obj); db.session.commit()` | `repository.delete(obj)` |
| SQLite file `app.db` | MySQL server + JDBC driver |
| `flask_login` (`UserMixin`, `current_user`, `login_user`, `@login_required`) | Spring Security (`UserDetails`, `Authentication`, `.formLogin()`, `.authorizeHttpRequests()`) |
| `werkzeug.security.generate_password_hash` | `BCryptPasswordEncoder` |
| `flask_wtf.FlaskForm` + WTForms validators | A DTO class with Jakarta Bean Validation annotations, bound via `@ModelAttribute` + `BindingResult` |
| CSRF hidden field (Flask-WTF automatic) | Spring Security CSRF (automatic in Thymeleaf `<form th:action>` tags) |
| `flash(msg, category)` | `RedirectAttributes.addFlashAttribute(...)` |
| `url_for("endpoint", **args)` | Thymeleaf `@{/path/{id}(id=${id})}` link expressions |
| `python requests` | Spring's `RestClient` (or `RestTemplate`) |
| `db.relationship(..., secondary=...)` | JPA `@ManyToMany` + `@JoinTable` |
| `db.Column(db.JSON)` for `tags` | Real columns (this guide's recommendation — see §7.2) |

---

## 4. Tech Stack & Dependencies

Use **Spring Initializr** (start.spring.io) with:

- **Project:** Maven
- **Language:** Java
- **Spring Boot version:** latest stable 3.x
- **Packaging:** Jar
- **Java version:** 17 or 21

**Dependencies to select on Initializr**, and what each one is *for* (the
Flask equivalent, so it's not just a name to you):

| Initializr dependency | Maven artifact | Replaces |
|---|---|---|
| Spring Web | `spring-boot-starter-web` | Flask itself (routing, HTTP handling) — includes an embedded Tomcat server, so you don't run/configure a separate WSGI server the way you might with Flask+gunicorn |
| Spring Data JPA | `spring-boot-starter-data-jpa` | Flask-SQLAlchemy |
| Thymeleaf | `spring-boot-starter-thymeleaf` | Jinja2 |
| Spring Security | `spring-boot-starter-security` | Flask-Login |
| Validation | `spring-boot-starter-validation` | WTForms validators |
| MySQL Driver | `mysql-connector-j` | the `sqlite3` driver Flask-SQLAlchemy used implicitly |
| Spring Boot DevTools | `spring-boot-devtools` | Flask's `debug=True` auto-reload (`run.py:2`) |

**Add manually to `pom.xml`** (Initializr doesn't offer it as a checkbox):

```xml
<dependency>
    <groupId>org.thymeleaf.extras</groupId>
    <artifactId>thymeleaf-extras-springsecurity6</artifactId>
</dependency>
```
This lets your Thymeleaf templates use `sec:authorize` (the equivalent of
checking `current_user.is_authenticated` in a Jinja2 template) and makes CSRF
tokens get added to forms automatically.

---

## 5. Project Structure

Flask's flat `app/` package (one `models.py`, one `routes.py`, one `forms.py`)
becomes several Java packages, organized by *role* — this is the standard
Spring convention (a "layered architecture"):

```
src/main/java/com/yourname/foodapp/
├── FoodappApplication.java        # main() — replaces run.py
├── config/
│   └── SecurityConfig.java        # replaces flask_login setup in __init__.py
├── controller/                    # replaces routes.py — HTTP-facing, thin
│   ├── AuthController.java
│   ├── HomeController.java
│   ├── RecipeController.java
│   ├── ProfileController.java
│   └── ExternalRecipeController.java
├── model/                         # replaces models.py — JPA entities
│   ├── User.java
│   ├── Recipe.java
│   ├── Comment.java
│   └── Rating.java
├── repository/                    # Spring Data interfaces (data access)
│   ├── UserRepository.java
│   ├── RecipeRepository.java
│   ├── CommentRepository.java
│   └── RatingRepository.java
├── service/                       # business logic, called by controllers
│   ├── UserService.java
│   ├── RecipeService.java
│   └── MealDbClient.java
├── dto/                           # replaces forms.py
│   ├── LoginForm.java
│   ├── RegisterForm.java
│   ├── UpdateProfileForm.java
│   ├── RecipeForm.java
│   ├── CommentForm.java
│   └── RatingForm.java
└── security/
    ├── AppUserDetails.java        # wraps your User entity for Spring Security
    └── AppUserDetailsService.java # replaces @login.user_loader

src/main/resources/
├── application.properties
├── templates/                     # replaces app/templates/*.html
└── static/                        # replaces app/static/ (css, images)
```

**Why the layering matters:** in Flask, `routes.py` did everything —
validated input, queried the DB, and applied business rules, all in one
function. In Spring's convention, **controllers stay thin** (parse the
request, call a service, pick a view), **services hold the business logic**
(the stuff that would trigger a re-write if the rules changed), and
**repositories only know how to fetch/save data**. This split is what makes
each piece independently testable (see §16).

---

## 6. MySQL Setup

### 6.1 Install MySQL

```bash
brew install mysql
brew services start mysql
```

### 6.2 Run the included setup script

This repo includes `database/init-db.sh` and `database/schema.sql`, which
create the database, a dedicated non-root app user, and every table your
entities will map to (see §7 for why the schema looks the way it does).

```bash
cd database
./init-db.sh
```

It will prompt you for:
1. A new password to set for the app's MySQL user (`foodapp_user` by default)
2. Your MySQL admin (`root`) password, to authorize creating the database/user

When it finishes, it prints the exact `application.properties` lines to copy
into your new Spring Boot project.

You can override the defaults with environment variables if you want a
different name, e.g. `DB_NAME=foodapp_dev ./init-db.sh`.

**Copy `database/` (both files) into your new Spring Boot project root** once
you create it — that way the setup script travels with the project, and a
teammate (or grader) can run it too instead of you handing them a raw SQL
dump.

### 6.3 `application.properties`, line by line

Create `src/main/resources/application.properties` in your new project:

```properties
# --- Database connection ---
spring.datasource.url=jdbc:mysql://localhost:3306/foodapp?useSSL=false&serverTimezone=UTC
spring.datasource.username=foodapp_user
spring.datasource.password=the-password-you-set-in-init-db.sh
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# --- JPA / Hibernate ---
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

What each line does:
- `spring.datasource.url` — the JDBC connection string: protocol
  (`jdbc:mysql://`), host, port, and database name. This is the direct
  equivalent of `SQLALCHEMY_DATABASE_URI` in `app/__init__.py:15`.
- `spring.datasource.username`/`password` — matches the user
  `database/init-db.sh` created.
- `driver-class-name` — normally auto-detected from the JDBC URL; being
  explicit avoids ambiguity and is a common teaching convention.
- `spring.jpa.hibernate.ddl-auto=validate` — **this tells Hibernate to check
  that your `@Entity` classes match the existing database schema at startup,
  and fail loudly if they don't, rather than silently altering tables.**
  Since you're running `schema.sql` yourself to create tables, this is the
  disciplined choice (see the callout below). During very early development,
  some tutorials use `update` instead, which lets Hibernate auto-create/alter
  tables from your entities — convenient, but it means your database schema
  is *implicit*, driven by whatever your Java code currently looks like,
  which is confusing to debug and doesn't match how real teams work.
- `show-sql` / `format_sql` — prints the actual SQL Hibernate generates to
  your console, formatted readably. Turn this on while learning — it's the
  best way to build intuition for what your JPA annotations are actually
  doing under the hood (the direct equivalent of SQLAlchemy's `echo=True`).

> **Why `validate` instead of `update`, given you already have `schema.sql`:**
> With `ddl-auto=validate`, your database schema is the single source of
> truth, defined explicitly in one file you can read top to bottom
> (`database/schema.sql`), and your Java `@Entity` classes must match it. If
> they don't match, you get a clear startup error telling you exactly which
> column is wrong — much easier to debug than Hibernate silently deciding how
> to alter a live table. This is also standard practice on real teams (often
> going a step further with a migration tool like Flyway, which isn't
> necessary for this class project).

### 6.4 Verifying the connection

Start your (still mostly empty) Spring Boot app with `./mvnw spring-boot:run`
once you've generated the project — if `application.properties` and the
database are both set up correctly, you'll see Hibernate's startup log lines
and no connection errors. If you see `Failed to configure a DataSource`, see
§18.

---

## 7. Entity Design (full code)

This section gives you complete, working JPA entity classes matching
`database/schema.sql`. Type these in — they're mostly declarative mapping
code, and seeing a fully correct example is the fastest way to learn JPA
annotations. Once you understand `User`, the same patterns repeat for the
other three entities, which are given in more abbreviated form.

### 7.1 What changed vs. the original `models.py`, and why

| Original (`models.py`) | This design | Why |
|---|---|---|
| No DB-level uniqueness on `username`/`email` (`models.py:21,23`) | `UNIQUE` constraint on both | `requirements.md` use case 1 requires both to be unique; the original only had application code implying this, never enforced |
| `comment_ids` string column (`models.py:74`) + manual parsing (`models.py:150-177`) | Real `comments` table, `@OneToMany`/`@ManyToOne` relationship | Lets the database (and JPA) manage the relationship — no string parsing, and cascading delete is automatic |
| `ratings` string column (`models.py:79`) + manual parsing (`models.py:99-120`) | Real `ratings` table with a `UNIQUE(user_id, recipe_id)` constraint | The DB itself now guarantees "one rating per user per recipe"; the original only enforced this by looping over a string at write time |
| `tags` JSON column (`models.py:82`) | Six real columns (`temperature`, `dish_type`, `dairy`, `sweetness`, `meat`, `seafood`) | These are fixed, single-valued categories (see the `RadioField` choices in `forms.py:31-36`) — plain columns are simpler to query/filter than JSON and need no special MySQL JSON handling |
| `created = db.Column(db.DateTime, default=datetime.now())` (`models.py:76,78`) | `@CreationTimestamp` | The original's `default=datetime.now()` is evaluated **once at class-definition time**, so every recipe got the *same* timestamp — the moment the Flask process started. `@CreationTimestamp` sets it correctly per row, at insert time. |
| `User.recipes` relationship commented out (`models.py:25`) | `@OneToMany(..., cascade = CascadeType.ALL, orphanRemoval = true)` actually enabled | Lets `userRepository.delete(user)` cascade-delete their recipes/comments/ratings automatically, instead of the manual loop in the original `delete_profile()` (`routes.py:431-445`) |

### 7.2 `User.java` (fully worked example)

```java
package com.yourname.foodapp.model;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String username;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    // Never call this "password" — it never holds a plaintext password.
    // Set via UserService using a PasswordEncoder (see §10).
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    // mappedBy = "user" means: "the foreign key lives on the Recipe side,
    // in the field called `user`". cascade + orphanRemoval means deleting
    // a User deletes their recipes too — this is what replaces the manual
    // delete loop in the original delete_profile() (routes.py:431-445).
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Recipe> recipes = new HashSet<>();

    // Many-to-many: a user can favorite many recipes, a recipe can be
    // favorited by many users. @JoinTable describes the join table itself
    // (matches database/schema.sql's favorite_recipes table exactly).
    @ManyToMany
    @JoinTable(
        name = "favorite_recipes",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "recipe_id")
    )
    private Set<Recipe> favoriteRecipes = new HashSet<>();

    // JPA requires a no-arg constructor (it uses reflection to build
    // objects when loading from the database). Keep it protected, not
    // public, so application code is nudged toward the "real" constructor
    // below instead.
    protected User() {
    }

    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Set<Recipe> getRecipes() {
        return recipes;
    }

    public Set<Recipe> getFavoriteRecipes() {
        return favoriteRecipes;
    }
}
```

A few things worth understanding, not just copying:
- **`@Id @GeneratedValue(strategy = GenerationType.IDENTITY)`** — the primary
  key, auto-incremented by MySQL itself (`AUTO_INCREMENT` in
  `schema.sql`). This is the direct equivalent of `id = db.Column(db.Integer,
  primary_key=True)`.
- **`@Column(nullable = false, unique = true, length = 32)`** — maps to
  `NOT NULL`, `UNIQUE`, `VARCHAR(32)` respectively. Compare to
  `database/schema.sql`'s `users` table — these must match, since
  `ddl-auto=validate` checks them against each other at startup.
- Getters and a setter for every mutable field, no logic inside them. This
  looks repetitive — later, once you're comfortable, you can explore
  **Lombok** (`@Getter @Setter` on the class) to auto-generate these at
  compile time. Not required for this project; mentioned so you know it
  exists when you see it in other people's Spring code.

### 7.3 `Recipe.java`

```java
package com.yourname.foodapp.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "recipes")
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String title;

    @Lob
    @Column(nullable = false)
    private String description;

    @Lob
    @Column(nullable = false)
    private String ingredients;

    @Lob
    @Column(nullable = false)
    private String instructions;

    // Set automatically by Hibernate on INSERT — fixes the "same timestamp
    // for every row" bug described in §7.1.
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    // FetchType.LAZY: don't load the owning User from the database until
    // something actually calls recipe.getUser() — avoids pulling in user
    // data every time you just want a list of recipes.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 20)
    private String temperature;

    @Column(name = "dish_type", length = 20)
    private String dishType;

    @Column(length = 20)
    private String dairy;

    @Column(length = 20)
    private String sweetness;

    @Column(length = 20)
    private String meat;

    @Column(length = 20)
    private String seafood;

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Rating> ratings = new ArrayList<>();

    protected Recipe() {
    }

    public Recipe(String title, String description, String ingredients,
                  String instructions, User user) {
        this.title = title;
        this.description = description;
        this.ingredients = ingredients;
        this.instructions = instructions;
        this.user = user;
    }

    // TODO: generate getters and setters for every field above with your
    // IDE (IntelliJ: Code → Generate → Getters and Setters, select all).
    // No logic goes in them — this is exactly the same pattern as User.java.

    // One piece of real logic worth writing by hand, since it's the direct
    // replacement for average_rating() in the original models.py:118-120:
    public Double getAverageRating() {
        if (ratings.isEmpty()) {
            return null;
        }
        return ratings.stream()
            .mapToInt(Rating::getValue)
            .average()
            .orElse(0.0);
    }
}
```

### 7.4 `Comment.java`

```java
package com.yourname.foodapp.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "comments")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Lob
    @Column(nullable = false)
    private String comment;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    protected Comment() {
    }

    public Comment(User user, Recipe recipe, String comment) {
        this.user = user;
        this.recipe = recipe;
        this.comment = comment;
    }

    // TODO: generate getters/setters (id, user, recipe, comment, created)
}
```

This replaces `models.py:193-209` — but notice the original's `delete()`
method, which manually patched the parent recipe's `comment_ids` string, has
no equivalent here at all. With a real `@ManyToOne`/`@OneToMany` relationship
and `orphanRemoval = true` on `Recipe.comments`, removing a comment from
`recipe.getComments()` (or calling `commentRepository.delete(comment)`
directly) is enough — there's no derived string state to keep in sync.

### 7.5 `Rating.java`

```java
package com.yourname.foodapp.model;

import jakarta.persistence.*;

@Entity
@Table(name = "ratings", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "recipe_id"})
})
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(nullable = false)
    private int value; // 1–5, validated in RatingForm (see §9), not here

    protected Rating() {
    }

    public Rating(User user, Recipe recipe, int value) {
        this.user = user;
        this.recipe = recipe;
        this.value = value;
    }

    // TODO: generate getters/setters (id, user, recipe, value)
}
```

This is a brand-new entity — the original app had no `Rating` table at all,
just the `ratings` string on `Recipe` (`models.py:79`). See §7.1 for why.

---

## 8. Repository Layer (full code)

Spring Data JPA generates the implementation of these interfaces for you at
startup, purely from the method names (this is called a **derived query
method** — Spring parses `findByUserId` into `WHERE user_id = ?` by
convention). You never write a method body here for the simple cases.

```java
package com.yourname.foodapp.repository;

import com.yourname.foodapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
}
```

```java
package com.yourname.foodapp.repository;

import com.yourname.foodapp.model.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;

public interface RecipeRepository
        extends JpaRepository<Recipe, Long>, JpaSpecificationExecutor<Recipe> {
    List<Recipe> findByUserId(Long userId);
}
```

```java
package com.yourname.foodapp.repository;

import com.yourname.foodapp.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByRecipeId(Long recipeId);
}
```

```java
package com.yourname.foodapp.repository;

import com.yourname.foodapp.model.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RatingRepository extends JpaRepository<Rating, Long> {
    Optional<Rating> findByUserIdAndRecipeId(Long userId, Long recipeId);
}
```

### 8.1 Derived query method naming rules (how Spring parses these)

Spring Data builds the query from the method name using keywords:

| Keyword | Example | Generated WHERE clause |
|---|---|---|
| `findBy<Field>` | `findByEmail(String email)` | `WHERE email = ?` |
| `And` / `Or` | `findByUserIdAndRecipeId(...)` | `WHERE user_id = ? AND recipe_id = ?` |
| `Containing` | `findByTitleContaining(String s)` | `WHERE title LIKE %?%` (this is your equivalent of `Recipe.title.icontains(arg)` from `routes.py:50`) |
| `OrderBy<Field>Desc/Asc` | `findByUserIdOrderByCreatedDesc(...)` | adds `ORDER BY created DESC` |
| `existsBy...` | `existsByEmail(...)` | returns `boolean` instead of the entity |

### 8.2 Dynamic search/filter: `allrecipestags()`

The original `allrecipestags()` route (`routes.py:14-91`) builds up a query
with an unknown subset of filters active (title, temperature, dish_type,
dairy, sweetness, meat, seafood — any combination might be present in the
query string). A derived method name can't express "any subset of these 7
fields, if present" — you have two real options, from simplest to most
scalable:

**Option A — `@Query` with `IS NULL OR`** (fine for a fixed, small set of filters):
```java
@Query("""
    SELECT r FROM Recipe r
    WHERE (:title IS NULL OR r.title LIKE %:title% OR r.ingredients LIKE %:title%)
      AND (:temperature IS NULL OR r.temperature = :temperature)
      AND (:dishType IS NULL OR r.dishType = :dishType)
      AND (:dairy IS NULL OR r.dairy = :dairy)
      AND (:sweetness IS NULL OR r.sweetness = :sweetness)
      AND (:meat IS NULL OR r.meat = :meat)
      AND (:seafood IS NULL OR r.seafood = :seafood)
    """)
List<Recipe> search(String title, String temperature, String dishType,
                     String dairy, String sweetness, String meat, String seafood);
```

**Option B — `JpaSpecificationExecutor`** (already added to
`RecipeRepository` above): build the WHERE clause programmatically in your
service, adding a condition only for filters that are actually present. This
is the more idiomatic Spring Data way to do "N optional filters" and is worth
learning — search "Spring Data JPA Specification example" once you're ready
to implement `RecipeService.search(...)` (§11).

Either is fine for this project; Option A is more approachable if this is
your first time with Spring Data.

---

## 9. DTOs & Validation (full code)

These replace `forms.py`. Unlike WTForms' `FlaskForm`, these are plain Java
classes — the "form" behavior comes entirely from Bean Validation
annotations plus how you bind them in the controller (§12).

```java
package com.yourname.foodapp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class LoginForm {
    @NotBlank @Email(message = "Please enter a valid email address.")
    private String email;

    @NotBlank
    private String password;

    // getters/setters
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
```

> Note: with Spring Security's built-in form login (§10), you typically
> **don't** need a `LoginForm` DTO at all — Spring Security's login filter
> reads the `username`/`password` request parameters directly off the POST
> body before your code ever runs. This class is included for completeness
> and in case you choose to write a custom login flow instead of Spring
> Security's default one.

```java
package com.yourname.foodapp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterForm {
    @NotBlank
    private String username;

    @NotBlank @Email(message = "Please enter a valid email address.")
    private String email;

    @NotBlank @Size(min = 8, message = "Password must be at least 8 characters.")
    private String password;

    // getters/setters for username, email, password
}
```
Note the password minimum length is **8**, matching `requirements.md`'s use
case 1 ("Must be at least 8 characters"), not the original `forms.py:14`'s
`Length(min=4, max=35)` — this is a case where the requirements doc and the
actual Flask code disagreed; follow `requirements.md` in the port.

```java
package com.yourname.foodapp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateProfileForm {
    @NotBlank
    private String username;

    @NotBlank @Email(message = "Please enter a valid email address.")
    private String email;

    // Unlike RegisterForm, leaving this blank should mean "don't change the
    // password" — enforce that in UserService (§11), not here, since "may
    // be blank OR must be 8+ chars" isn't expressible with @NotBlank/@Size
    // alone.
    private String password;

    // getters/setters
}
```

```java
package com.yourname.foodapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RecipeForm {
    @NotBlank @Size(min = 1, max = 80)
    private String title;

    @NotBlank
    private String description;

    @NotBlank
    private String ingredients;

    @NotBlank
    private String instructions;

    // Matches the RadioField choices in the original forms.py:31-36.
    // A plain String is fine to start; once comfortable, consider a Java
    // enum per field instead (more type-safe, catches typos at compile
    // time) — not required for this project.
    private String temperature;
    private String dishType;
    private String dairy;
    private String sweetness;
    private String meat;
    private String seafood;

    // getters/setters for all of the above
}
```

```java
package com.yourname.foodapp.dto;

import jakarta.validation.constraints.NotBlank;

public class CommentForm {
    @NotBlank
    private String comment;

    // getter/setter
}
```

```java
package com.yourname.foodapp.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class RatingForm {
    @Min(1) @Max(5)
    private int rating;

    // getter/setter
}
```

---

## 10. Security (full code)

This is Spring Security's minimum setup for session-based form login — the
direct replacement for `flask_login`'s setup in `app/__init__.py:21-34` and
the `@login_required` decorators sprinkled through `routes.py`. Config code
like this is boilerplate you configure once, so it's given complete; the
comments explain what each piece does.

### 10.1 `AppUserDetails.java`

Spring Security doesn't know about your `User` entity — it works against its
own `UserDetails` interface. This class adapts one to the other (the direct
equivalent of `UserMixin` in `models.py:18`, which did the same job for
Flask-Login).

```java
package com.yourname.foodapp.security;

import com.yourname.foodapp.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class AppUserDetails implements UserDetails {

    private final User user;

    public AppUserDetails(User user) {
        this.user = user;
    }

    // Exposes the wrapped entity so controllers can get back to your
    // domain object, e.g. via @AuthenticationPrincipal AppUserDetails.
    public User getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // No role system in this app — everyone gets one generic authority.
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        // We log in by email (see forms.py's LoginForm), so this returns
        // email even though it's called "username" — that's just Spring
        // Security's interface naming, not a requirement that it be your
        // actual username field.
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() { return true; }
    @Override
    public boolean isAccountNonLocked() { return true; }
    @Override
    public boolean isCredentialsNonExpired() { return true; }
    @Override
    public boolean isEnabled() { return true; }
}
```

### 10.2 `AppUserDetailsService.java`

This is the direct replacement for the `@login.user_loader` callback in
`app/__init__.py:32-34` — Spring Security calls this at login time to look up
the user by whatever identifier they typed into the login form.

```java
package com.yourname.foodapp.security;

import com.yourname.foodapp.model.User;
import com.yourname.foodapp.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("No account with email " + email));
        return new AppUserDetails(user);
    }
}
```

### 10.3 `SecurityConfig.java`

```java
package com.yourname.foodapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Replaces werkzeug.security.generate_password_hash /
        // check_password_hash (used in models.py:35-40).
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Public pages — replaces the routes NOT decorated with
                // @login_required in routes.py (login, register, and
                // viewing a single recipe is public per requirements.md
                // use case 7: "Anyone can view the details of a recipe").
                .requestMatchers("/", "/login", "/register", "/css/**", "/images/**").permitAll()
                // Everything else requires login — replaces @login_required
                // on nearly every other route in routes.py.
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")           // replaces login.login_view = "login" (__init__.py:24)
                .loginProcessingUrl("/login")  // the URL your <form> POSTs to
                .usernameParameter("email")    // your login form's field is named "email", not "username"
                .defaultSuccessUrl("/home", true) // replaces `return redirect('/home')` (routes.py:281)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")          // replaces logout_user() (routes.py:310-313)
                .logoutSuccessUrl("/")
                .permitAll()
            );
        return http.build();
    }
}
```

**What this buys you vs. Flask-Login:** Flask-Login only tracked *who's*
logged in and gave you `@login_required` — it didn't touch CSRF, password
hashing, or session fixation protection; those were separate concerns you
either handled yourself (`generate_password_hash`) or didn't handle at all.
Spring Security bundles all of this: CSRF protection, session fixation
protection, and secure-by-default cookie handling come for free once
`spring-boot-starter-security` is on the classpath and you define a
`SecurityFilterChain` — you don't write that plumbing yourself.

### 10.4 Getting the logged-in user in a controller

Flask's `current_user` (from `flask_login`) becomes an injected parameter:

```java
@GetMapping("/profile")
public String profile(@AuthenticationPrincipal AppUserDetails principal, Model model) {
    User currentUser = principal.getUser();
    // ...
}
```

---

## 11. Service Layer (guided)

This — and §12 — is where you write the actual port. Each method below has a
signature and a comment explaining what to implement and which original code
to reference. Fill in the body yourself.

### 11.1 `UserService.java`

```java
package com.yourname.foodapp.service;

import com.yourname.foodapp.dto.RegisterForm;
import com.yourname.foodapp.dto.UpdateProfileForm;
import com.yourname.foodapp.model.User;
import com.yourname.foodapp.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // TODO: port register() from routes.py:296-307 (the register() view).
    // Steps:
    //   1. Hash form.getPassword() with passwordEncoder.encode(...) —
    //      replaces user.set_password() (models.py:35-36).
    //   2. Build a new User(username, email, hashedPassword).
    //   3. userRepository.save(user).
    //   4. Return the saved user (or void — your choice).
    // Note: requirements.md use case 1 requires rejecting duplicate
    // email/username BEFORE hitting the DB unique constraint, so the
    // controller can show a friendly validation error instead of a raw SQL
    // exception — check userRepository.existsByEmail(...) /
    // existsByUsername(...) first and throw or return an error indicator.
    public User register(RegisterForm form) {
        throw new UnsupportedOperationException("TODO");
    }

    // TODO: port update_profile() from routes.py:401-412.
    // Steps:
    //   1. Load the current user.
    //   2. Set username/email from the form (update_email/update_username
    //      in models.py:42-48 become plain setters now).
    //   3. Only re-hash and set a new password if
    //      form.getPassword() is non-blank (see the comment on
    //      UpdateProfileForm in §9 — this is the one validation rule that
    //      lives in the service, not the DTO).
    //   4. Save.
    public User updateProfile(User currentUser, UpdateProfileForm form) {
        throw new UnsupportedOperationException("TODO");
    }

    // TODO: port delete_profile() from routes.py:431-445.
    // With the cascade relationships set up in §7 (User.recipes has
    // cascade=ALL, orphanRemoval=true; Recipe.comments and Recipe.ratings
    // likewise), this should collapse to a single call:
    //   userRepository.delete(user);
    // — no manual "find all my recipes, find all my comments, delete each"
    // loop needed. If you find yourself writing that loop, your cascade
    // annotations in §7 aren't wired correctly yet — check there first.
    public void deleteAccount(User user) {
        throw new UnsupportedOperationException("TODO");
    }
}
```

### 11.2 `RecipeService.java`

```java
package com.yourname.foodapp.service;

import com.yourname.foodapp.dto.RecipeForm;
import com.yourname.foodapp.model.Comment;
import com.yourname.foodapp.model.Rating;
import com.yourname.foodapp.model.Recipe;
import com.yourname.foodapp.model.User;
import com.yourname.foodapp.repository.CommentRepository;
import com.yourname.foodapp.repository.RatingRepository;
import com.yourname.foodapp.repository.RecipeRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final CommentRepository commentRepository;
    private final RatingRepository ratingRepository;

    public RecipeService(RecipeRepository recipeRepository,
                          CommentRepository commentRepository,
                          RatingRepository ratingRepository) {
        this.recipeRepository = recipeRepository;
        this.commentRepository = commentRepository;
        this.ratingRepository = ratingRepository;
    }

    // TODO: port mysinglerecipeadd() from routes.py:153-184 (the creation
    // half only — comment/rating submission is handled separately, see
    // below). Build a `new Recipe(...)` from the form + the current user,
    // set the six tag fields, save, return it.
    public Recipe create(RecipeForm form, User owner) {
        throw new UnsupportedOperationException("TODO");
    }

    // TODO: port mysinglerecipeedit() from routes.py:188-239.
    // Load the recipe, verify recipe.getUser().getId().equals(currentUser.getId())
    // (replaces the ownership check at routes.py:194) — throw an exception
    // your controller can turn into a 403/redirect+flash if it doesn't
    // match. Then copy every field from the form onto the entity and save.
    public Recipe update(Long recipeId, RecipeForm form, User currentUser) {
        throw new UnsupportedOperationException("TODO");
    }

    // TODO: port mysinglerecipedelete() from routes.py:242-256.
    // Same ownership check as update(). With Recipe's cascade relationships
    // to Comment/Rating set up in §7, recipeRepository.delete(recipe) is
    // enough — no manual delete_all_comments() loop needed (compare to
    // the original models.py:169-177).
    public void delete(Long recipeId, User currentUser) {
        throw new UnsupportedOperationException("TODO");
    }

    // TODO: port the comment-submission half of mysinglerecipeview()
    // (routes.py:124-130). Build a new Comment(user, recipe, text) and
    // save via commentRepository — no need to also call
    // recipe.add_comment_id(...), since that string tracking doesn't exist
    // anymore (see §7.4).
    public Comment addComment(Long recipeId, String text, User author) {
        throw new UnsupportedOperationException("TODO");
    }

    // TODO: port rate_recipe() (models.py:99-116) as a service method
    // instead of a method on the entity. Steps:
    //   1. ratingRepository.findByUserIdAndRecipeId(user.getId(), recipeId)
    //   2. If present, update its value and save (replaces the "update
    //      existing rating" branch, models.py:101-110).
    //   3. If absent, create+save a new Rating (replaces models.py:113-116).
    // This is meaningfully simpler than the original because the
    // UNIQUE(user_id, recipe_id) constraint plus a single lookup replaces
    // manually scanning a delimited string for the user's ID.
    public Rating rate(Long recipeId, int value, User rater) {
        throw new UnsupportedOperationException("TODO");
    }

    // TODO: port allrecipestags() (routes.py:14-91) — the query-building
    // half, not the form-redirect half (that stays in the controller, see
    // §12). Use either the @Query approach or JpaSpecificationExecutor
    // described in §8.2. Treat a null/blank parameter as "no filter on
    // this field", matching the original's `if ("x" in request.args)` checks.
    public List<Recipe> search(String title, String temperature, String dishType,
                                String dairy, String sweetness, String meat,
                                String seafood) {
        throw new UnsupportedOperationException("TODO");
    }

    // TODO: port the recipe-of-the-day logic from home() (routes.py:316-329).
    // Java equivalent of Python's `random.seed(date.today().toordinal())`:
    //   long seed = java.time.LocalDate.now().toEpochDay();
    //   java.util.Random random = new java.util.Random(seed);
    //   List<Recipe> all = recipeRepository.findAll();
    //   Recipe pick = all.get(random.nextInt(all.size()));
    // Handle the empty-list case the same way the original does at
    // routes.py:326-327 (though consider returning null/Optional and
    // letting the template handle "no recipes yet" instead of constructing
    // a fake Recipe with id=-1 — that was a workaround for Jinja2's
    // template needing *some* object; Thymeleaf can branch on null directly
    // with th:if).
    public Recipe recipeOfTheDay() {
        throw new UnsupportedOperationException("TODO");
    }
}
```

### 11.3 Favoriting — where should this logic live?

The original puts `add_favorite`/`remove_favorite` directly on `User`
(`models.py:51-60`). In this design, `User.favoriteRecipes` (§7.2) is a plain
`Set<Recipe>`, so toggling a favorite is just:
```java
currentUser.getFavoriteRecipes().add(recipe);   // or .remove(recipe)
userRepository.save(currentUser);
```
Whether you write a small `UserService.addFavorite(User, Recipe)` wrapper
around that or just do it inline in the controller is a judgment call for a
relationship this simple — either is fine. If you want the practice, add it
to `UserService` following the same pattern as the methods above.

---

## 12. Controllers (guided) & Route Table

### 12.1 Route table

Every route in `routes.py`, mapped to a suggested Spring MVC equivalent.
Resource-oriented URLs (`/recipes/{id}` instead of
`/home/myrecipes/mysinglerecipeview/{id}`) are the conventional Spring MVC
style, so this table cleans the paths up rather than mirroring the originals
exactly — you don't have to use these exact paths, but it's worth adopting
the convention.

| Original Flask route | Suggested Spring MVC endpoint | Notes |
|---|---|---|
| `GET /`, `GET/POST /login` | `GET /login` | `POST /login` is handled entirely by Spring Security's form login filter — you don't write a controller method for it (see §10.3) |
| `GET/POST /register` | `GET /register`, `POST /register` | |
| `GET /logout` | handled by Spring Security's logout filter | configured in §10.3, not a controller method |
| `GET /home` | `GET /home` | recipe-of-the-day |
| `GET/POST /home/allrecipestagspage` | `GET /recipes` | search/filter via query params |
| `GET /home/myrecipes` | `GET /recipes/mine` | |
| `GET /home/following` | `GET /following` | this page exists in the original templates but has no real backing feature/model in `routes.py` — decide whether to implement a real "follow" feature or drop it; it's not in `requirements.md`'s 18 functional requirements |
| `GET /home/myprofile` | `GET /profile` | |
| `GET/POST .../mysinglerecipeview/<id>` | `GET /recipes/{id}` (view), `POST /recipes/{id}/comments` (add comment), `POST /recipes/{id}/ratings` (rate) | the original overloads one route for view+comment-submit+rating-submit; splitting into 3 endpoints is cleaner in Spring MVC |
| `GET/POST .../mysinglerecipeadd` | `GET /recipes/new`, `POST /recipes` | |
| `GET/POST .../mysinglerecipe/<id>/edit` | `GET /recipes/{id}/edit`, `POST /recipes/{id}` | |
| `GET/POST .../mysinglerecipe/<id>/delete` | `POST /recipes/{id}/delete` | plain HTML forms can't send `DELETE` without extra config, so a `POST` to a `/delete` sub-path is the pragmatic choice, same idea as the original |
| `GET/POST /home/random_recipe` | `GET /recipes/random-from-api` | |
| `GET/POST .../mysinglerecipeadd/<meal_id>` | `GET /recipes/import/{mealId}`, `POST /recipes/import/{mealId}` | |
| `GET/POST /home/myprofile/update` | `GET /profile/edit`, `POST /profile` | |
| `.../recipe_id/add_favorite`, `.../remove_favorite` | `POST /recipes/{id}/favorite`, `POST /recipes/{id}/unfavorite` | |
| `GET /home/myprofile/deleteprofile` | `POST /profile/delete` | **the original uses `GET` for a destructive action** — a plain link that deletes your account on click. Don't replicate this; make it a `POST` from a confirm form. |
| `GET /home/myprofile/favorites` | `GET /profile/favorites` | |

### 12.2 `AuthController.java` (fully worked example)

Everything else in this section is a guided stub — this one controller is
given complete, as your pattern to copy for the others.

```java
package com.yourname.foodapp.controller;

import com.yourname.foodapp.dto.RegisterForm;
import com.yourname.foodapp.model.User;
import com.yourname.foodapp.service.UserService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    // Spring Security intercepts POST /login itself (see §10.3) — this
    // method only needs to render the empty login form on GET.
    @GetMapping("/login")
    public String loginPage() {
        return "login"; // resolves to templates/login.html
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("form", new RegisterForm());
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterForm form,
                            BindingResult result,
                            Model model) {
        if (result.hasErrors()) {
            return "register"; // re-render with field errors, same idea as
                                // form.validate_on_submit() returning False
                                // in the original register() (routes.py:299)
        }

        if (userService.emailTaken(form.getEmail())) {
            result.rejectValue("email", "duplicate", "This email is already registered.");
            return "register";
        }
        if (userService.usernameTaken(form.getUsername())) {
            result.rejectValue("username", "duplicate", "This username is already taken.");
            return "register";
        }

        User created = userService.register(form);
        return "redirect:/login";
    }
}
```

This assumes you add `emailTaken`/`usernameTaken` helper methods to
`UserService` wrapping `userRepository.existsByEmail(...)` /
`existsByUsername(...)` — small enough to write yourself following the
pattern in §11.1.

### 12.3 `RecipeController.java` (guided stub)

```java
package com.yourname.foodapp.controller;

import com.yourname.foodapp.dto.CommentForm;
import com.yourname.foodapp.dto.RatingForm;
import com.yourname.foodapp.dto.RecipeForm;
import com.yourname.foodapp.model.User;
import com.yourname.foodapp.security.AppUserDetails;
import com.yourname.foodapp.service.RecipeService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/recipes")
public class RecipeController {

    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    // TODO: GET /recipes — port the query-param handling from
    // allrecipestags() (routes.py:44-91), calling recipeService.search(...)
    // (§11.2). Populate `model` with both the results and the current
    // filter values (so the search form can show what's currently
    // selected, same as form.title_for_search.data = arg does in the
    // original).
    @GetMapping
    public String list(@RequestParam(required = false) String title,
                        @RequestParam(required = false) String temperature,
                        @RequestParam(required = false) String dishType,
                        @RequestParam(required = false) String dairy,
                        @RequestParam(required = false) String sweetness,
                        @RequestParam(required = false) String meat,
                        @RequestParam(required = false) String seafood,
                        Model model) {
        throw new UnsupportedOperationException("TODO");
    }

    // TODO: GET /recipes/mine — port myrecipes() (routes.py:94-98).
    @GetMapping("/mine")
    public String mine(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        throw new UnsupportedOperationException("TODO");
    }

    // TODO: GET /recipes/{id} — port the view half of mysinglerecipeview()
    // (routes.py:113-150), MINUS the comment/rating form submission, which
    // moves to the two methods below. Remember: this route is public per
    // requirements.md use case 7, so `principal` may be null — guard
    // every current_user.* usage (show_buttons, check_favorite in the
    // original) accordingly.
    @GetMapping("/{id}")
    public String view(@PathVariable Long id,
                        @AuthenticationPrincipal AppUserDetails principal,
                        Model model) {
        throw new UnsupportedOperationException("TODO");
    }

    // TODO: POST /recipes/{id}/comments — port the comment half of
    // mysinglerecipeview() (routes.py:124-130). On success, redirect back
    // to GET /recipes/{id} (the Post/Redirect/Get pattern — prevents
    // resubmitting the comment if the user refreshes).
    @PostMapping("/{id}/comments")
    public String addComment(@PathVariable Long id,
                              @Valid @ModelAttribute("commentForm") CommentForm form,
                              BindingResult result,
                              @AuthenticationPrincipal AppUserDetails principal) {
        throw new UnsupportedOperationException("TODO");
    }

    // TODO: POST /recipes/{id}/ratings — port the rating half of
    // mysinglerecipeview() (routes.py:132-133).
    @PostMapping("/{id}/ratings")
    public String rate(@PathVariable Long id,
                        @Valid @ModelAttribute("ratingForm") RatingForm form,
                        BindingResult result,
                        @AuthenticationPrincipal AppUserDetails principal) {
        throw new UnsupportedOperationException("TODO");
    }

    // TODO: GET /recipes/new + POST /recipes — port mysinglerecipeadd()
    // (routes.py:153-184).
    @GetMapping("/new")
    public String newForm(Model model) {
        throw new UnsupportedOperationException("TODO");
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") RecipeForm form,
                          BindingResult result,
                          @AuthenticationPrincipal AppUserDetails principal) {
        throw new UnsupportedOperationException("TODO");
    }

    // TODO: GET /recipes/{id}/edit + POST /recipes/{id} — port
    // mysinglerecipeedit() (routes.py:188-239). Remember the ownership
    // check (routes.py:194) — recipeService.update(...) already enforces
    // it per §11.2, so this method mostly needs to catch whatever
    // exception you chose to throw there and flash+redirect, same as the
    // original's flash("You do not have access to this recipe").
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id,
                            @AuthenticationPrincipal AppUserDetails principal,
                            Model model) {
        throw new UnsupportedOperationException("TODO");
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                          @Valid @ModelAttribute("form") RecipeForm form,
                          BindingResult result,
                          @AuthenticationPrincipal AppUserDetails principal) {
        throw new UnsupportedOperationException("TODO");
    }

    // TODO: POST /recipes/{id}/delete — port mysinglerecipedelete()
    // (routes.py:242-256).
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                          @AuthenticationPrincipal AppUserDetails principal) {
        throw new UnsupportedOperationException("TODO");
    }

    // TODO: POST /recipes/{id}/favorite + /unfavorite — port add_favorite()
    // / remove_favorite() (routes.py:414-429).
    @PostMapping("/{id}/favorite")
    public String favorite(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails principal) {
        throw new UnsupportedOperationException("TODO");
    }

    @PostMapping("/{id}/unfavorite")
    public String unfavorite(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails principal) {
        throw new UnsupportedOperationException("TODO");
    }
}
```

### 12.4 `HomeController.java` (guided stub)

Handles the landing/redirect and the home page's "recipe of the day". Build
this at **M4**.

```java
package com.yourname.foodapp.controller;

import com.yourname.foodapp.model.Recipe;
import com.yourname.foodapp.service.RecipeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final RecipeService recipeService;

    public HomeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    // GET / — the original rendered the login page at "/" (routes.py). With
    // Spring Security's form login (§10.3) owning /login, the cleanest thing
    // for "/" is to send everyone to /home and let Security bounce anonymous
    // users to /login itself. No service call needed.
    @GetMapping("/")
    public String index() {
        return "redirect:/home";
    }

    // TODO: GET /home — port home() (routes.py:316-329).
    // Steps:
    //   1. Recipe pick = recipeService.recipeOfTheDay();   (§11.2)
    //   2. model.addAttribute("recipeOfTheDay", pick);
    //      — pick may be null (no recipes yet); the template guards with
    //        th:if (§13), so you do NOT build a fake id=-1 Recipe like the
    //        original did for Jinja2.
    //   3. return "home";   // templates/home.html
    @GetMapping("/home")
    public String home(Model model) {
        throw new UnsupportedOperationException("TODO");
    }

    // OPTIONAL: GET /home/following — the original renders templates/
    // following.html, but there is no follow model/feature behind it and it
    // is NOT in requirements.md's functional requirements. Either delete this
    // route, or return an empty-state page:
    //   return "following";
    @GetMapping("/home/following")
    public String following(Model model) {
        throw new UnsupportedOperationException("TODO");
    }
}
```

### 12.5 `ProfileController.java` (guided stub)

Profile view, edit, delete, and favorites list. Build this at **M9**.

```java
package com.yourname.foodapp.controller;

import com.yourname.foodapp.dto.UpdateProfileForm;
import com.yourname.foodapp.security.AppUserDetails;
import com.yourname.foodapp.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/profile")
public class ProfileController {

    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    // TODO: GET /profile — port the myprofile() view (routes.py).
    // Put the current user on the model (username, email, and their recipes
    // via user.getRecipes()) and return "profile".
    // Gotcha: user.getRecipes() is a LAZY collection. If the template
    // iterates it, either load it inside a @Transactional service method
    // first, or add a fetch-join query — otherwise
    // LazyInitializationException (§18).
    @GetMapping
    public String profile(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        throw new UnsupportedOperationException("TODO");
    }

    // TODO: GET /profile/edit — render the update form pre-filled with the
    // current username/email (leave the password field blank). Build an
    // UpdateProfileForm, copy in the two fields,
    // model.addAttribute("form", form), return "profile-edit".
    @GetMapping("/edit")
    public String editForm(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        throw new UnsupportedOperationException("TODO");
    }

    // TODO: POST /profile — port update_profile() (routes.py:401-412).
    // Steps:
    //   1. @Valid the form; if result.hasErrors() return "profile-edit".
    //   2. Duplicate check: if the new username/email is taken by SOMEONE
    //      ELSE (compare against principal.getUser()'s current values),
    //      result.rejectValue("username"/"email", "duplicate", "...") and
    //      return "profile-edit". See the UserService.updateProfile
    //      discussion in §11.1 (the "exclude the user's own value" rule).
    //   3. userService.updateProfile(principal.getUser(), form);
    //   4. You just changed the username/email that identifies the logged-in
    //      principal, so the simplest correct move is to force a fresh login:
    //         return "redirect:/logout";
    //      (Re-authenticating in place is possible but not worth it here.)
    @PostMapping
    public String update(@Valid @ModelAttribute("form") UpdateProfileForm form,
                         BindingResult result,
                         @AuthenticationPrincipal AppUserDetails principal) {
        throw new UnsupportedOperationException("TODO");
    }

    // TODO: POST /profile/delete — port delete_profile() (routes.py:431-445).
    // MUST be @PostMapping from a confirm form — the original used GET (a
    // plain link that deletes your account on click); do NOT copy that (§12.1
    // route-table callout).
    // Steps:
    //   1. userService.deleteAccount(principal.getUser());   — cascade
    //      deletes their recipes/comments/ratings (§11.1); no manual loop.
    //   2. Invalidate the session so the now-deleted user isn't still
    //      "logged in":  request.getSession().invalidate();
    //   3. return "redirect:/login";
    @PostMapping("/delete")
    public String delete(@AuthenticationPrincipal AppUserDetails principal,
                         HttpServletRequest request) {
        throw new UnsupportedOperationException("TODO");
    }

    // TODO: GET /profile/favorites — port the myprofile/favorites view
    // (routes.py). model.addAttribute("recipes",
    // principal.getUser().getFavoriteRecipes()) and return "favorites".
    // Same LAZY-collection gotcha as GET /profile above.
    @GetMapping("/favorites")
    public String favorites(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        throw new UnsupportedOperationException("TODO");
    }
}
```

### 12.6 `ExternalRecipeController.java` (guided stub)

Pull a recipe from TheMealDB and import it as the user's own. Depends on
`MealDbClient` (§15). Build this at **M10**.

Note: these paths sit under `/recipes` but this is a *separate* controller
from `RecipeController` — that's fine, two controllers can share a URL
prefix as long as no two methods map the same path+verb. Spring matches
literal segments (`/recipes/random-from-api`) ahead of variable ones
(`/recipes/{id}`), so there's no clash. Full paths are written on each
method here (no class-level `@RequestMapping`) to keep that obvious.

```java
package com.yourname.foodapp.controller;

import com.yourname.foodapp.dto.RecipeForm;
import com.yourname.foodapp.model.Recipe;
import com.yourname.foodapp.security.AppUserDetails;
import com.yourname.foodapp.service.MealDbClient;
import com.yourname.foodapp.service.RecipeService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
public class ExternalRecipeController {

    private final MealDbClient mealDbClient;
    private final RecipeService recipeService;

    public ExternalRecipeController(MealDbClient mealDbClient, RecipeService recipeService) {
        this.mealDbClient = mealDbClient;
        this.recipeService = recipeService;
    }

    // TODO: GET /recipes/random-from-api — port random_recipe()
    // (routes.py:331-338).
    //   1. Map<String,Object> meal = mealDbClient.fetchRandomMeal();   (§15.1)
    //   2. Redirect to the review screen for that meal's id:
    //         return "redirect:/recipes/import/" + meal.get("idMeal");
    @GetMapping("/recipes/random-from-api")
    public String random() {
        throw new UnsupportedOperationException("TODO");
    }

    // TODO: GET /recipes/import/{mealId} — the "review before saving" step;
    // port the form-prefill half of add_API_recipe() (routes.py:340-399,
    // specifically the `initial` dict it builds around routes.py:364-369).
    //   1. Map<String,Object> meal = mealDbClient.fetchMealById(mealId);
    //   2. Build a RecipeForm and set:
    //        title        <- (String) meal.get("strMeal")
    //        instructions <- (String) meal.get("strInstructions")
    //        ingredients  <- mealDbClient.formatIngredients(meal)   (§15.1)
    //        description  <- however the original composed it
    //                        (e.g. strCategory + " / " + strArea)
    //   3. model.addAttribute("form", form);
    //      model.addAttribute("mealId", mealId);   // so the form's POST target knows the id
    //   4. return "recipe-form";   // reuse the SAME template as GET /recipes/new
    @GetMapping("/recipes/import/{mealId}")
    public String importForm(@PathVariable String mealId, Model model) {
        throw new UnsupportedOperationException("TODO");
    }

    // TODO: POST /recipes/import/{mealId} — the save half of add_API_recipe().
    // Once the user has reviewed the pre-filled form this is identical to
    // RecipeController.create():
    //   1. @Valid the form; if result.hasErrors() return "recipe-form".
    //   2. Recipe saved = recipeService.create(form, principal.getUser());
    //   3. return "redirect:/recipes/" + saved.getId();
    // Simplification: you can DELETE this method and just point the review
    // form's th:action at the existing POST /recipes endpoint — only keep a
    // separate handler if you need import-specific behavior.
    @PostMapping("/recipes/import/{mealId}")
    public String importSave(@PathVariable String mealId,
                             @Valid @ModelAttribute("form") RecipeForm form,
                             BindingResult result,
                             @AuthenticationPrincipal AppUserDetails principal) {
        throw new UnsupportedOperationException("TODO");
    }
}
```

### 12.7 Cross-cutting rules for every controller

Apply these to `RecipeController` (§12.3) and the three stubs above alike:

- **Getting the current user.** `@AuthenticationPrincipal AppUserDetails
  principal` is your `current_user` (§10.4). On protected routes SecurityConfig
  guarantees it is non-null. On the one public route (`GET /recipes/{id}`,
  per requirements.md use case 7) it can be `null` — guard every
  `principal.getUser()` there.
- **Post/Redirect/Get.** Every `@PostMapping` that changes data must end with
  `return "redirect:/...";`, never a bare view name — otherwise a browser
  refresh re-submits. The *only* exception is re-rendering a form after
  `result.hasErrors()` (`return "recipe-form";`).
- **Flash messages** (Flask's `flash()`). Add a `RedirectAttributes`
  parameter, call `redirectAttributes.addFlashAttribute("message", "...")`
  before returning the redirect, and read `${message}` in the target
  template.
- **Service exceptions.** The service layer throws `IllegalStateException`
  for "not found" / "not the owner" / "duplicate" (§11). Per controller,
  either catch it and
  `redirectAttributes.addFlashAttribute("error", e.getMessage())` + redirect,
  or let it bubble to a shared `@ControllerAdvice` handler. Catching locally
  is the simpler start.
- **View names.** The `String` you return maps to
  `src/main/resources/templates/<name>.html`. The templates that ship with
  this project (§13) are: `home`, `login`, `register`, `recipe-list`,
  `my-recipes`, `recipe-view`, `recipe-form`, `profile`, `profile-edit`,
  `favorites`, `following`, `error`.
- **Lazy relationships in templates.** Any `FetchType.LAZY` field
  (`recipe.getUser()`, `user.getRecipes()`, `user.getFavoriteRecipes()`,
  `recipe.getComments()`) is fetched on first access, which in a template
  happens *after* your service method returned. In practice this works,
  because Spring Boot leaves **`spring.jpa.open-in-view=true`** on by
  default — it holds the Hibernate session open for the whole request,
  including view rendering. That's why the templates here can safely read
  `recipe.user.username`. Two things to know: (a) it's why you may see a
  `spring.jpa.open-in-view is enabled by default` warning in your startup
  log — that's informational, not a problem for this project; (b) if you
  ever set it to `false`, every lazy access from a template starts throwing
  `LazyInitializationException` (§18), and you'd then need to load what the
  view needs inside a `@Transactional` method or with a `JOIN FETCH` query.
  The `RecipeController.view()` example in §12.8 is written the safe way —
  it hands the template plain values instead of relying on lazy loading.
- **Build order.** Don't write all controllers now — `HomeController` at M4,
  `ProfileController` at M9, `ExternalRecipeController` at M10 (§17).

### 12.8 Model contract: exactly what each template expects

The templates in §13 are already written, so each controller method has a
precise job: put these named attributes on the `Model`, then return the view
name. A missing attribute usually shows up as a blank spot on the page, or a
`SpringEL` error in the console naming the attribute — check this table
first.

| View name | Controller method | Required model attributes |
|---|---|---|
| `login` | `AuthController.loginPage()` | *(none — reads `param.error` / `param.logout`)* |
| `register` | `AuthController.registerForm()` / `.register()` | `form` : `RegisterForm` |
| `home` | `HomeController.home()` | `recipeOfTheDay` : `Recipe` **or `null`** |
| `following` | `HomeController.following()` | *(none)* |
| `recipe-list` | `RecipeController.list()` | `recipes` : `List<Recipe>`, plus `title`, `temperature`, `dishType`, `dairy`, `sweetness`, `meat`, `seafood` (the current filter values, echoed back) |
| `my-recipes` | `RecipeController.mine()` | `recipes` : `List<Recipe>` |
| `recipe-view` | `RecipeController.view()` | `recipe` : `Recipe`, `comments` : `List<Comment>`, `isOwner` : `boolean`, `isFavorite` : `boolean`, `userRating` : `Integer` **or `null`** |
| `recipe-form` | `RecipeController.newForm()` / `.editForm()` / `ExternalRecipeController.importForm()` | `form` : `RecipeForm`, `formAction` : `String`, `heading` : `String` |
| `profile` | `ProfileController.profile()` | `user` : `User`, `recipes` : `List<Recipe>`, `favoriteCount` : `int` |
| `profile-edit` | `ProfileController.editForm()` | `form` : `UpdateProfileForm` |
| `favorites` | `ProfileController.favorites()` | `recipes` : `Set<Recipe>` or `List<Recipe>` |
| `error` | *(none — Spring Boot renders it automatically)* | *(Spring supplies `status`, `error`, `message`, `path`)* |

Three notes on that table:

- **`recipe-form` is one template serving three routes.** It doesn't know
  whether you're creating, editing, or importing — you tell it by setting
  `formAction` (where the form POSTs) and `heading` (what the page says).
  That keeps the branching in Java, where you can read it.
- **`recipe-view` takes plain flags, not entities to dig through.** The
  template never asks "is the current user the owner?" itself, because
  answering that in a template means null-checking an anonymous principal in
  SpringEL. You compute `isOwner` / `isFavorite` / `userRating` in the
  controller, where it's ordinary Java.
- **The comment and rating forms need no model attribute.** They post plain
  `name="comment"` / `name="rating"` parameters, which
  `@ModelAttribute CommentForm` / `RatingForm` still binds automatically. So
  don't add `commentForm` / `ratingForm` to the model — nothing reads them.

#### A fully-worked example: `RecipeController.view()`

This is the method with the most model attributes, so here it is complete —
use it as the pattern for filling in the rest of §12.3's stubs.

```java
@GetMapping("/{id}")
public String view(@PathVariable Long id,
                   @AuthenticationPrincipal AppUserDetails principal,
                   Model model) {

    Recipe recipe = recipeService.findById(id);   // throws if not found

    // This route is PUBLIC (requirements.md use case 7), so principal is
    // null for a logged-out visitor. Everything below has to cope with that.
    User currentUser = (principal != null) ? principal.getUser() : null;

    boolean isOwner = currentUser != null
        && recipe.getUser().getId().equals(currentUser.getId());

    boolean isFavorite = currentUser != null
        && currentUser.getFavoriteRecipes().contains(recipe);

    Integer userRating = null;
    if (currentUser != null) {
        userRating = ratingRepository
            .findByUserIdAndRecipeId(currentUser.getId(), id)
            .map(Rating::getValue)
            .orElse(null);
    }

    model.addAttribute("recipe", recipe);
    model.addAttribute("comments", commentRepository.findByRecipeId(id));
    model.addAttribute("isOwner", isOwner);
    model.addAttribute("isFavorite", isFavorite);
    model.addAttribute("userRating", userRating);
    return "recipe-view";
}
```

Add a `findById(Long)` to `RecipeService` for the first line (it's the same
"load or throw `IllegalStateException`" you already wrote three times inside
`update`/`delete`/`rate` — pull it out into one method and call it from all
four). Reaching `commentRepository`/`ratingRepository` from the controller
as shown is the short path; the tidier version is to expose
`recipeService.commentsFor(id)` / `recipeService.ratingBy(user, id)` and
keep the controller free of repositories.

#### Wiring the three `recipe-form` routes

```java
// GET /recipes/new
model.addAttribute("form", new RecipeForm());
model.addAttribute("formAction", "/recipes");
model.addAttribute("heading", "Share a recipe");
return "recipe-form";

// GET /recipes/{id}/edit — copy the entity's fields onto a fresh form
RecipeForm form = new RecipeForm();
form.setTitle(recipe.getTitle());
form.setDescription(recipe.getDescription());
form.setIngredients(recipe.getIngredients());
form.setInstructions(recipe.getInstructions());
form.setTemperature(recipe.getTemperature());
form.setDishType(recipe.getDishType());
form.setDairy(recipe.getDairy());
form.setSweetness(recipe.getSweetness());
form.setMeat(recipe.getMeat());
form.setSeafood(recipe.getSeafood());
model.addAttribute("form", form);
model.addAttribute("formAction", "/recipes/" + id);
model.addAttribute("heading", "Edit your recipe");
return "recipe-form";
```

**Important:** whenever a POST fails validation and you `return
"recipe-form";` to re-render, you must re-add `formAction` and `heading`
too — they aren't automatically carried over from the GET, and without them
the form posts to the wrong URL (or the page fails to render). Spring
re-adds `form` for you because it's the `@ModelAttribute`, but nothing else.

#### The tag values must match in two places

The six tag dropdowns appear in both `recipe-form.html` (what gets saved)
and `recipe-list.html` (what you can filter by). They currently offer:

| Field | Options |
|---|---|
| `temperature` | Hot, Cold |
| `dishType` | Appetizer, Main Course, Side, Dessert, Snack, Drink |
| `dairy` | Dairy, Dairy-Free |
| `sweetness` | Sweet, Savory |
| `meat` | Meat, Vegetarian |
| `seafood` | Seafood, No Seafood |

If these don't match your original `forms.py` `RadioField` choices, change
them — but change them in **both** templates, or filtering silently returns
nothing (the saved value never equals the filtered value). All options must
also stay under 20 characters, since `schema.sql` declares these columns
`VARCHAR(20)`.

---

## 13. Thymeleaf Templates (already written for you)

The front end is **already built** and lives in
`src/main/resources/templates/`. It's Bootstrap 5.3 (from a CDN) plus a warm
kitchen theme in `src/main/resources/static/css/app.css`. Your job in §12 is
to write controllers that hand these templates the model attributes they
expect (§12.8) — not to write HTML.

Read §13.1 to understand the Thymeleaf syntax you'll see in them, then §13.2
for what each file is and how they fit together.

### 13.1 Jinja2 → Thymeleaf, side by side

| Jinja2 (`app/templates/*.html`) | Thymeleaf equivalent |
|---|---|
| `{{ variable }}` | `<span th:text="${variable}"></span>` |
| `{% for x in list %}...{% endfor %}` | `<tr th:each="x : ${list}">...</tr>` |
| `{% if condition %}...{% endif %}` | `<div th:if="${condition}">...</div>` |
| `{% extends "base.html" %}` + `{% block content %}` (`base.html:7`) | **Inverted:** Jinja2 pages *extend* a base that pulls their content in; Thymeleaf pages *pull pieces in* with `th:replace="~{fragments/layout :: navbar}"`. There's no "extends" here — see §13.2 |
| `url_for('static', filename='styles.css')` (`base.html:4`) | `th:href="@{/styles.css}"` |
| `url_for('endpoint', id=x)` | `th:href="@{/recipes/{id}(id=${x})}"` |
| `{{ form.title(...) }}` (WTForms rendering) | `<input th:field="*{title}">`, bound to whatever object the template's top-level `th:object` points at |
| `{{ form.title.errors }}` | `<span th:errors="*{title}"></span>` |
| `{% if current_user.is_authenticated %}` | `<div sec:authorize="isAuthenticated()">` (needs the `thymeleaf-extras-springsecurity6` dependency from §4) |

Crucially: **Thymeleaf templates are valid HTML.** `th:*` attributes are
ignored by a browser opening the file directly (they just don't do anything
without the Thymeleaf engine processing them) — this means you can open a
template as a static mockup even without the server running, which is handy
once we get to the design pass.

### 13.2 The templates in this project

Twelve files under `src/main/resources/templates/`, plus one stylesheet:

| File | Rendered by | Purpose |
|---|---|---|
| `fragments/layout.html` | *(never directly)* | Shared pieces — see below |
| `login.html` | `GET /login` | Email + password, `?error` / `?logout` banners |
| `register.html` | `GET`/`POST /register` | Bound to `RegisterForm` |
| `home.html` | `GET /home` | Recipe-of-the-day hero + quick links |
| `recipe-list.html` | `GET /recipes` | Search box + 6 tag filters + card grid |
| `my-recipes.html` | `GET /recipes/mine` | The user's own recipes |
| `recipe-view.html` | `GET /recipes/{id}` | Full recipe, comments, star rating, favorite/edit/delete |
| `recipe-form.html` | `GET /recipes/new`, `GET /recipes/{id}/edit`, `GET /recipes/import/{mealId}` | One form for create, edit **and** import |
| `profile.html` | `GET /profile` | Profile card, stats, own recipes, delete-account |
| `profile-edit.html` | `GET /profile/edit` | Bound to `UpdateProfileForm` |
| `favorites.html` | `GET /profile/favorites` | Favorited recipes |
| `following.html` | `GET /home/following` | Honest "not built" empty state |
| `error.html` | *(automatic)* | Replaces Spring's Whitelabel error page |
| `static/css/app.css` | *(linked from `head`)* | The warm theme layered over Bootstrap |

Every template opens with an HTML comment listing its exact model contract —
read that comment before writing the controller method that renders it. The
same contracts are collected in the table at §12.8.

#### How the shared layout works

There's no `base.html` to extend (this project doesn't use the
thymeleaf-layout-dialect — it isn't in `pom.xml`). Instead
`fragments/layout.html` defines seven named fragments, and each page pulls
in the ones it wants:

```html
<head th:replace="~{fragments/layout :: head('My profile')}"></head>
<body>
  <nav th:replace="~{fragments/layout :: navbar}"></nav>
  <div th:replace="~{fragments/layout :: flash}"></div>

  <main> ...this page's own content... </main>

  <footer th:replace="~{fragments/layout :: footer}"></footer>
  <th:block th:replace="~{fragments/layout :: scripts}"></th:block>
</body>
```

| Fragment | What it gives you |
|---|---|
| `head(pageTitle)` | `<title>`, Google Fonts, Bootstrap CSS, Bootstrap Icons, `app.css` |
| `navbar` | Brand, nav links, and a login/logout area that switches on `sec:authorize` |
| `flash` | Renders `${message}` (green) and `${error}` (red) — your `flash()` replacement |
| `stars(rating)` | Read-only ★★★☆☆ display; handles a `null` average |
| `recipeCard(recipe)` | One recipe card, used by four different list pages |
| `footer` | Site footer |
| `scripts` | Bootstrap's JS bundle (needed for dropdowns and modals) |

`th:replace` *replaces* the host tag with the fragment; `th:insert` would
nest it inside. These fragments are written for `th:replace` — the empty
host tags above are just placeholders that disappear.

To loop cards, pass each item into the fragment:
```html
<div class="row g-4">
  <div th:each="r : ${recipes}" th:replace="~{fragments/layout :: recipeCard(${r})}"></div>
</div>
```

#### Things in these templates worth understanding

- **CSRF is automatic, but only with `th:action`.** Every `<form>` here uses
  `th:action="@{...}"`, which makes Spring Security inject the hidden CSRF
  token — the equivalent of Flask-WTF's `{{ form.csrf_token }}`. A plain
  `action="/recipes"` gets you a **403 on POST** (§18).
- **Logout is a POST form, not a link.** Spring Security 6+ CSRF-protects
  logout, so `<a href="/logout">` returns 405. The navbar wraps it in a tiny
  form styled as a dropdown item.
- **`sec:authorize` is your `current_user.is_authenticated`.** Used as
  `sec:authorize="isAuthenticated()"` and `sec:authorize="isAnonymous()"`.
  The navbar also prints the current username with
  `sec:authentication="principal.user.username"` — that reaches through
  `AppUserDetails.getUser()`, which is exactly why that getter exists (§10.1).
- **Multi-line text keeps its line breaks via CSS, not `th:utext`.**
  Ingredients and instructions render inside `style="white-space: pre-line"`
  with plain `th:text`. Using `th:utext` would render the text unescaped and
  hand you a stored-XSS hole, since recipe bodies are user input.
- **The star rating picker is CSS-only.** Five radio inputs written 5→1 in
  the DOM, flipped visually with `flex-direction: row-reverse`, so the
  `input:checked ~ label` sibling selector can light up every star to the
  left. No JavaScript.
- **Bootstrap comes from a CDN**, so the app needs an internet connection to
  look right. To go offline later, download `bootstrap.min.css` /
  `bootstrap.bundle.min.js` into `static/` and change the two links in the
  `head` and `scripts` fragments.
- **`th:if="${#lists.isEmpty(recipes)}"`** drives every empty state. Each
  list page has a designed "nothing here yet" panel — so a controller that
  correctly returns an empty list still produces a good-looking page.

#### Making them your own

Almost all of the visual identity is CSS custom properties at the top of
`static/css/app.css`:

```css
:root {
    --cream: #fdf7f0;      /* page background */
    --terracotta: #c9603f; /* primary buttons, links, brand */
    --honey: #e0a458;      /* stars, secondary accents */
    --sage: #7d9471;       /* tag pills */
    --cocoa: #43342a;      /* body text */
}
```

Change those five and the whole app changes with them. Headings use
*Fraunces*, body text uses *Nunito* — both loaded in the `head` fragment.

---

## 14. Static Resources

- The app's stylesheet already lives at
  `src/main/resources/static/css/app.css`, linked from the `head` fragment as
  `<link rel="stylesheet" th:href="@{/css/app.css}">` (this replaces
  `url_for('static', filename='styles.css')` in `base.html:4`). You do **not**
  need to port the old `styles.css` — the new theme supersedes it.
- Move `app/static/*.png` → `src/main/resources/static/images/`, then
  reference them as `th:src="@{/images/whatever.png}"`.
- Spring Boot serves everything under `src/main/resources/static/` at the web
  root automatically — no route needed, same as Flask's implicit `/static/`
  handling.
- **But Spring Security still guards those URLs.** `SecurityConfig` (§10.3)
  permits `/css/**` and `/images/**`, which is why the stylesheet loads on
  the login page while you're logged out. If you add a new static folder
  (say `static/js/`), add `/js/**` to `.requestMatchers(...).permitAll()` or
  it will 302-redirect to the login page and silently not load.

---

## 15. External API Integration (TheMealDB)

Original code (`routes.py:331-399`) uses Python's `requests` library to call
`https://www.themealdb.com/api/json/v1/1/random.php` and
`.../lookup.php?i={id}`, then manually pulls `strIngredient1..20` /
`strMeasure1..20` pairs into a formatted ingredients string.

### 15.1 `MealDbClient.java`

```java
package com.yourname.foodapp.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MealDbClient {

    private final RestClient restClient = RestClient.create("https://www.themealdb.com/api/json/v1/1");

    // Deserializing into a Map keeps this simple to start — TheMealDB's
    // response shape (strIngredient1..20, strMeasure1..20 as flat fields)
    // doesn't map cleanly to a tidy Java class anyway. You can introduce a
    // proper `Meal` record later if you want stronger typing.
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchRandomMeal() {
        Map<String, Object> response = restClient.get()
            .uri("/random.php")
            .retrieve()
            .body(Map.class);
        List<Map<String, Object>> meals = (List<Map<String, Object>>) response.get("meals");
        return meals.get(0);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchMealById(String mealId) {
        Map<String, Object> response = restClient.get()
            .uri("/lookup.php?i={id}", mealId)
            .retrieve()
            .body(Map.class);
        List<Map<String, Object>> meals = (List<Map<String, Object>>) response.get("meals");
        return meals.get(0);
    }

    // TODO: port the ingredient-formatting loop from routes.py:350-361.
    // TheMealDB's fields are named strIngredient1..strIngredient20 /
    // strMeasure1..strMeasure20, so loop i from 1 to 20, look up
    // meal.get("strIngredient" + i) / meal.get("strMeasure" + i) from the
    // Map returned above, and build the same "amount name" lines the
    // original does, skipping blank ingredient names.
    public String formatIngredients(Map<String, Object> meal) {
        throw new UnsupportedOperationException("TODO");
    }
}
```

`RestClient` (used above) is the modern Spring 6.1+/Boot 3.2+ way to make
HTTP calls — a synchronous, fluent replacement for the older `RestTemplate`.
If your course material teaches `RestTemplate` instead, the equivalent call
is:
```java
RestTemplate restTemplate = new RestTemplate();
Map<String, Object> response = restTemplate.getForObject(url, Map.class);
```
Either is fine; `RestClient` is what new Spring code should prefer going
forward.

### 15.2 Wiring it into a controller

`ExternalRecipeController` (§12.4) calls `mealDbClient.fetchRandomMeal()` /
`fetchMealById(...)`, builds a `RecipeForm` pre-filled the same way
`routes.py:364-369` builds its `initial` dict, and passes it to the same
`recipes/new`-style template so the user can review/edit before saving —
follow `add_API_recipe()` (`routes.py:340-399`) step by step.

---

## 16. Testing

Spring Boot Test (`spring-boot-starter-test`, included by Initializr by
default) gives you JUnit 5 + Mockito + `MockMvc` out of the box.

### 16.1 A worked example: testing `RecipeService.rate()`

```java
package com.yourname.foodapp.service;

import com.yourname.foodapp.model.Rating;
import com.yourname.foodapp.model.Recipe;
import com.yourname.foodapp.model.User;
import com.yourname.foodapp.repository.RatingRepository;
import com.yourname.foodapp.repository.RecipeRepository;
import com.yourname.foodapp.repository.CommentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

    @Mock RecipeRepository recipeRepository;
    @Mock CommentRepository commentRepository;
    @Mock RatingRepository ratingRepository;
    @InjectMocks RecipeService recipeService;

    @Test
    void firstTimeRatingCreatesNewRow() {
        User user = new User("alice", "alice@example.com", "hash");
        Recipe recipe = new Recipe("Soup", "desc", "ingr", "instr", user);

        when(ratingRepository.findByUserIdAndRecipeId(any(), any())).thenReturn(Optional.empty());

        recipeService.rate(1L, 5, user);

        verify(ratingRepository).save(any(Rating.class));
    }

    // TODO: add a second test covering the "user already rated — update,
    // don't insert" branch, mirroring the two branches in the original
    // rate_recipe() (models.py:99-116).
}
```

This kind of test (`@ExtendWith(MockitoExtension.class)`, mock the
repositories, test the service in isolation) is the direct analog of
mocking `db.session` in a Flask/pytest test — you're testing your business
logic without needing a real database.

### 16.2 `@WebMvcTest` for controllers, `@SpringBootTest` for full integration

- `@WebMvcTest(RecipeController.class)` + `MockMvc` — tests a controller in
  isolation (mock the service layer), roughly analogous to Flask's test
  client hitting a route.
- `@SpringBootTest` — full-stack integration test against a real (or
  Testcontainers-managed) database, if you want to go that far.

This app didn't have automated tests in the Flask version, so this is
optional — but it's the natural next step once the core features work.

---

## 17. Suggested Build Order (Milestones)

Build in this order — each milestone is runnable/demoable on its own, and
later milestones depend on earlier ones. **Run the app after every
milestone** — don't write three milestones' worth of code before running it
for the first time.

1. **M0 — Scaffold:** Generate the project from Spring Initializr (§4), run
   `database/init-db.sh` (§6.2), confirm `./mvnw spring-boot:run` boots with
   no entities yet and no DB errors.
2. **M1 — User + Registration:** `User` entity (§7.2), `UserRepository`
   (§8), `RegisterForm` (§9), `AuthController.register()` (§12.2, given in
   full). Confirm a row appears in the `users` table after submitting the
   register form.
3. **M2 — Security:** `AppUserDetails`, `AppUserDetailsService`,
   `SecurityConfig` (§10, given in full). Confirm visiting a protected page
   while logged out redirects to `/login`, and that logging in with a
   registered user's credentials works.
4. **M3 — Recipe CRUD (own recipes only):** `Recipe` entity (§7.3),
   `RecipeRepository`, `RecipeForm`, the create/edit/delete/view methods on
   `RecipeController` (§12.3). Ownership checks in `RecipeService`.
5. **M4 — Home & listing:** `HomeController.home()` (recipe-of-the-day),
   `RecipeController.list()` with no filters applied yet (just "all
   recipes").
6. **M5 — Search & tag filtering:** flesh out `RecipeController.list()` +
   `RecipeService.search()` with the query-param filters (§8.2, §11.2).
7. **M6 — Comments:** `Comment` entity (§7.4), `CommentForm`,
   `RecipeController.addComment()`.
8. **M7 — Ratings:** `Rating` entity (§7.5), `RatingForm`,
   `RecipeController.rate()`.
9. **M8 — Favorites:** `User.favoriteRecipes` (already in §7.2's `User`
   code), favorite/unfavorite endpoints, favorites list page.
10. **M9 — Profile management:** `UserService.updateProfile()`/
    `deleteAccount()`, `ProfileController`.
11. **M10 — External API:** `MealDbClient` (§15), `ExternalRecipeController`.
12. **M11 — Front-end polish:** the templates in §13.2 are already written
    and styled, so there's no build step here — this milestone is about
    tuning: adjust the five CSS custom properties in `static/css/app.css` to
    taste, fix the tag option lists if they don't match your original
    `forms.py` choices (§12.8), and click every page looking for an empty
    state or error path that reads badly.
13. **M12 — Tests & cleanup:** add tests (§16) for the trickiest logic
    (rating upsert, tag search, cascade delete).

---

## 18. Troubleshooting: Common Beginner Errors

**Whitelabel Error Page (generic Spring error page, no stack trace shown)**
Usually means either no controller matched the URL (typo in `@GetMapping`
path, or you forgot the class-level `@RequestMapping("/recipes")` prefix on
`RecipeController`), or a template file is missing/misnamed. Check the
application's console log — the real exception (with stack trace) is always
printed there even when the browser just shows the generic page.

**`Failed to configure a DataSource: 'url' attribute is not specified`**
`application.properties` is missing, misnamed, or not on the classpath
(must be exactly `src/main/resources/application.properties`), or MySQL
isn't running (`brew services list` to check).

**`Schema-validation: missing table [recipes]` (or similar) at startup**
You're using `ddl-auto=validate` (§6.3) but haven't run
`database/init-db.sh` yet against the database your app is pointed at, or
your `@Entity`'s `@Table(name = "...")` doesn't match the actual table name
in `schema.sql`.

**403 Forbidden on a POST request, even though you're logged in**
Almost always a missing CSRF token — check your `<form>` tag uses
`th:action` (not a plain `action="..."` attribute); only `th:action` triggers
the automatic CSRF token injection from the Thymeleaf-Spring-Security
integration (§4, §13.2's note on this).

**`org.hibernate.LazyInitializationException: could not initialize proxy - no Session`**
You tried to access a `FetchType.LAZY` relationship (e.g. `recipe.getUser()`)
*after* the database session/transaction already closed — typically because
you accessed it from the Thymeleaf template instead of the controller/service
layer, after the request's transaction ended. Fix by making sure any lazy
relationship you need in the view is already loaded (accessed at least once)
while still inside the `@Service`/`@Transactional` method, not lazily
triggered from inside the template.

**`No qualifying bean of type '...' available`**
Spring couldn't find something to inject. Usually means you forgot
`@Service`/`@Repository`/`@Component` on a class, or the class lives outside
the package Spring scans from (everything must be under (or below) the
package containing your `@SpringBootApplication` class).

**Port 8080 already in use**
Another instance of your app (or something else) is already running on that
port. Stop it, or set `server.port=8081` in `application.properties`.

**Circular reference / infinite loop when printing an entity (`toString()`)**
If you ever add `toString()` to `User`/`Recipe`/`Comment` (by hand or via
Lombok's `@Data`, which generates one), be careful: `User` → `recipes` →
`Recipe.user` → back to `User` is a cycle, and a naive generated `toString()`
will recurse forever. Either exclude relationship fields from `toString()`,
or don't generate one for entities with bidirectional relationships. (This
doesn't come up if you stick to plain field getters/setters as written in §7,
only if you introduce Lombok's `@Data` later.)

---

## 19. Reference Docs

- Spring Boot reference docs: https://docs.spring.io/spring-boot/reference/
- Spring Data JPA: https://docs.spring.io/spring-data/jpa/reference/
- Spring Security: https://docs.spring.io/spring-security/reference/
- Bean Validation (Jakarta): https://beanvalidation.org/
- Thymeleaf: https://www.thymeleaf.org/documentation.html
- MySQL 8.0 reference: https://dev.mysql.com/doc/refman/8.0/en/
- Baeldung (task-oriented Spring tutorials — search e.g. "Baeldung Spring
  Data JPA Specifications", "Baeldung Spring Security form login"):
  https://www.baeldung.com/
