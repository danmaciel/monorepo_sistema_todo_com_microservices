#!/usr/bin/env python3
"""Verify actual HTTP traces were exported to the local Zipkin instance."""
import json
import sys
import time
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:9411"
EXPECTED = {"api-gateway", "user-service", "todo-service"}


def get(path):
    with urllib.request.urlopen(BASE + path, timeout=8) as response:
        return json.load(response)


def main():
    last = "Zipkin not responding"
    for _ in range(40):
        try:
            names = set(get("/api/v2/services"))
            missing = EXPECTED - names
            if not missing:
                print("TRACING PASSED: Zipkin received spans from " + ", ".join(sorted(EXPECTED)))
                return
            last = "no traces received from " + ", ".join(sorted(missing))
        except (urllib.error.URLError, TimeoutError, ValueError) as exc:
            last = str(exc)
        time.sleep(3)
    raise AssertionError(last)


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print("TRACING FAILED: " + str(exc), file=sys.stderr)
        sys.exit(1)
