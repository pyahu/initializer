-- The two roles of the database guide, step 4: [[dbName]]_migrator owns the schema, [[dbName]]_app reads and
-- writes data. Run it once per database, as an admin, with passwords from your secret store:
--
--   psql -v ON_ERROR_STOP=1 -v migrator_password=... -v app_password=... -d [[dbName]] -f db/roles.sql
--
-- Docker Compose and the tests run it too, with local passwords (db/init-local.sh).

CREATE ROLE [[dbName]]_migrator LOGIN PASSWORD :'migrator_password';

-- The connection budget (step 1): a pool of 5 times 8 pods at most (HPA maximum 6, one surge, one draining).
CREATE ROLE [[dbName]]_app LOGIN PASSWORD :'app_password' CONNECTION LIMIT 40;

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
CREATE SCHEMA [[dbName]] AUTHORIZATION [[dbName]]_migrator;
GRANT USAGE ON SCHEMA [[dbName]] TO [[dbName]]_app;
ALTER DEFAULT PRIVILEGES FOR ROLE [[dbName]]_migrator IN SCHEMA [[dbName]]
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO [[dbName]]_app;
ALTER DEFAULT PRIVILEGES FOR ROLE [[dbName]]_migrator IN SCHEMA [[dbName]]
    GRANT USAGE, SELECT ON SEQUENCES TO [[dbName]]_app;

-- Server side timeouts on every connection of the runtime role (step 3).
ALTER ROLE [[dbName]]_app IN DATABASE :"DBNAME" SET search_path = [[dbName]];
ALTER ROLE [[dbName]]_app IN DATABASE :"DBNAME" SET statement_timeout = '15s';
ALTER ROLE [[dbName]]_app IN DATABASE :"DBNAME" SET idle_in_transaction_session_timeout = '60s';
ALTER ROLE [[dbName]]_migrator IN DATABASE :"DBNAME" SET search_path = [[dbName]];
