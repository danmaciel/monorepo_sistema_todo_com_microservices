#!/usr/bin/env python3
"""E2E fault injection: authenticated Todo CRUD survives User Service downtime.

Requires the disposable Docker Compose environment and running API Gateway.
Does not print tokens or passwords. Restarts User Service after the test.
"""
import subprocess
import sys
import uuid
import urllib.error

from e2e_gateway import call, expect


def compose(*args):
    subprocess.run(["docker", "compose", *args], check=True, stdout=subprocess.DEVNULL)


def register(label):
    suffix = uuid.uuid4().hex
    username = "outage_" + label + "_" + suffix
    status, body = call("POST", "/api/users/register", {
        "username": username,
        "email": username + "@example.invalid",
        "password": "Local-Ci-" + uuid.uuid4().hex,
    })
    expect(201, status, "register " + label)
    assert body and body.get("token") and body.get("user", {}).get("id")
    return body["token"]


def main():
    first = register("owner")
    second = register("other")
    status, task = call("POST", "/api/todos", {"title": "Survives User Service outage"}, token=first)
    expect(201, status, "create task before outage")
    task_id = task["id"]

    try:
        compose("stop", "user-service")

        # An unreachable upstream may return a gateway 5xx or reach the
        # client's 15-second timeout. Neither means the API remained usable.
        try:
            status, _ = call("GET", "/api/users/me", token=first)
            assert 500 <= status <= 599, (
                "User Service should be unavailable during the failure scenario; got HTTP " + str(status)
            )
        except (TimeoutError, urllib.error.URLError):
            pass

        status, owner_task = call("GET", "/api/todos/" + task_id, token=first)
        expect(200, status, "read own task while User Service is down")
        assert owner_task["id"] == task_id

        status, _ = call("GET", "/api/todos/" + task_id, token=second)
        expect(403, status, "deny cross-user read during outage")

        status, changed = call(
            "PATCH", "/api/todos/" + task_id + "/complete", token=first
        )
        expect(200, status, "complete own task during outage")
        assert changed["completed"] is True

        status, created = call(
            "POST", "/api/todos", {"title": "Created during User Service outage"}, token=first
        )
        expect(201, status, "create own task during outage")

        status, listing = call("GET", "/api/todos", token=first)
        expect(200, status, "list own tasks during outage")
        ids = {item["id"] for item in listing["todos"]}
        assert {task_id, created["id"]}.issubset(ids)

        status, _ = call("DELETE", "/api/todos/" + created["id"], token=first)
        expect(204, status, "delete own task during outage")
    finally:
        compose("start", "user-service")

    print("OUTAGE E2E PASSED: Todo CRUD and owner isolation survived User Service downtime")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("OUTAGE E2E FAILED: " + str(error), file=sys.stderr)
        sys.exit(1)
