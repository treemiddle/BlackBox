#!/usr/bin/env python3
"""BlackBox device proxy (host-side) — single, adaptive entry point.

Enumerates connected devices via adb, forwards each device's server
(127.0.0.1:8080 on the device) to a unique local port, and serves the UI at
http://localhost:8080.

Adaptive: with one device it shows that device's UI directly; with two or more
it reveals a device selector bar at the top. Newly connected devices are
detected automatically (the UI polls). Each device's own UI is loaded in an
iframe (/dev/<port>/) and its /api/* calls are reverse-proxied to that device.

Usage:  python3 tools/blackbox-proxy.py   →   open http://localhost:8080
No third-party dependencies (Python 3.7+ stdlib only).
"""
import http.client
import http.server
import json
import os
import re
import shutil
import subprocess
import threading
import time

DEVICE_PORT = int(os.environ.get("BLACKBOX_DEVICE_PORT", "8080"))
PROXY_PORT = int(os.environ.get("BLACKBOX_PROXY_PORT", "8080"))
BASE_LOCAL_PORT = int(os.environ.get("BLACKBOX_BASE_PORT", "8081"))
REQUEST_TIMEOUT = int(os.environ.get("BLACKBOX_REQUEST_TIMEOUT", "4"))
REFRESH_INTERVAL = int(os.environ.get("BLACKBOX_REFRESH_INTERVAL", "3"))

ADB = (
    os.environ.get("ADB")
    or shutil.which("adb")
    or os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")
)

devices = {}  # serial -> {"model": str, "port": int}
_devices_lock = threading.Lock()
_pool_lock = threading.Lock()
_pool = {}


def adb(*args, timeout=10):
    try:
        return subprocess.run(
            [ADB, *args], capture_output=True, text=True, timeout=timeout
        ).stdout.strip()
    except Exception:
        return ""


def connected_serials():
    serials = []
    for line in adb("devices").splitlines()[1:]:
        parts = line.split("\t")
        if len(parts) == 2 and parts[1].strip() == "device":
            serials.append(parts[0].strip())
    return serials


def refresh_devices():
    serials = connected_serials()
    with _devices_lock:
        snapshot = dict(devices)
    used = {snapshot[s]["port"] for s in serials if s in snapshot}
    next_port = BASE_LOCAL_PORT
    result = {}
    for serial in serials:
        existing = snapshot.get(serial)
        if existing:
            port, model = existing["port"], existing["model"]
        else:
            while next_port in used:
                next_port += 1
            port = next_port
            used.add(port)
            model = adb("-s", serial, "shell", "getprop", "ro.product.model") or serial
            adb("-s", serial, "shell", "settings", "put", "global", "cached_apps_freezer", "disabled")
        subprocess.run(
            [ADB, "-s", serial, "forward", f"tcp:{port}", f"tcp:{DEVICE_PORT}"],
            capture_output=True,
            timeout=10,
        )
        result[serial] = {"model": model, "port": port}
    with _devices_lock:
        devices.clear()
        devices.update(result)


def devices_snapshot():
    with _devices_lock:
        return [
            {"serial": s, "model": v["model"], "port": v["port"]}
            for s, v in devices.items()
        ]


def free_proxy_port():
    for serial in connected_serials():
        subprocess.run(
            [ADB, "-s", serial, "forward", "--remove", f"tcp:{PROXY_PORT}"],
            capture_output=True,
            timeout=5,
        )


def _get_conn(port):
    with _pool_lock:
        bucket = _pool.get(port)
        if bucket:
            return bucket.pop(), True
    return http.client.HTTPConnection("127.0.0.1", port, timeout=REQUEST_TIMEOUT), False


def _put_conn(port, conn):
    with _pool_lock:
        _pool.setdefault(port, []).append(conn)


def relay(port, method, path, body):
    error = None
    for _ in range(8):
        conn, reused = _get_conn(port)
        try:
            conn.request(method, path, body=body)
            response = conn.getresponse()
            data = response.read()
            ctype = response.getheader("Content-Type", "application/octet-stream")
            status = response.status
            _put_conn(port, conn)
            return data, ctype, status
        except Exception as err:
            error = err
            try:
                conn.close()
            except Exception:
                pass
            if not reused:
                break
    raise error


def device_refresher():
    while True:
        refresh_devices()
        time.sleep(REFRESH_INTERVAL)


