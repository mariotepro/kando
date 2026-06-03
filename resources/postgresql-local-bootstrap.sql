CREATE USER kando WITH PASSWORD 'kando';
CREATE DATABASE kando OWNER kando;

\connect kando

ALTER DATABASE kando OWNER TO kando;
GRANT USAGE, CREATE ON SCHEMA public TO kando;
