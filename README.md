# BlackBox

A lightweight, **browser-based** debugging tool for Android — inspect your app's
**network, SharedPreferences, and SQLite/Room**, plus **screenshots and screen
recording**, all from your desktop browser.

Think Flipper, but the viewer is just Chrome (no Electron) and there are **zero
native dependencies** — so no 16 KB page-size issues on modern devices.

## Why

[Flipper](https://github.com/facebook/flipper) is archived (EOL) and the native
`.so` libraries it bundles aren't 16 KB-page aligned — a problem for `debug`
builds on 16 KB devices (SDK 36+). BlackBox replaces the parts teams actually
use with a pure-JVM, native-free approach:

- **Device side** — a tiny embedded HTTP server (Ktor CIO) exposes captured data
  as JSON plus a static HTML UI. Bound to `127.0.0.1` only, `debug` builds only,
  no-op in release.
- **Desktop side** — your browser, reached over `adb forward`. Nothing to install.

## Features

- 🌐 **Network** — OkHttp request/response capture (method, status, URL, timing,
  headers, bodies) with JSON pretty-print, URL filter, clear, sortable request
  time (asc/desc), and one-click copy of a body or the request as a cURL command.
- ⚙️ **Preferences** — every SharedPreferences file (key / value / type); edit or
  delete values (type-preserving).
- 🗄️ **Databases** — browse SQLite/Room databases, tables, and rows (read-only)
  with an optional **Live Updates** toggle.
- 📷 **Screenshot** — capture the current app window (PixelCopy, no permission).
- ⏺ **Screen recording** — MediaProjection → H.264 MP4, auto-downloaded on stop.
- 🧩 **Multi-app** — several debug apps on one device each claim the next free port
  (`8080`–`8089`); the app on `8080` acts as a hub, serving an app picker and
  reverse-proxying to the others, so a single `localhost:8080` exposes every app.
- 📱 **Multi-device** — a small host proxy enumerates connected devices and serves
  one adaptive UI (a device selector appears automatically with 2+ devices). Device
  selection (proxy) and app selection (device hub) compose: pick a device, then an app.

## Quick start

Build the debug APK (needs JDK 21 — Android Studio's bundled JBR works):

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:assembleDebug
```

**One device:**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.treemiddle.blackbox/.MainActivity
adb forward tcp:8080 tcp:8080
open http://localhost:8080
```

**Multiple devices** — install/launch on each, then run the proxy:

```bash
python3 tools/blackbox-proxy.py     # auto-forwards every connected device
open http://localhost:8080          # device selector appears at the top
```

The proxy is adaptive: one device → that device directly; two or more → a device
dropdown. New devices are detected automatically. No third-party Python deps. The
proxy binds port 8080 itself and clears any stray `adb forward tcp:8080`, so run it
*instead of* the one-device forward above — not both.

**Multiple apps on one device** — just install and launch each. The first app binds
`8080` and becomes the hub; the rest take `8081`–`8089`. Open `localhost:8080` and an
**app dropdown** appears at the top — no extra tooling. (Combine with the proxy above
for a device dropdown + app dropdown.)

## How it works

```
[device · debug build]
   BlackBox agent (auto-starts in Application.onCreate)
   ├─ Ktor CIO server on first free 127.0.0.1 port in 8080..8089
   ├─ OkHttp interceptor → ring buffer      (network)
   ├─ SharedPreferences / SQLite readers     (prefs, db)
   ├─ PixelCopy / MediaProjection            (screenshot, record)
   └─ hub (only the app on :8080): app picker + reverse-proxy to sibling
      apps via loopback — discovers them by probing 127.0.0.1:8080..8089
        │ adb forward (USB tunnel)
        ▼
[desktop]  browser → http://localhost:8080
        ▲
   host proxy (tools/blackbox-proxy.py) — only for multiple devices:
   enumerates via `adb devices`, reverse-proxies /api/* to each device
```

Two orthogonal layers: the **device hub** picks among apps on one device
(all in-app, single `adb forward`); the **host proxy** picks among devices.
Multi-device inherently needs the proxy — an app can't enumerate other devices
(`adb devices` is a host-only command). The hub reaches sibling apps over the
device's shared loopback using a tiny raw-socket HTTP client (no cleartext-policy
dependency).

## Tech

- Kotlin · AGP 9.0.1 / Gradle 9.1 · min SDK 26
- [Ktor](https://ktor.io) `server-cio` (embedded server) · OkHttp 5
- Vanilla HTML/CSS/JS UI — single file, no framework or build step
- Host proxy: Python 3 standard library only

## Project structure

```
app/src/main/
  ├─ java/com/treemiddle/blackbox/
  │   ├─ server/     Ktor server + routes, hub (app picker), loopback HTTP client
  │   ├─ capture/    OkHttp interceptor + thread-safe ring buffer
  │   ├─ prefs/      SharedPreferences read / write
  │   ├─ db/         SQLite read-only reader
  │   ├─ screen/     PixelCopy screenshot
  │   └─ record/     MediaProjection screen recording
  └─ assets/devtools/
      ├─ index.html  per-app browser UI (network / prefs / db / …)
      └─ hub.html     app picker served by the :8080 hub
tools/blackbox-proxy.py           host-side multi-device proxy
```

## Status

Proof of concept — all features built and verified on real devices, now including
multi-app (device-side hub) and multi-device (host proxy). The device-side agent has
been extracted into TADA's `core/devtools` source module — a flavorless Android
library shared by the rider and driver apps (debug-only, no-op in release), wired in
with a one-line `DevTools.init(app)` at startup plus one interceptor line in the
OkHttp client factory.

---

Not affiliated with Meta/Flipper. Inspired by Flipper and Stetho.
