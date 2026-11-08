#!/usr/bin/env python3
"""Browser checks for the opt-in Web Locks session refresh path.

Two pages in one context share a cookie but not JS state. Both must recover
a session without replaying a consumed refresh token. This exercises real
browser coordination but does not deterministically force simultaneous POSTs.
"""
import os
import uuid

from playwright.sync_api import expect, sync_playwright

from e2e_browser import FRONTEND, assert_browser_cookie, assert_tokens_not_persisted, register_test_account


def main():
    assert os.environ.get("E2E_BROWSER_ENABLED") == "true", "Disposable CI only"
    username, password = register_test_account()
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=True)
        context = browser.new_context(ignore_https_errors=True)
        try:
            login_tab = context.new_page()
            login_tab.goto(FRONTEND + "/login", wait_until="domcontentloaded")
            login_tab.locator('input[name="username"]').fill(username)
            login_tab.locator('input[name="password"]').fill(password)
            with login_tab.expect_response(
                lambda r: r.url.endswith("/api/users/session/login") and r.request.method == "POST"
            ) as login:
                login_tab.get_by_role("button", name="Entrar").click()
            assert login.value.status == 200
            expect(login_tab.get_by_role("heading", name="Minhas Tarefas")).to_be_visible()

            statuses = []
            def collect_refresh(response):
                if response.url.endswith("/api/users/refresh") and response.request.method == "POST":
                    statuses.append(response.status)
            context.on("response", collect_refresh)

            # Start navigations with commit rather than waiting for the React
            # bootstrap, allowing their HTTP refreshes to overlap in practice.
            first = context.new_page()
            second = context.new_page()
            first.goto(FRONTEND + "/", wait_until="commit")
            second.goto(FRONTEND + "/", wait_until="commit")
            expect(first.get_by_role("heading", name="Minhas Tarefas")).to_be_visible(timeout=20000)
            expect(second.get_by_role("heading", name="Minhas Tarefas")).to_be_visible(timeout=20000)
            assert len(statuses) == 2 and all(s == 200 for s in statuses), (
                "Two tabs must bootstrap and rotate without 401/403"
            )
            assert_browser_cookie(context)
            assert_tokens_not_persisted(first)
            assert_tokens_not_persisted(second)

            # Reload the earliest tab after the shared cookie rotated twice.
            # The CSRF must be bootstrapped while holding the same Web Lock.
            with first.expect_response(
                lambda r: r.url.endswith("/api/users/refresh") and r.request.method == "POST"
            ) as rotated:
                first.reload(wait_until="domcontentloaded")
            assert rotated.value.status == 200
            expect(first.get_by_role("heading", name="Minhas Tarefas")).to_be_visible()

            # Do not log session material. End using a fresh bootstrap in the
            # latest tab; the existing logout path still has stale-CSRF limits.
            with first.expect_response(
                lambda r: r.url.endswith("/api/users/logout") and r.request.method == "POST"
            ) as logout:
                first.get_by_role("button", name="Sair").click()
            assert logout.value.status == 204
            print("Web Locks browser refresh passed: two tabs and reload")
        finally:
            context.close()
            browser.close()


if __name__ == "__main__":
    main()
