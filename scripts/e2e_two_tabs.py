#!/usr/bin/env python3
"""Two-tab logout: stale CSRF is recovered before revoking shared session.

Only for disposable HTTPS CI. This deliberately exercises a known limitation,
NOT concurrent refresh coordination. Never log credentials, cookies or tokens.
"""
import os

from playwright.sync_api import expect, sync_playwright

from e2e_browser import (
    FRONTEND,
    assert_browser_cookie,
    assert_tokens_not_persisted,
    register_test_account,
)


def main():
    assert os.environ.get("E2E_BROWSER_ENABLED") == "true", (
        "Two-tab test is restricted to disposable CI"
    )
    username, password = register_test_account()
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=True)
        context = browser.new_context(ignore_https_errors=True)
        try:
            first = context.new_page()
            first.goto(FRONTEND + "/login", wait_until="domcontentloaded")
            first.locator('input[name="username"]').fill(username)
            first.locator('input[name="password"]').fill(password)
            with first.expect_response(
                lambda response: response.url.endswith("/api/users/session/login")
                and response.request.method == "POST"
            ) as login:
                first.get_by_role("button", name="Entrar").click()
            assert login.value.status == 200, "First tab could not log in"
            expect(first.get_by_role("heading", name="Minhas Tarefas")).to_be_visible()
            assert_browser_cookie(context)

            # Opening another page in the SAME browser context shares cookies,
            # but gives it an independent JS heap and CSRF state.
            second = context.new_page()
            with second.expect_response(
                lambda response: response.url.endswith("/api/users/refresh")
                and response.request.method == "POST"
            ) as second_refresh:
                second.goto(FRONTEND + "/", wait_until="domcontentloaded")
            assert second_refresh.value.status == 200, (
                "Second tab could not restore session through refresh"
            )
            expect(second.get_by_role("heading", name="Minhas Tarefas")).to_be_visible()
            assert_browser_cookie(context)

            # First tab has old in-memory CSRF, but logout now obtains the
            # current CSRF under the same cross-tab lock used by refresh.
            with first.expect_response(
                lambda response: response.url.endswith("/api/users/logout")
                and response.request.method == "POST"
            ) as logout:
                first.get_by_role("button", name="Sair").click()
            assert logout.value.status == 204, (
                "Logout should revoke the current refresh family despite stale tab state"
            )
            expect(first.get_by_role("button", name="Entrar")).to_be_visible()
            assert not any(
                cookie["name"] == "__Secure-todo-refresh"
                for cookie in context.cookies(FRONTEND + "/api/users/refresh")
            ), "Refresh cookie remains after cross-tab logout"

            # The other tab can retain an earlier access JWT in memory:
            # revoking refresh does not immediately revoke bearer JWTs.
            # On reload, bootstrap must fail without a valid refresh cookie.
            with second.expect_response(
                lambda response: response.url.endswith("/api/users/session/csrf")
                and response.request.method == "POST"
            ) as denied_bootstrap:
                second.reload(wait_until="domcontentloaded")
            assert denied_bootstrap.value.status in (401, 403), (
                "Second tab unexpectedly restored a revoked refresh session"
            )
            expect(second.get_by_role("button", name="Entrar")).to_be_visible()
            assert_tokens_not_persisted(first)
            assert_tokens_not_persisted(second)
            print("Two-tab logout passed: stale tab revokes current refresh family")
        finally:
            context.close()
            browser.close()


if __name__ == "__main__":
    main()
