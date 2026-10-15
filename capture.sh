#!/usr/bin/env bash
# Capture desktop + mobile screenshots of $CAPTURE_URL into $CAPTURE_DIR.
# Exit 75 = temporary navigation/browser infrastructure failure.
# Exit 1  = script usage error or rendering defect. Leaves the app running.
set -euo pipefail

URL="${CAPTURE_URL:-}"
DIR="${CAPTURE_DIR:-}"
if [[ -z "$URL" ]]; then echo "[capture] CAPTURE_URL is required." >&2; exit 1; fi
if [[ -z "$DIR" ]]; then echo "[capture] CAPTURE_DIR is required." >&2; exit 1; fi
/usr/bin/time -p mkdir -p "$DIR"

# --- Display: reuse the worker Xvfb, else start our own. ---
OWN_XVFB=""
if [[ -z "${DISPLAY:-}" || ! -S "/tmp/.X11-unix/X${DISPLAY#:}" ]]; then
  DETECTED=""
  for sock in /tmp/.X11-unix/X*; do
    [[ -S "$sock" ]] || continue
    DETECTED=":${sock##*X}"
    break
  done
  if [[ -n "$DETECTED" ]]; then
    export DISPLAY="$DETECTED"
  else
    export DISPLAY=":99"
    /usr/bin/time -p Xvfb :99 -screen 0 1440x900x24 -nolisten tcp &
    OWN_XVFB="$!"
    /usr/bin/time -p sleep 2
  fi
fi
echo "[capture] DISPLAY=$DISPLAY url=$URL dir=$DIR"

SESSION="cap-$$-$(date +%s)"
# Prefer the underlying CLI binary with system Chrome: the shared ms-playwright
# chromium build may skew from this worker's playwright-core (exit 75 loop),
# while /usr/bin/google-chrome is always present. Fall back to the PATH wrapper.
PW_BIN="$HOME/.local/share/omgithub-playwright/node_modules/.bin/playwright-cli"
[[ -x "$PW_BIN" ]] || PW_BIN="playwright-cli"
PW="$PW_BIN -s=$SESSION"
BROWSER_ARGS="--browser chrome"

cleanup() {
  /usr/bin/time -p $PW close 2>/dev/null || true
  if [[ -n "$OWN_XVFB" ]]; then kill "$OWN_XVFB" 2>/dev/null || true; fi
}
trap cleanup EXIT

# --- Open the exact URL (navigation/browser failures are temporary: exit 75). ---
if ! /usr/bin/time -p timeout 90 $PW open $BROWSER_ARGS "$URL" >/tmp/cap-open.log 2>&1; then
  echo "[capture] browser open/navigation failed (temporary)." >&2
  tail -n 5 /tmp/cap-open.log >&2 || true
  exit 75
fi

# --- Wait for rendered content (readyState + non-empty body, up to ~40s). ---
RENDERED=false
for ((i=0; i<20; i++)); do
  # eval prints a verbose block; the actual value is the quoted line after "### Result".
  RAW="$(/usr/bin/time -p $PW eval "()=>document.readyState+'|'+document.body.innerText.length" 2>/dev/null || true)"
  STATE="$(printf '%s\n' "$RAW" | grep -m1 '^"' | tr -d '"')"
  echo "[capture] poll $i: $STATE"
  if [[ "$STATE" == complete* ]]; then
    LEN="${STATE##*|}"
    if [[ "$LEN" =~ ^[0-9]+$ ]] && (( LEN > 50 )); then RENDERED=true; break; fi
  fi
  /usr/bin/time -p sleep 2
done
if [[ "$RENDERED" != true ]]; then
  # Distinguish: reachable-but-blank (defect=1) vs never-settled (temporary=75).
  if /usr/bin/time -p timeout 30 $PW goto "$URL" >/dev/null 2>&1; then
    echo "[capture] page reachable but rendered no content (defect)." >&2
    exit 1
  fi
  echo "[capture] page never settled (temporary)." >&2
  exit 75
fi
/usr/bin/time -p sleep 2

# --- Sanity: expected app content present, else rendering defect. ---
if ! /usr/bin/time -p $PW find "LokTvHookPro" >/dev/null 2>&1; then
  echo "[capture] expected content 'LokTvHookPro' not found (defect)." >&2
  exit 1
fi

# --- Desktop 1280x800. ---
/usr/bin/time -p $PW resize 1280 800
/usr/bin/time -p sleep 1
if ! /usr/bin/time -p $PW screenshot --filename "$DIR/final-desktop.png"; then
  echo "[capture] desktop screenshot failed." >&2
  exit 1
fi

# --- Mobile 390x844 (reload so responsive layout applies). ---
/usr/bin/time -p $PW resize 390 844
if ! /usr/bin/time -p timeout 60 $PW reload >/dev/null 2>&1; then
  echo "[capture] mobile reload failed (temporary)." >&2
  exit 75
fi
/usr/bin/time -p sleep 2
if ! /usr/bin/time -p $PW screenshot --filename "$DIR/final-mobile.png"; then
  echo "[capture] mobile screenshot failed." >&2
  exit 1
fi

# --- Verify artifacts (missing/empty = defect). ---
for f in "$DIR/final-desktop.png" "$DIR/final-mobile.png"; do
  if [[ ! -s "$f" ]] || (( $(/usr/bin/time -p stat -c%s "$f") < 1024 )); then
    echo "[capture] artifact invalid: $f (defect)." >&2
    exit 1
  fi
done
/usr/bin/time -p ls -la "$DIR/final-desktop.png" "$DIR/final-mobile.png"
echo "[capture] OK — app left running."
exit 0
