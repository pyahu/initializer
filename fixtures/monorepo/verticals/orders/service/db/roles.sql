-- The two roles of the database guide, step 4: orders_migrator owns the schema, orders_app reads and
-- writes data. Run it once per database, as an admin, with passwords from your secret store:
--
--   psql -v ON_ERROR_STOP=1 -v migrator_password=... -v app_password=... -d orders -f db/roles.sql
--
-- Docker Compose and the tests run it too, with local passwords (db/init-local.sh).

CREATE ROLE orders_migrator LOGIN PASSWORD :'migrator_password';

-- The connection budget (step 1): a pool of 5 times 8 pods at most (HPA maximum 6, one surge, one draining).
CREATE ROLE orders_app LOGIN PASSWORD :'app_password' CONNECTION LIMIT 40;

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
CREATE SCHEMA orders AUTHORIZATION orders_migrator;
GRANT USAGE ON SCHEMA orders TO orders_app;
ALTER DEFAULT PRIVILEGES FOR ROLE orders_migrator IN SCHEMA orders
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO orders_app;
ALTER DEFAULT PRIVILEGES FOR ROLE orders_migrator IN SCHEMA orders
    GRANT USAGE, SELECT ON SEQUENCES TO orders_app;

-- The first Liquibase changeset removes INSERT/UPDATE/DELETE on DATABASECHANGELOG and DATABASECHANGELOGLOCK
-- from the runtime role after those tables exist. SELECT remains available for migration readiness.

-- Server side timeouts on every connection of the runtime role (step 3).
ALTER ROLE orders_app IN DATABASE :"DBNAME" SET search_path = orders;
ALTER ROLE orders_app IN DATABASE :"DBNAME" SET statement_timeout = '15s';
ALTER ROLE orders_app IN DATABASE :"DBNAME" SET idle_in_transaction_session_timeout = '60s';
ALTER ROLE orders_migrator IN DATABASE :"DBNAME" SET search_path = orders;
