#!/usr/bin/env python3
"""Builds the GitHub release notes from CHANGELOG.md plus the CI signing note.

Usage: python3 ci/release-notes.py <version>
"""

import os
import re
import sys


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__, file=sys.stderr)
        return 2
    version = sys.argv[1]

    with open("CHANGELOG.md", encoding="utf-8") as handle:
        changelog = handle.read()

    pattern = r"^## \[" + re.escape(version) + r"\].*?(?=^## \[|\Z)"
    match = re.search(pattern, changelog, re.MULTILINE | re.DOTALL)
    section = match.group(0).strip() if match else "See CHANGELOG.md for the details of this version."

    lines = [section, "", "## Install", ""]
    lines.append(
        "Download `NFC-Cardholder-%s.apk` from the assets below and install it on an "
        "Android device with NFC." % version
    )
    lines.append("")
    lines.append(
        "The same source builds on F-Droid with its own signing key; the APK attached here is "
        "built by GitHub Actions."
    )

    note_path = os.path.join("build", "signing-note.txt")
    if os.path.exists(note_path):
        with open(note_path, encoding="utf-8") as handle:
            note = handle.read().strip()
        if note:
            lines.append("")
            lines.extend("> " + line for line in note.splitlines())

    print("\n".join(lines))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