class Handler(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_GET(self):
        if self.path == "/":
            self._send(SHELL_HTML.encode(), "text/html; charset=utf-8")
        elif self.path == "/devices":
            self._send(json.dumps(devices_snapshot()).encode(), "application/json")
        elif self.path.startswith("/dev/"):
            self._proxy("GET")
        else:
            self.send_error(404)

    def do_POST(self):
        if self.path.startswith("/dev/"):
            self._proxy("POST")
        else:
            self.send_error(404)

    def _proxy(self, method):
        match = re.match(r"^/dev/(\d+)(/.*)$", self.path)
        if not match:
            self.send_error(404)
            return
        port, rest = int(match.group(1)), match.group(2)
        body = None
        if method == "POST":
            length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(length) if length else b""
        try:
            data, ctype, status = relay(port, method, rest, body)
        except Exception as error:
            self.send_error(502, str(error))
            return
        self._send(data, ctype, status)

    def _send(self, data, ctype, status=200):
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def handle_one_request(self):
        try:
            super().handle_one_request()
        except (ConnectionResetError, BrokenPipeError):
            self.close_connection = True

    def log_message(self, *args):
        pass


SHELL_HTML = """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>BlackBox</title>
  <style>
    html, body { margin: 0; height: 100%; background: #15171c; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; color: #d7dae0; }
    .bar { height: 42px; display: none; align-items: center; gap: 12px; padding: 0 14px; background: #15171c; border-bottom: 1px solid #2a2f38; font-size: 14px; }
    .bar.show { display: flex; }
    .brand { font-weight: 700; display: flex; align-items: center; gap: 8px; }
    .brand .dot { width: 9px; height: 9px; border-radius: 50%; background: #4c8bf5; box-shadow: 0 0 8px #4c8bf5; }
    select { background: #12141a; color: #d7dae0; border: 1px solid #2a2f38; border-radius: 7px; padding: 5px 10px; font-size: 13px; outline: none; }
    select:focus { border-color: #4c8bf5; }
    button { background: #252b34; border: 1px solid #313945; color: #cfd4dc; border-radius: 7px; padding: 5px 11px; cursor: pointer; font-size: 12px; }
    button:hover { background: #2d353f; }
    .muted { color: #6b7482; font-size: 12px; }
    iframe { border: 0; width: 100%; height: 100vh; display: block; background: #1b1e24; }
    .empty { display: none; align-items: center; justify-content: center; height: 100vh; color: #5c6672; }
  </style>
</head>
<body>
  <div class="bar" id="bar">
    <span class="brand"><span class="dot"></span> BlackBox</span>
    <span class="muted">Device</span>
    <select id="dev"></select>
    <button onclick="refresh()">↻</button>
    <span class="muted" id="cnt"></span>
  </div>
  <iframe id="frame"></iframe>
  <div class="empty" id="empty">No devices connected · plug one in</div>
  <script>
    const bar = document.getElementById('bar');
    const sel = document.getElementById('dev');
    const frame = document.getElementById('frame');
    const empty = document.getElementById('empty');
    let sig = '';

    function show(port) { frame.src = '/dev/' + port + '/'; }

    async function refresh() {
      let list = [];
      try { list = await (await fetch('/devices')).json(); } catch (e) {}
      const multi = list.length > 1;
      bar.classList.toggle('show', multi);
      frame.style.height = multi ? 'calc(100vh - 42px)' : '100vh';

      if (!list.length) { frame.style.display = 'none'; empty.style.display = 'flex'; sig = ''; return; }
      frame.style.display = 'block'; empty.style.display = 'none';
      document.getElementById('cnt').textContent = list.length + ' device(s)';

      const newSig = list.map((x) => x.port + ':' + x.serial).join(',');
      if (newSig === sig) return;
      const prev = sel.value;
      sig = newSig;
      sel.innerHTML = list.map((x) => '<option value="' + x.port + '">' + x.model + ' · ' + x.serial + '</option>').join('');
      const keep = list.some((x) => String(x.port) === prev);
      sel.value = keep ? prev : String(list[0].port);
      if (!keep || !frame.getAttribute('src')) show(sel.value);
    }

    sel.onchange = () => show(sel.value);
    refresh();
    setInterval(refresh, 4000);
  </script>
</body>
</html>
"""


def main():
    free_proxy_port()
    refresh_devices()
    print(f"BlackBox → http://localhost:{PROXY_PORT}  (adb: {ADB})")
    for serial, info in devices.items():
        print(f"  {info['model']} ({serial}) → tcp:{info['port']}")
    if not devices:
        print("  (no devices connected yet)")
    threading.Thread(target=device_refresher, daemon=True).start()
    http.server.ThreadingHTTPServer(("127.0.0.1", PROXY_PORT), Handler).serve_forever()


if __name__ == "__main__":
    main()
