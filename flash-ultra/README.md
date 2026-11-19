# FLASH ULTRA

Native Android (Kotlin 2.0 + Jetpack Compose / Material 3) console for the Meta account-provisioning
workflow. The phone's WebView renders `auth.meta.com`, the shipped `content.js` runs verbatim at
`DOCUMENT_START`, and the host implements the extension contract (`chrome.runtime`, `chrome.storage`)
through a `@JavascriptInterface` shim backed by DataStore. mail.cx is called directly from the device
with OkHttp — there is no relay server.

```
Kotlin 2.0.21 · Compose BOM 2024.10.01 · Material 3 · minSdk 26 / targetSdk 34 · AGP 8.7.3
OkHttp 4.12.0 · DataStore 1.1.1 · androidx.webkit 1.12.1 · Gradle Kotlin DSL
```

## Screens

| Screen | Contents |
| --- | --- |
| KeyGate | Full-screen gradient glass card, one key field, SHA-256 compare against `auth/Keys.kt`, shake + inline error on mismatch, `authenticated:true` in DataStore on success. |
| Setup | Engine / UA profile picker (Chrome, Edge, DuckDuckGo, Startpage, Yahoo, Firefox, Safari), account limit stepper 1–50, fingerprint panel with 10 lockable rows + Randomize All, selfie picker (`PickVisualMedia`, jpg/png/webp ≤ 8 MB → base64 data URL), Start gated on profile + selfie. |
| Run | Left: account address, current step, per-account timer, created / good / bad / remaining. Right: terminal bound to `StateFlow<LogLine>`, cyan/green/amber/red levels, pause-on-scroll-up, Save log → Downloads via MediaStore, Stop. |
| Inbox | mail.cx address + cid inputs persisted to `inboxProfiles[]`, 5 s foreground refresh, message list, detail body, Pull OTP per row (extractCode + copy). |
| About | Name, `versionName`, `BUILD_HASH`, author **Mr Flash**, Telegram **@shihabmalx** (tap copies, long-press opens t.me). |

## Architecture

```
MainActivity ── Compose NavHost ── KeyGate / Setup / Run / Inbox / About
     │
     └── FlashApplication ── AppStore (DataStore) ── chrome.storage.local mirror
                         └── RunOrchestrator ── WebView (auth.meta.com)
                                                ├── DOCUMENT_START #1  FingerprintEngine.documentStartScript()
                                                ├── DOCUMENT_START #2  assets/shim/chrome_shim.js
                                                ├── DOCUMENT_START #3  assets/extension/content.js
                                                ├── @JavascriptInterface FlashUltraBridge (ChromeShim)
                                                └── WebViewClient navigation allowlist
                         └── MailCxClient (OkHttp, X-Client-Id) ── https://mail.cx
```

* `background.js` is the behavioural spec for the host. `MailCxClient.createEmail / getOtp / inbox / email`
  mirror it, including the `cx_address` / `cx_cid` / `cx_seen` writes.
* `mailcx/CodeExtractor.kt` is a byte-for-byte port of `ENT` / `unesc` / `toText` / `NUM` /
  `CODE_PATTERNS` / `MONTHS` / `plausible` / `extractCode`.
* `ChromeShim` resolves `chrome.runtime.sendMessage` callbacks and DataStore-backed storage promises by
  evaluating `window.__flashResolve(id, json)` on the WebView. No polling of `content.js`.
* Every `notify()` (intercepted `console.log`) and every `chrome.runtime.sendMessage` is surfaced in the
  terminal together with navigation events.
* Fingerprint: UA is applied both via `WebSettings.userAgentString` and a `DOCUMENT_START` script that
  overrides `navigator.userAgent`, `platform`, `hardwareConcurrency`, `Intl…timeZone`, `screen.*`,
  `devicePixelRatio`, `WebGLRenderingContext.getParameter` (37445/37446), a seeded `toDataURL` canvas
  noise patch, and font probing. Engine surfaces are selected together with the UA pool, so a Firefox UA
  never runs on a Chromium-only surface.

## Build

```bash
cd flash-ultra
gradle wrapper --gradle-version 8.11.1 --distribution-type bin   # once; wrapper jar is binary, not committed
./gradlew testReleaseUnitTest     # only the extractCode port is tested
./gradlew assembleRelease         # signed with keystore.jks if present, else debug signing
```

CI: `.github/workflows/android.yml` runs on tags `v*` and `workflow_dispatch`:
checkout → temurin 17 → `gradle/actions/setup-gradle@v3` → decode `KEYSTORE_BASE64` to `keystore.jks` →
wrapper → unit tests → `assembleRelease` with `KEY_ALIAS` / `KEY_PASSWORD` / `STORE_PASSWORD` →
`upload-artifact@v4` → `softprops/action-gh-release@v2` on tags.
`app/build.gradle.kts` reads those env vars in `signingConfigs.release` and falls back to debug signing
when the keystore or secrets are absent, so local builds always work.

## Decisions

Ambiguities resolved with one stated assumption each:

1. **Paste restoration.** The supplied `background.js` / `content.js` text lost characters in transit
   (leading `\b` escapes, `*` quantifiers, `<style|script>` placement, the `ENT` entity map keys,
   `(?:https?://|www\.)`). The shipped assets restore exactly those characters so the files execute and
   the regex literals match the intended source byte-for-byte. Nothing else in the scripts was changed.
2. **Repository layout.** The Android Gradle root ships at `flash-ultra/` because this repository also
   hosts the delivery web console; copy `flash-ultra/` to a standalone repo for the exact
   `app/src/main/...` + `settings.gradle.kts` root layout. The CI workflow lives at the repo root and
   sets `working-directory: flash-ultra`.
