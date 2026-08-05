#!/usr/bin/env bash
set -euo pipefail

LABEL="com.treemiddle.blackbox.proxy"
PLIST="$HOME/Library/LaunchAgents/$LABEL.plist"
LOG="$HOME/Library/Logs/blackbox-proxy.log"
DOMAIN="gui/$(id -u)"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROXY="$SCRIPT_DIR/blackbox-proxy.py"

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
esac

[ -f "$PROXY" ] || { echo "proxy not found: $PROXY" >&2; exit 1; }

if /usr/bin/python3 -c 'import sys' >/dev/null 2>&1; then
  PYTHON="/usr/bin/python3"
else
  PYTHON="$(command -v python3 || echo /usr/bin/python3)"
fi
ADB_BIN="${ADB:-$(command -v adb || echo "$HOME/Library/Android/sdk/platform-tools/adb")}"

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

launchctl bootout "$DOMAIN/$LABEL" 2>/dev/null || true
launchctl bootstrap "$DOMAIN" "$PLIST"

echo "BlackBox daemon installed → http://localhost:8080"
echo "  proxy : $PROXY"
echo "  python: $PYTHON"
echo "  adb   : $ADB_BIN"
echo "  log   : $LOG"
echo "  stop  : $SCRIPT_DIR/$(basename "$0") uninstall"
