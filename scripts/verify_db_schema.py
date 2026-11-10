#!/usr/bin/env python3
"""Read-only PostgreSQL schema contract check for the local Docker Compose demo.

No DDL, inserts, updates or deletes. Does not print credentials or row data.
It intentionally checks a minimum contract rather than claiming an exact,
production-ready schema or solving the migration from Hibernate ddl-auto.
"""
import subprocess
import sys

SQL = """
SELECT table_name, column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name IN ('users', 'todos', 'refresh_sessions')
ORDER BY table_name, ordinal_position;
"""

REQUIRED = {
    ("users", "id"): ("uuid", "NO"),
    ("users", "username"): ("character varying", "NO"),
    ("users", "email"): ("character varying", "NO"),
    ("users", "password"): ("character varying", "NO"),
    ("users", "enabled"): ("boolean", "NO"),
    ("todos", "id"): ("uuid", "NO"),
    ("todos", "title"): ("character varying", "NO"),
    ("todos", "user_id"): ("uuid", "NO"),
    ("todos", "completed"): ("boolean", "NO"),
    ("refresh_sessions", "id"): ("uuid", "NO"),
    ("refresh_sessions", "user_id"): ("uuid", "NO"),
    ("refresh_sessions", "family_id"): ("uuid", "NO"),
    ("refresh_sessions", "token_fingerprint"): ("character", "NO"),
    ("refresh_sessions", "expires_at"): ("timestamp with time zone", "NO"),
}

def main():
    command = [
        "docker", "compose", "exec", "-T", "postgres",
        "psql", "-X", "-v", "ON_ERROR_STOP=1",
        "-U", "todouser", "-d", "tododb", "-At", "-F", "|",
        "-c", SQL,
    ]
    result = subprocess.run(command, capture_output=True, text=True, timeout=30)
    if result.returncode != 0:
        raise RuntimeError("PostgreSQL schema inspection failed; check Docker Compose database readiness")
    actual = {}
    for line in result.stdout.splitlines():
        parts = line.split("|")
        if len(parts) != 4:
            raise AssertionError("Unexpected schema inspection output")
        table, column, data_type, nullable = parts
        actual[(table, column)] = (data_type, nullable)

    wrong = [
        (table, column) for (table, column), expected in REQUIRED.items()
        if actual.get((table, column)) != expected
    ]
    if wrong:
        names = ", ".join(f"{table}.{column}" for table, column in sorted(wrong))
        raise AssertionError("Required schema columns missing or type/nullability differs: " + names)
    print(f"SCHEMA CONTRACT PASSED: {len(REQUIRED)} required columns verified (read-only)")

if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, AssertionError, subprocess.TimeoutExpired) as error:
        print("SCHEMA CONTRACT FAILED: " + str(error), file=sys.stderr)
        sys.exit(1)
