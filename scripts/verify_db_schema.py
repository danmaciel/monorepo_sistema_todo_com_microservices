#!/usr/bin/env python3
"""Read-only schema checks for the two disposable PostgreSQL databases.

Uses Docker Compose and information_schema only; does not print credentials,
change records, or validate migration compatibility with historical volumes.
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

CONTRACTS = {
    "user-db": {
        "database": "usersdb",
        "user": "users_app",
        "required": {
            ("users", "id"): ("uuid", "NO"),
            ("users", "username"): ("character varying", "NO"),
            ("users", "email"): ("character varying", "NO"),
            ("users", "password"): ("character varying", "NO"),
            ("users", "enabled"): ("boolean", "NO"),
            ("refresh_sessions", "id"): ("uuid", "NO"),
            ("refresh_sessions", "user_id"): ("uuid", "NO"),
            ("refresh_sessions", "family_id"): ("uuid", "NO"),
            ("refresh_sessions", "token_fingerprint"): ("character", "NO"),
            ("refresh_sessions", "expires_at"): ("timestamp with time zone", "NO"),
        },
        "forbidden": {"todos"},
    },
    "todo-db": {
        "database": "todosdb",
        "user": "todos_app",
        "required": {
            ("todos", "id"): ("uuid", "NO"),
            ("todos", "title"): ("character varying", "NO"),
            ("todos", "user_id"): ("uuid", "NO"),
            ("todos", "completed"): ("boolean", "NO"),
        },
        "forbidden": {"users", "refresh_sessions"},
    },
}


def inspect(service, config):
    command = [
        "docker", "compose", "exec", "-T", service,
        "psql", "-X", "-v", "ON_ERROR_STOP=1", "-U", config["user"],
        "-d", config["database"], "-At", "-F", "|", "-c", SQL,
    ]
    result = subprocess.run(command, capture_output=True, text=True, timeout=30)
    if result.returncode != 0:
        raise RuntimeError("Database inspection unavailable for " + service)
    actual = {}
    for line in result.stdout.splitlines():
        parts = line.split("|")
        if len(parts) != 4:
            raise AssertionError("Unexpected schema inspection output in " + service)
        table, column, data_type, nullable = parts
        actual[(table, column)] = (data_type, nullable)
    wrong = [
        table + "." + column
        for (table, column), expected in config["required"].items()
        if actual.get((table, column)) != expected
    ]
    if wrong:
        raise AssertionError(service + ": missing/incompatible columns: " + ", ".join(sorted(wrong)))
    unexpected = {table for table, _ in actual} & config["forbidden"]
    if unexpected:
        raise AssertionError(service + ": unexpected other-service tables: " + ", ".join(sorted(unexpected)))
    print(f"SCHEMA CONTRACT PASSED: {service} ({len(config['required'])} required columns; isolated tables)")


def main():
    for service, config in CONTRACTS.items():
        inspect(service, config)


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, AssertionError, subprocess.TimeoutExpired) as error:
        print("SCHEMA CONTRACT FAILED: " + str(error), file=sys.stderr)
        sys.exit(1)