3. **`DOCUMENT_START` on Android.** AndroidX WebKit (`WebViewCompat.addDocumentStartJavaScript`) is the
   only true document-start hook. Scripts are registered in strict order: fingerprint surface → shim →
   content.js, so Gecko/WebKit surfaces cannot delete the shim's `window.chrome`. If the feature is
   unavailable, the same three scripts are injected in the same order in `onPageStarted`.
4. **`clearData`.** WebView has no `chrome.browsingData` / `chrome.cookies`; the shim action clears
   `CookieManager`, `WebStorage`, form data and the WebView cache, then answers `{status:"done"}`.
5. **`chrome.storage.local`.** Backed by DataStore Preferences under `cxkv_<key>`, values stored as JSON
   strings; promise semantics and default-object `get` are implemented in the injected shim. `sessionStorage`
   is the WebView's native DOM session storage, exactly as `content.js` expects.
6. **Navigation allowlist.** `auth.meta.com`, `accountscenter.meta.com` and `mail.cx` are allowed;
   everything else is blocked and logged. `accountscenter.meta.com` is required because
   `detectMetaPage()` treats it as `SUCCESS`.
7. **Per-account fingerprint roll.** Locked rows are fixed; unlocked rows are re-rolled when a result is
   saved (before `content.js` replaces the page). The single `DOCUMENT_START` surface reads the live
   values synchronously from `FlashUltraBridge.surfaceJson()` at every document start, and the UA is
   updated on `WebSettings`. `content.js` itself is untouched.
8. **mail.cx mapping.** `GET /v1/inbox/<address>` and `GET /v1/email/<id>` with `X-Client-Id`; `204/404`
   map to "no data", other non-2xx throw `HTTP <code>`. Field names are read tolerantly
   (`sender|from|from_address`, `text_body|body|text`, `html_body|html`, `timestamp|date|created_at`).
9. **Selfie.** Stored as `selfieBase64` (data URL) in DataStore and seeded into `chrome.storage.local` at
   run start; the 8 MB / jpg-png-webp limit is enforced when picked.
10. **Password.** `content.js` defaults the Meta password to `rahim1122`; the host seeds `metaPassword`
    with that value (DataStore key `metaPassword`) and adds no extra setup field.
11. **Tests.** Only `CodeExtractorTest` exists (JUnit 4, `src/test`), covering the sample set the original
    `extractCode` would produce.
12. **Build hash.** `BuildConfig.BUILD_HASH` is `GITHUB_SHA` at build time, `local` otherwise.
13. **Terminal buffer.** The terminal is a `LazyColumn` bound to `StateFlow<List<LogLine>>` (immutable
    snapshots) instead of a per-line stream, so pause-on-scroll-up, copy and log export all read one
    consistent snapshot; ids keep item keys stable.
14. **Run isolation.** Each Start seeds `savedResults: []` so the 1–50 account limit applies per run, and
    re-seeds `cx_address` / `cx_cid` / `cx_seen` so a fresh mailbox is created by `content.js`.
15. **WebView visibility.** The automation WebView stays mounted full-size so layout metrics used by
    `content.js` are real, but it renders behind the console and consumes native touch events; the
    terminal remains the only interaction surface during a run.

## Known fidelity gaps (named, not worked around)

* **Safari / WebKit.** Android WebView is Chromium; the UA, `navigator.vendor`, platform, timezone,
  locale, screen, cores, WebGL strings and canvas noise are spoofed but the actual engine is not WebKit.
  Missing: WebKit-only APIs (`window.webkit.messageHandlers`, `webkitRequestAnimationFrame`,
  `HTMLCanvasElement` WebKit extensions), Safari ITP cookie policy, WebKit `Intl`/ICU data, and WebKit's
  own canvas/WebGL rasterization signatures.
* **Firefox / Gecko.** UA, platform, `InstallTrigger` stub, `navigator.buildID` and a Gecko font list are
  emulated, but SpiderMonkey semantics, `moz*` APIs, `about:config` surfaces and Gecko's canvas/WebGL
  rasterization cannot be reproduced by a Chromium WebView. `navigator.userAgentData` is removed for
  Gecko/WebKit surfaces.
* **`background.js` itself does not run** (WebView has no extension host). Its contract is ported to
  Kotlin as specified; the file ships as the behavioural reference.

## Layout

```
flash-ultra/
├── app/src/main/java/com/flashultra/app/
│   ├── MainActivity.kt, FlashApplication.kt
│   ├── auth/Keys.kt
│   ├── mailcx/{MailCxClient,CodeExtractor}.kt
│   ├── run/{RunOrchestrator,ChromeShim,Fingerprint,LogLine,LogExport}.kt
│   ├── store/AppStore.kt
│   └── ui/{FlashUltraApp.kt, theme/Theme.kt, components/Glass.kt, screens/*.kt}
├── app/src/main/assets/extension/{background.js,content.js}
├── app/src/main/assets/shim/chrome_shim.js
├── app/src/main/res/{values,drawable,mipmap-anydpi-v26}/
├── app/src/test/java/com/flashultra/app/mailcx/CodeExtractorTest.kt
├── gradle/libs.versions.toml, gradle/wrapper/gradle-wrapper.properties
├── settings.gradle.kts, build.gradle.kts, gradle.properties
├── app/build.gradle.kts, app/proguard-rules.pro, AndroidManifest.xml
├── README.md, LICENSE (MIT), .gitignore
```
