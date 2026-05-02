-- Creates both service databases and grants the application user access.
-- The application user (libuser) is created by MariaDB from the
-- MYSQL_USER / MYSQL_PASSWORD env vars set in docker-compose.yml.

CREATE DATABASE IF NOT EXISTS userdb;
CREATE DATABASE IF NOT EXISTS bookdb;

GRANT ALL PRIVILEGES ON userdb.* TO 'libuser'@'%';
GRANT ALL PRIVILEGES ON bookdb.* TO 'libuser'@'%';
FLUSH PRIVILEGES;
