#!/usr/bin/env python3
"""Smoke test over the public API Gateway, without printing credentials."""
import json
import sys
import time
import urllib.error
import urllib.request
import uuid

BASE = "http://127.0.0.1:8080"


def call(method, path, data=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    encoded = json.dumps(data).encode("utf-8") if data is not None else None
    request = urllib.request.Request(BASE + path, encoded, headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        error.read()
        return error.code, None


def expect(status, actual, message):
    if status != actual:
        raise AssertionError(f"{message}: expected HTTP {status}, got {actual}")


def main():
    # The gateway endpoint must be available before running the scenario.
    for attempt in range(90):
        try:
            result, _ = call("GET", "/api/todos")
            if result in (401, 403):
                break
        except (urllib.error.URLError, TimeoutError):
            pass
        time.sleep(3)
    else:
        raise AssertionError("Gateway/Todo Service did not become available")

    suffix = uuid.uuid4().hex[:10]
    password = "Ci-Test-" + uuid.uuid4().hex
    users = []
    for index in range(2):
        username = f"e2e_{suffix}_{index}"
        status, registered = call("POST", "/api/users/register", {
            "username": username,
            "email": username + "@example.invalid",
            "password": password,
        })
        expect(201, status, f"register user {index}")
        assert registered and registered.get("token"), "register must return JWT"
        status, logged = call("POST", "/api/users/login", {
            "username": username,
            "password": password,
        })
        expect(200, status, f"login user {index}")
        assert logged and logged.get("token"), "login must return JWT"
        users.append(logged["token"])

    status, _ = call("POST", "/api/todos", {
        "title": "Anonymous attempt",
    })
    assert status in (401, 403), f"anonymous task creation must be denied, got HTTP {status}"

    status, _ = call("GET", "/api/todos", token="invalid.jwt.token")
    assert status in (401, 403), f"invalid JWT must be denied, got HTTP {status}"

    status, created = call("POST", "/api/todos", {
        "title": "Verify gateway integration",
        "description": "Created during a disposable CI run",
    }, token=users[0])
    expect(201, status, "create todo")
    todo_id = created["id"]

    status, fetched = call("GET", "/api/todos/" + todo_id, token=users[0])
    expect(200, status, "read own todo")
    assert fetched["id"] == todo_id

    status, _ = call("GET", "/api/todos/" + todo_id, token=users[1])
    expect(403, status, "reject other user's todo")

    # A second user cannot discover or modify the first user's tasks.
    status, other_list = call("GET", "/api/todos", token=users[1])
    expect(200, status, "list second user's tasks")
    assert other_list is not None
    assert all(todo["id"] != todo_id for todo in other_list.get("todos", [])), (
        "other user's task must not appear in the list"
    )

    status, _ = call("PUT", "/api/todos/" + todo_id, {
        "title": "Unauthorized change",
    }, token=users[1])
    expect(403, status, "reject cross-user task update")

    status, _ = call("PATCH", "/api/todos/" + todo_id + "/complete", token=users[1])
    expect(403, status, "reject cross-user task completion")

    status, _ = call("PATCH", "/api/todos/" + todo_id + "/uncomplete", token=users[1])
    expect(403, status, "reject cross-user task reopening")

    status, updated = call("PATCH", "/api/todos/" + todo_id + "/complete", token=users[0])
    expect(200, status, "complete own todo")
    assert updated["completed"] is True

    status, _ = call("DELETE", "/api/todos/" + todo_id, token=users[1])
    expect(403, status, "reject cross-user deletion")

    status, _ = call("DELETE", "/api/todos/" + todo_id, token=users[0])
    expect(204, status, "delete own todo")
    print("E2E PASSED: auth, owner isolation for list/read/update/complete/delete, CRUD")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"E2E FAILED: {error}", file=sys.stderr)
        sys.exit(1)
