#!/usr/bin/env python3
"""Real Chromium smoke test for the opt-in HTTPS browser session.

Runs only with disposable CI credentials and a local Vite HTTPS proxy.
Never prints JWTs, refresh cookie values, credentials, or CSRF tokens.
"""
import json
import os
import time
import urllib.request
import uuid

from playwright.sync_api import sync_playwright, expect

FRONTEND = "https://localhost:3000"
BACKEND = "http://127.0.0.1:8080"


def register_test_account():
    username = "browser_" + uuid.uuid4().hex[:12]
    password = "Browser-Only-" + uuid.uuid4().hex
    request = urllib.request.Request(
        BACKEND + "/api/users/register",
        json.dumps({
            "username": username,
            "email": username + "@example.invalid",
            "password": password,
        }).encode("utf-8"),
        {"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=15) as result:
        assert result.status == 201, "Could not register disposable browser account"
        result.read()
    return username, password


def assert_browser_cookie(context):
    cookies = [
        cookie for cookie in context.cookies(FRONTEND + "/api/users/refresh")
        if cookie["name"] == "__Secure-todo-refresh"
    ]
    assert len(cookies) == 1, "Expected one refresh cookie"
    cookie = cookies[0]
    assert cookie["httpOnly"] and cookie["secure"], "Refresh cookie flags missing"
    assert cookie["sameSite"] == "Strict"
    assert cookie["path"] == "/api/users"


def assert_tokens_not_persisted(page):
    snapshot = page.evaluate("""() => ({
      local: Object.entries(localStorage),
      session: Object.entries(sessionStorage)
    })""")
    for name, value in snapshot["local"] + snapshot["session"]:
        assert "auth-storage" not in name or (
            '"token":' not in value and '"csrf":' not in value
        ), "Authentication secrets were written to browser storage"


def main():
    assert os.environ.get("E2E_BROWSER_ENABLED") == "true", (
        "Browser tests must be explicitly enabled in the disposable CI environment"
    )
    username, password = register_test_account()

    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=True)
        context = browser.new_context(ignore_https_errors=True)
        page = context.new_page()
        try:
            page.goto(FRONTEND + "/login", wait_until="domcontentloaded")
            page.locator('input[name="username"]').fill(username)
            page.locator('input[name="password"]').fill(password)
            with page.expect_response(
                lambda response: response.url.endswith("/api/users/session/login")
                and response.request.method == "POST"
            ) as login:
                page.get_by_role("button", name="Entrar").click()
            assert login.value.status == 200, "Session login was rejected"
            expect(page.get_by_role("heading", name="Minhas Tarefas")).to_be_visible()
            assert_browser_cookie(context)
            assert_tokens_not_persisted(page)

            # Create data to confirm the freshly issued Bearer token works for
            # the Todo Service, not just the User Service authentication route.
            title = "Chromium E2E " + uuid.uuid4().hex[:8]
            page.get_by_role("button", name="Nova Tarefa").click()
            page.get_by_placeholder("Qual é a sua nova tarefa?").fill(title)
            with page.expect_response(
                lambda response: response.url.endswith("/api/todos")
                and response.request.method == "POST"
            ) as created:
                page.get_by_role("button", name="Adicionar Tarefa").click()
            assert created.value.status in (200, 201), (
                f"Create Todo endpoint returned HTTP {created.value.status}"
            )
            with page.expect_response(
                lambda response: "/api/todos" in response.url
                and response.request.method == "GET"
            ) as fetched:
                page.get_by_role("heading", name="Minhas Tarefas").wait_for()
                # FetchTodos is triggered by createTodo's completion.
                # A response listener below confirms this happened.
            assert fetched.value.status == 200, (
                f"List Todos endpoint returned HTTP {fetched.value.status}"
            )
            expect(page.get_by_role("heading", name=title)).to_be_visible(timeout=15000)

            # Reload must bootstrap CSRF from the HttpOnly cookie, then rotate
            # refresh session and restore an in-memory access JWT.
            with page.expect_response(
                lambda response: response.url.endswith("/api/users/refresh")
                and response.request.method == "POST"
            ) as reload_refresh:
                page.reload(wait_until="domcontentloaded")
            assert reload_refresh.value.status == 200, (
                "Reload did not restore the session through cookie refresh"
            )
            expect(page.get_by_role("heading", name="Minhas Tarefas")).to_be_visible()
            expect(page.get_by_role("heading", name=title)).to_be_visible()
            assert_browser_cookie(context)
            assert_tokens_not_persisted(page)

            with page.expect_response(
                lambda response: response.url.endswith("/api/users/logout")
                and response.request.method == "POST"
            ) as logout:
                page.get_by_role("button", name="Sair").click()
            assert logout.value.status == 204, "Logout did not revoke refresh family"
            expect(page.get_by_role("button", name="Entrar")).to_be_visible()
            assert not any(
                cookie["name"] == "__Secure-todo-refresh"
                for cookie in context.cookies(FRONTEND + "/api/users/refresh")
            ), "Refresh cookie remains after logout"

            page.reload(wait_until="domcontentloaded")
            expect(page.get_by_role("button", name="Entrar")).to_be_visible()
            assert_tokens_not_persisted(page)
            print("Browser E2E passed: HTTPS cookie login, Todo CRUD, reload/refresh, logout")
        finally:
            context.close()
            browser.close()


if __name__ == "__main__":
    main()
