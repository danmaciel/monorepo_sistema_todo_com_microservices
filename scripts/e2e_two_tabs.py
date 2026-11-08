#!/usr/bin/env python3
"""Two-tab baseline: stale CSRF after another tab rotates a shared refresh cookie.

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

            # First tab still has the original CSRF in memory, while the
            # shared cookie was rotated by second tab. This must be rejected
            # before the refresh-family replay handler; it is not a fix.
            with first.expect_response(
                lambda response: response.url.endswith("/api/users/logout")
                and response.request.method == "POST"
            ) as stale_logout:
                first.get_by_role("button", name="Sair").click()
            assert stale_logout.value.status == 403, (
                "Expected fail-closed rejection of stale CSRF in first tab"
            )
            expect(first.get_by_role("button", name="Entrar")).to_be_visible()

            # CSRF denial must NOT revoke the surviving session family.
            with second.expect_response(
                lambda response: response.url.endswith("/api/users/refresh")
                and response.request.method == "POST"
            ) as survivor_refresh:
                second.reload(wait_until="domcontentloaded")
            assert survivor_refresh.value.status == 200, (
                "Stale-CSRF rejection unexpectedly broke the surviving session"
            )
            expect(second.get_by_role("heading", name="Minhas Tarefas")).to_be_visible()
            assert_tokens_not_persisted(first)
            assert_tokens_not_persisted(second)

            # End the disposable session via the tab with current CSRF.
            with second.expect_response(
                lambda response: response.url.endswith("/api/users/logout")
                and response.request.method == "POST"
            ) as logout:
                second.get_by_role("button", name="Sair").click()
            assert logout.value.status == 204
            print("Two-tab baseline passed: stale CSRF denied, other tab remains valid")
        finally:
            context.close()
            browser.close()


if __name__ == "__main__":
    main()
