#!/usr/bin/env bash
#
# Initializes the local MySQL database for the FOODpApp Spring Boot migration:
#   - creates the database (default: foodapp)
#   - creates a dedicated, non-root app user (default: foodapp_user)
#   - applies database/schema.sql
#
# Usage:
#   ./database/init-db.sh
#
# Optional overrides (environment variables):
#   DB_NAME             database name              (default: foodapp)
#   DB_USER             app's MySQL username        (default: foodapp_user)
#   DB_PASSWORD         app's MySQL password        (prompted if not set)
#   MYSQL_ROOT_USER      MySQL admin user to connect as (default: root)
#
# You will be prompted for the MySQL admin (root) password interactively —
# this script never hardcodes it and never passes it as a command-line flag
# (which would be visible to other local processes via `ps`); it's exported
# as MYSQL_PWD for the duration of the mysql calls only.

set -euo pipefail

DB_NAME="${DB_NAME:-foodapp}"
DB_USER="${DB_USER:-foodapp_user}"
MYSQL_ROOT_USER="${MYSQL_ROOT_USER:-root}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCHEMA_FILE="$SCRIPT_DIR/schema.sql"

# Basic sanity check on identifiers before they're interpolated into SQL below.
if [[ ! "$DB_NAME" =~ ^[A-Za-z0-9_]+$ ]]; then
    echo "DB_NAME must contain only letters, numbers, and underscores." >&2
    exit 1
fi
if [[ ! "$DB_USER" =~ ^[A-Za-z0-9_]+$ ]]; then
    echo "DB_USER must contain only letters, numbers, and underscores." >&2
    exit 1
fi

if ! command -v mysql >/dev/null 2>&1; then
    echo "mysql client not found on PATH." >&2
    echo "Install MySQL first, e.g.: brew install mysql && brew services start mysql" >&2
    exit 1
fi

if [ ! -f "$SCHEMA_FILE" ]; then
    echo "Could not find schema.sql next to this script ($SCHEMA_FILE)." >&2
    exit 1
fi

if [ -z "${DB_PASSWORD:-}" ]; then
    read -r -s -p "Choose a password for new MySQL user '${DB_USER}': " DB_PASSWORD
    echo
    read -r -s -p "Confirm password: " DB_PASSWORD_CONFIRM
    echo
    if [ "$DB_PASSWORD" != "$DB_PASSWORD_CONFIRM" ]; then
        echo "Passwords did not match. Aborting." >&2
        exit 1
    fi
fi

echo "Connecting to MySQL as admin user '${MYSQL_ROOT_USER}'..."
read -r -s -p "MySQL '${MYSQL_ROOT_USER}' password (leave blank if none): " MYSQL_ROOT_PASSWORD
echo

export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
trap 'unset MYSQL_PWD' EXIT

echo "Creating database '${DB_NAME}' and user '${DB_USER}'..."
mysql -u "$MYSQL_ROOT_USER" <<SQL
CREATE DATABASE IF NOT EXISTS ${DB_NAME} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASSWORD}';
ALTER USER '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASSWORD}';
GRANT ALL PRIVILEGES ON ${DB_NAME}.* TO '${DB_USER}'@'localhost';
FLUSH PRIVILEGES;
SQL

echo "Applying schema.sql..."
mysql -u "$MYSQL_ROOT_USER" "$DB_NAME" < "$SCHEMA_FILE"

unset MYSQL_PWD
trap - EXIT

echo
echo "Done. '${DB_NAME}' is ready with tables: users, recipes, comments, ratings, favorite_recipes."
echo
echo "Add this to your Spring Boot project's src/main/resources/application.properties:"
echo
echo "  spring.datasource.url=jdbc:mysql://localhost:3306/${DB_NAME}?useSSL=false&serverTimezone=UTC"
echo "  spring.datasource.username=${DB_USER}"
echo "  spring.datasource.password=<the password you just entered>"
echo "  spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver"
echo "  spring.jpa.hibernate.ddl-auto=validate"
echo "  spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect"
