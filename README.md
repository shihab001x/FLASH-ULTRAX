# FLASH ULTRA — Android project + delivery console

This repository ships the complete **FLASH ULTRA** Android application (Kotlin 2.0, Compose Material 3,
OkHttp 4.12, DataStore, WebView + ChromeShim) plus a small Next.js/PostgreSQL delivery console that
indexes, previews and exports every project file.

## Where the Android project lives

```
flash-ultra/                    ← Gradle root (settings.gradle.kts, build.gradle.kts, gradle/, app/)
  app/src/main/java/com/flashultra/app/...
  app/src/main/assets/extension/{background.js,content.js}
  app/src/main/assets/shim/chrome_shim.js
  app/src/main/res/...
  app/src/test/java/.../CodeExtractorTest.kt
.github/workflows/android.yml   ← CI at the repo root, working-directory: flash-ultra
```

Read [`flash-ultra/README.md`](flash-ultra/README.md) for the architecture, the decisions taken on
ambiguities, the named WebView/Safari/Gecko fidelity gaps, the signing setup and the CI pipeline.

## Delivery console (this Next.js app)

* `/` — glassmorphism hub with project stats, searchable file browser, byte-level file viewer, decisions
  list and a one-click `.tar.gz` export of the Android project.
* Index metadata is stored in PostgreSQL via Drizzle (`artifacts`, `decisions` tables); the console
  re-indexes automatically and on demand (`POST /api/artifacts`).
* `GET /api/file?path=…` — guarded file reader (project paths only).
* `GET /api/archive` — streams `flash-ultra.tar.gz` (Android project + CI workflow).
* `GET /api/health` — PostgreSQL health probe.

```bash
npm run dev        # local console
npx drizzle-kit push   # create the index tables against DATABASE_URL
```

## Build the APK

```bash
cd flash-ultra
gradle wrapper --gradle-version 8.11.1 --distribution-type bin
./gradlew testReleaseUnitTest assembleRelease
```

CI signs with `KEYSTORE_BASE64`, `KEY_ALIAS`, `KEY_PASSWORD`, `STORE_PASSWORD` when present and falls back
to debug signing so local builds work without secrets.

MIT licensed — see [`flash-ultra/LICENSE`](flash-ultra/LICENSE).
