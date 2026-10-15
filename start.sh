#!/usr/bin/env bash
# LokTvHookPro preview server.
# Serves the built static directory (dist/) in the FOREGROUND on $PORT (default 3000)
# and publishes deployment-output.json for the controller.
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PORT="${PORT:-3000}"
OUT_DIR="$PROJECT_DIR/dist"
META_DIR="${OPENCODE_WEB_DIR:-/home/runner/work/_temp/omgithub-web}"

/usr/bin/time -p mkdir -p "$META_DIR" "$OUT_DIR"

/usr/bin/time -p bash -c "cd \"$PROJECT_DIR\" && pwd"

# Install dependencies when the project declares any.
if [[ -f "$PROJECT_DIR/package.json" ]]; then
  if [[ -f "$PROJECT_DIR/package-lock.json" ]]; then
    /usr/bin/time -p bash -c "cd \"$PROJECT_DIR\" && npm ci --no-audit --no-fund"
  else
    /usr/bin/time -p bash -c "cd \"$PROJECT_DIR\" && npm install --no-audit --no-fund"
  fi
else
  echo "[start] no package.json — static only, skipping npm."
fi

# Build when needed: (re)generate the built static page from site/ source.
if [[ ! -f "$OUT_DIR/index.html" || "$PROJECT_DIR/site/index.html" -nt "$OUT_DIR/index.html" ]]; then
  /usr/bin/time -p cp -f "$PROJECT_DIR/site/index.html" "$OUT_DIR/index.html"
else
  echo "[start] dist/index.html up to date — build skipped."
fi

# Publish deployment metadata for the controller (paths only, not served content).
/usr/bin/time -p bash -c "printf '{\"project\": \"%s\", \"directory\": \"%s\"}' \"$PROJECT_DIR\" \"$OUT_DIR\" > \"$META_DIR/deployment-output.json\""
/usr/bin/time -p cat "$META_DIR/deployment-output.json"
echo

# Serve the built static directory in the foreground (controller owns the tmux session).
echo "[start] serving $OUT_DIR on 127.0.0.1:$PORT (foreground)"
cd "$OUT_DIR"
exec python3 -m http.server "$PORT" --bind 127.0.0.1
