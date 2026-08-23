#!/usr/bin/env python3
"""Build the vendored interface and put it where the service serves it from.

    python build-webapp.py

Two steps, and the second one is not optional. Webpack writes `webapp/pack/`; the service
serves `src/main/resources/static-resources/`. Between them, `{{.BaseURL}}` is replaced with
nothing: focalboard's own server fills that placeholder in as a Go template before sending
`index.html`, and a static file server does not, so the page would ask for
`{{.BaseURL}}/static/main.js` and get a 404 with no error anywhere saying why.
"""

from __future__ import annotations

import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
WEBAPP = ROOT / "webapp"
PACK = WEBAPP / "pack"
STATIC = ROOT / "src" / "main" / "resources" / "static-resources"


def main() -> int:
    if not (WEBAPP / "node_modules").is_dir():
        print("webapp/node_modules is missing — run `npm install --legacy-peer-deps` in webapp/")
        return 1

    print("building webapp/ …")
    result = subprocess.run(
        ["npx", "cross-env", "NODE_ENV=production", "webpack", "--config", "webpack.prod.js"],
        cwd=WEBAPP, shell=True)
    if result.returncode != 0:
        return result.returncode

    if STATIC.exists():
        shutil.rmtree(STATIC)
    shutil.copytree(PACK, STATIC)

    index = STATIC / "index.html"
    html = index.read_text(encoding="utf-8").replace("{{.BaseURL}}", "")
    index.write_text(html, encoding="utf-8", newline="\n")

    files = sum(1 for _ in STATIC.rglob("*") if _.is_file())
    print(f"wrote {files} file(s) to {STATIC.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
