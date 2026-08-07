#!/usr/bin/env bash
set -euo pipefail

LABEL="com.treemiddle.blackbox.proxy"
PLIST="$HOME/Library/LaunchAgents/$LABEL.plist"
LOG="$HOME/Library/Logs/blackbox-proxy.log"
DOMAIN="gui/$(id -u)"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROXY="$SCRIPT_DIR/blackbox-proxy.py"
ADB_BIN="${ADB:-$(command -v adb || echo "$HOME/Library/Android/sdk/platform-tools/adb")}"

reset_blackbox() {
  launchctl bootout "$DOMAIN/$LABEL" 2>/dev/null || true
  pkill -f "blackbox-proxy.py" 2>/dev/null || true
  PIDS="$(lsof -nP -tiTCP:8080 -sTCP:LISTEN 2>/dev/null || true)"
  [ -n "$PIDS" ] && kill -9 $PIDS 2>/dev/null || true
  "$ADB_BIN" forward --remove-all 2>/dev/null || true
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    lsof -nP -tiTCP:8080 -sTCP:LISTEN >/dev/null 2>&1 || break
    sleep 0.3
  done
}

case "${1:-install}" in
  uninstall|stop|remove)
    launchctl bootout "$DOMAIN/$LABEL" 2>/dev/null || true
    rm -f "$PLIST"
    echo "BlackBox daemon removed."
    exit 0
    ;;
  status)
    if launchctl print "$DOMAIN/$LABEL" >/dev/null 2>&1; then
      echo "BlackBox daemon: loaded → http://localhost:8080"
    else
      echo "BlackBox daemon: not loaded"
    fi
    exit 0
    ;;
  restart|reset)
    if [ "${2:-}" = "--hard" ]; then
      "$ADB_BIN" kill-server >/dev/null 2>&1 || true
      "$ADB_BIN" start-server >/dev/null 2>&1 || true
    fi
    reset_blackbox
    if [ -f "$PLIST" ]; then
      launchctl bootstrap "$DOMAIN" "$PLIST"
      echo "BlackBox daemon restarted → http://localhost:8080"
    else
      echo "ports/forwards reset. daemon not installed — run: $(basename "$0") install"
    fi
    exit 0
    ;;
  install)
    ;;
  *)
    echo "usage: $(basename "$0") {install|status|restart|uninstall}" >&2
    exit 1
    ;;
esac

[ -f "$PROXY" ] || { echo "proxy not found: $PROXY" >&2; exit 1; }

if /usr/bin/python3 -c 'import sys' >/dev/null 2>&1; then
  PYTHON="/usr/bin/python3"
else
  PYTHON="$(command -v python3 || echo /usr/bin/python3)"
fi

mkdir -p "$HOME/Library/LaunchAgents" "$HOME/Library/Logs"

cat > "$PLIST" <<PLIST_EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key><string>$LABEL</string>
  <key>ProgramArguments</key>
  <array>
    <string>$PYTHON</string>
    <string>$PROXY</string>
  </array>
  <key>EnvironmentVariables</key>
  <dict>
    <key>ADB</key><string>$ADB_BIN</string>
    <key>PYTHONUNBUFFERED</key><string>1</string>
  </dict>
  <key>RunAtLoad</key><true/>
  <key>KeepAlive</key><true/>
  <key>ProcessType</key><string>Background</string>
  <key>StandardOutPath</key><string>$LOG</string>
  <key>StandardErrorPath</key><string>$LOG</string>
</dict>
</plist>
PLIST_EOF

reset_blackbox
launchctl bootstrap "$DOMAIN" "$PLIST"

echo "BlackBox daemon installed → http://localhost:8080"
echo "  proxy : $PROXY"
echo "  python: $PYTHON"
echo "  adb   : $ADB_BIN"
echo "  log   : $LOG"
echo "  stop  : $SCRIPT_DIR/$(basename "$0") uninstall"
