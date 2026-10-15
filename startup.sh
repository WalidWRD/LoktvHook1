#!/usr/bin/env bash
# Once-per-worker prerequisites only (executed by the worker bootstrap, not per start).
set -euo pipefail

# Headed-Chromium binary for playwright-cli captures (idempotent: skips when present).
# NOTE: use the version-pinned playwright installer — `playwright-cli install-browser`
# may fetch a build (e.g. 1248) that this playwright-core (expects 1243) rejects.
if ! ls ~/.cache/ms-playwright/chromium-*/chrome-linux*/chrome >/dev/null 2>&1; then
  /usr/bin/time -p "$HOME/.local/share/omgithub-playwright/node_modules/.bin/playwright" install chromium
fi
