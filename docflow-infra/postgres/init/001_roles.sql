-- ============================================================
-- Security / Audit DB roles
-- Runs ONCE at initial DB creation
-- ============================================================

-- Read-only role for auditors / SOC
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'audit_ro') THEN
        CREATE ROLE audit_ro;
    END IF;
END$$;

-- Login role for external SOC team
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'soc_ro') THEN
        CREATE ROLE soc_ro
            LOGIN
            PASSWORD 'postgres'
            NOSUPERUSER
            NOCREATEDB
            NOCREATEROLE
            NOINHERIT;
    END IF;
END$$;

-- SOC inherits audit permissions
GRANT audit_ro TO soc_ro;

-- Allow connection
GRANT CONNECT ON DATABASE current_database() TO audit_ro;

-- Allow schema usage (no write)
GRANT USAGE ON SCHEMA public TO audit_ro;
