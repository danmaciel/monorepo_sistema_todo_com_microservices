#!/usr/bin/env python3
"""Smoke test over the public API Gateway, without printing credentials."""
import json
import base64
import hashlib
import hmac
import os
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


def check_gateway_cors():
    """Check allowed and disallowed browser preflight requests at the Gateway."""
    for origin, allowed in (
        ("http://localhost:5173", True),
        ("http://localhost:3000", True),
        ("https://untrusted.example.invalid", False),
    ):
        request = urllib.request.Request(
            BASE + "/api/users/login",
            method="OPTIONS",
            headers={
                "Origin": origin,
                "Access-Control-Request-Method": "POST",
                "Access-Control-Request-Headers": "content-type",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=15) as response:
                status, headers = response.status, response.headers
        except urllib.error.HTTPError as error:
            status, headers = error.code, error.headers
            error.read()

        exposed_origin = headers.get("Access-Control-Allow-Origin")
        if allowed:
            assert 200 <= status < 300, f"allowed preflight rejected: HTTP {status}"
            assert exposed_origin == origin, "Gateway must echo the explicitly allowed origin"
            assert headers.get("Access-Control-Allow-Credentials") != "true", (
                "credentialed browser requests must remain disabled until CSRF design is complete"
            )
        else:
            assert exposed_origin != origin, "Gateway reflected a disallowed origin"


def signed_test_token(user_id, *, expired=False, issuer=None, audience=None):
    """Sign a JWT with disposable CI credentials; never print it."""
    secret = os.environ.get("JWT_SECRET", "")
    if len(secret.encode("utf-8")) < 32:
        raise AssertionError("E2E requires JWT_SECRET (at least 32 UTF-8 bytes)")

    def encoded_json(obj):
        raw = json.dumps(obj, separators=(",", ":")).encode("utf-8")
        return base64.urlsafe_b64encode(raw).rstrip(b"=")

    now = int(time.time())
    claims = {
        "sub": user_id,
        "iat": now - 7200 if expired else now,
        "exp": now - 3600 if expired else now + 3600,
    }
    if issuer is not None:
        claims["iss"] = issuer
    if audience is not None:
        claims["aud"] = audience

    header = encoded_json({"alg": "HS256", "typ": "JWT"})
    payload = encoded_json(claims)
    signing_input = header + b"." + payload
    signature = hmac.new(secret.encode("utf-8"), signing_input, hashlib.sha256).digest()
    signature = base64.urlsafe_b64encode(signature).rstrip(b"=")
    return (signing_input + b"." + signature).decode("ascii")


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

    check_gateway_cors()

    suffix = uuid.uuid4().hex[:10]
    password = "Ci-Test-" + uuid.uuid4().hex
    users = []
    user_ids = []
    for index in range(2):
        username = f"e2e_{suffix}_{index}"
        status, registered = call("POST", "/api/users/register", {
            "username": username,
            "email": username + "@example.invalid",
            "password": password,
        })
        expect(201, status, f"register user {index}")
        assert registered and registered.get("token"), "register must return JWT"
        assert registered.get("user", {}).get("id"), "register must return user ID"
        user_ids.append(registered["user"]["id"])
        status, logged = call("POST", "/api/users/login", {
            "username": username,
            "password": password,
        })
        expect(200, status, f"login user {index}")
        assert logged and logged.get("token"), "login must return JWT"
        users.append(logged["token"])

    # Reserved refresh/logout routes must never become usable by accident.
    # They are deliberately disabled in CI and no cookie-based HTTP flow exists.
    for reserved in ("/api/users/refresh", "/api/users/logout"):
        status, _ = call("POST", reserved)
        assert status in (401, 403, 404), (
            f"reserved session endpoint unexpectedly exposed: {reserved}, HTTP {status}"
        )

    # User profiles are private: the path ID must match the authenticated account.
    status, own_profile = call("GET", "/api/users/me", token=users[0])
    expect(200, status, "read own profile through /me")
    assert own_profile["id"] == user_ids[0]

    status, same_profile = call("GET", "/api/users/" + user_ids[0], token=users[0])
    expect(200, status, "read own profile through /users/{id}")
    assert same_profile["id"] == user_ids[0]

    status, _ = call("GET", "/api/users/" + user_ids[0], token=users[1])
    expect(403, status, "deny reading another user's email/profile")

    status, own_exists = call("GET", "/api/users/exists/" + user_ids[0], token=users[0])
    expect(200, status, "check own account existence")
    assert own_exists is True

    status, _ = call("GET", "/api/users/exists/" + user_ids[0], token=users[1])
    expect(403, status, "deny cross-user account existence query")

    status, _ = call("GET", "/api/users/exists/" + user_ids[0])
    assert status in (401, 403), f"anonymous account lookup must be denied, got HTTP {status}"

    status, _ = call("GET", "/api/users/" + user_ids[0])
    assert status in (401, 403), f"anonymous profile access must be denied, got HTTP {status}"

    # Both services must reject an authentically signed token whose exp is in the past.
    expired_token = signed_test_token(user_ids[0], expired=True)
    status, _ = call("GET", "/api/users/me", token=expired_token)
    assert status in (401, 403), f"user-service accepted expired signed JWT: HTTP {status}"

    status, _ = call("GET", "/api/todos", token=expired_token)
    assert status in (401, 403), f"todo-service accepted expired signed JWT: HTTP {status}"

    # Correctly signed but wrong issuer/audience must be denied by both services.
    for invalid_token in (
        signed_test_token(user_ids[0], issuer="other-service", audience="todo-api"),
        signed_test_token(user_ids[0], issuer="todo-user-service", audience="other-api"),
        signed_test_token(user_ids[0], issuer="todo-user-service"),
    ):
        for path in ("/api/users/me", "/api/todos"):
            status, _ = call("GET", path, token=invalid_token)
            assert status in (401, 403), (
                f"{path} accepted a signed JWT with invalid issuer/audience: HTTP {status}"
            )

    # E2E runs both services in strict mode. Properly signed legacy JWTs
    # without issuer/audience must be rejected, even before they expire.
    legacy_token = signed_test_token(user_ids[0])
    for path in ("/api/users/me", "/api/todos"):
        status, _ = call("GET", path, token=legacy_token)
        assert status in (401, 403), (
            f"{path} accepted a legacy JWT while strict validation is required: HTTP {status}"
        )

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
    print("E2E PASSED: private user profiles, auth, task owner isolation and CRUD")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"E2E FAILED: {error}", file=sys.stderr)
        sys.exit(1)
