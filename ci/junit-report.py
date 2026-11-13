#!/usr/bin/env python3
"""Print failed unit tests as GitHub check annotations.

Used by CI when the gradle console output does not show the assertion details,
because logs and artefacts cannot always be downloaded from the runner.
"""

from __future__ import annotations

import glob
import sys
import xml.etree.ElementTree as ET

PATTERNS = [
    "app/build/test-results/*/*.xml",
    "app/build/test-results/**/*.xml",
]


def result_files() -> list[str]:
    seen: list[str] = []
    for pattern in PATTERNS:
        for path in sorted(glob.glob(pattern, recursive=True)):
            if path not in seen:
                seen.append(path)
    return seen


def main() -> int:
    files = result_files()
    print("junit result files: %s" % (files or "none"), file=sys.stderr)
    failures = 0
    for path in files:
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError as error:
            print("cannot parse %s: %s" % (path, error), file=sys.stderr)
            continue
        for case in root.iter("testcase"):
            for failure in list(case.iter("failure")) + list(case.iter("error")):
                failures += 1
                name = "%s > %s" % (case.get("classname"), case.get("name"))
                message = " ".join((failure.get("message") or "").split())
                body = " ".join((failure.text or "").split())
                detail = (message + " | " + body)[:480].rstrip(" |")
                print("::error file=%s::%s :: %s" % (path, name, detail))
    if failures == 0:
        print("no failed test cases found in the junit reports", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
