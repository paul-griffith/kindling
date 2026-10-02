# JCEF evaluation: embedded gateway network diagram viewer

> **Claude note:** this document was drafted by Claude (Claude Code) from a research pass and a
> proof-of-concept session on the `jcef` branch, October 2026. Development is paused; this records
> where things stand.

## Goal

The Gateway Network Diagram tool (`gatewaynetwork/GatewayNetworkViewer.kt`) renders its diagram with
a bundled React/cytoscape app (`main.js`, ~3.7 MB). It writes the static files and the diagram JSON to
a temp directory and opens `index.html` in the user's default browser via `Desktop.browse`. The aim
was to see whether the Java Chromium Embedded Framework (JCEF) could show the diagram inside Kindling
instead, and what packaging it with Hydraulic Conveyor would involve.

## Proof of concept (this branch)

**Result:** it worked. Run out of Gradle on macOS (Apple Silicon), the diagram rendered essentially
correctly in an embedded browser. There were some minor Swing container/layout quirks around the
browser component, but nothing significant.

### What changed

| File | Change |
|---|---|
| `gradle/libs.versions.toml`, `build.gradle.kts` | Added `me.friwi:jcefmaven:152.0.6`. Added the macOS `--add-opens` flags JCEF needs to `applicationDefaultJvmArgs`. |
| `utils/Jcef.kt` (new) | Lazy `CefApp` singleton built with jcefmaven's `CefAppBuilder`. On first use it downloads Chromium natives (~140 MB) to `~/.kindling/jcef` and uses `~/.kindling/jcef-cache` as `root_cache_path`. Windowed (not off-screen) rendering. Exposes install progress as a `StateFlow`. |
| `gatewaynetwork/GatewayNetworkViewer.kt` | The panel now has "Diagram" and "JSON" tabs. "Diagram" shows a progress label while CEF downloads and initializes (off the EDT), then swaps in `browser.uiComponent` loaded with the same temp-dir `index.html`. The "View diagram in browser" button is still there for comparison. |

### Running it

- `./gradlew run` picks up the JVM flags automatically.
- **IDE run configurations don't.** Add these VM options to the `MainPanel` run config, or CEF fails
  to start on macOS:
  ```
  --add-opens=java.desktop/sun.awt=ALL-UNNAMED
  --add-opens=java.desktop/sun.lwawt=ALL-UNNAMED
  --add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED
  ```
- The first diagram you open triggers the Chromium download. Later launches reuse `~/.kindling/jcef`.
- Off-screen rendering would also need `--add-exports` for `java.base/java.lang`,
  `java.desktop/sun.awt` and `java.desktop/sun.java2d`. The POC doesn't use it.

### Observations

- **Signature errors on stderr:** CEF logs
  `ProcessIsSignedAndFulfillsRequirement ... -67030` because the dev JVM is unsigned. This is harmless.
- **Stray `Exception in thread "AppKit Thread"` lines:** these appeared a few times with no stack trace
  following. Nothing visibly broke. This hasn't been investigated; installing a default uncaught
  exception handler would capture the trace.
- **Harmless Chromium error:** "Unable to get gpu adapter" from Chromium's on-device ML service.
- **Shutdown was clean:** `CefApp` is never disposed (the frame uses `EXIT_ON_CLOSE`), but quitting
  Kindling left no orphaned `jcef Helper` processes in the one test.

### Known gaps (POC-level shortcuts)

- **Browsers are never closed:** closing a tab doesn't close its browser or `CefClient`, and
  `CefApp.dispose()` is never called. Closing a tab should call `browser.close(true)`. Be careful
  with Kindling's floatable tabs, which re-parent components.
- **Heavyweight component:** windowed CEF is a heavyweight AWT component. Lightweight Swing popups
  (FlatLaf menus, tooltips, combo popups) can render behind it, and re-parenting or tab switching can
  glitch. This is the likely cause of the wrapping issues seen. The options are to force heavyweight
  popups, or to use off-screen rendering (OSR), which is laggier and has its own macOS keyboard bugs.
- **Still uses temp files:** it keeps the temp-dir/`file://` approach. A cleaner version would serve
  the classpath resources through a `CefResourceHandler` or custom scheme and inject the JSON with
  `executeJavaScript`.
- **Requires network access** the first time a diagram is opened, for the Chromium download.
- **Only tested on macOS arm64.** Windows and Linux weren't exercised.
- **Tested with synthetic data:** a minimal hand-written three-gateway JSON was used, not a real GAN
  dump.

## Packaging with Conveyor (not attempted)

### State of the ecosystem (October 2026)

- **jcefmaven** (`me.friwi:jcefmaven`) is still the way to get JCEF.
  - Actively maintained: 152.0.6 released 2026-09-30, roughly quarterly releases.
  - Upstream `chromiumembedded/java-cef` publishes no Maven artifacts of its own and points to
    jcefmaven.
  - Natives are published per platform as `me.friwi:jcef-natives-<os>-<arch>`, as a `.tar.gz` inside
    a jar.
  - Version string for 152.0.6: `jcef-17e805a+cef-152.0.6+g708dc14+chromium-152.0.7977.83`.
  - Don't trust `maven-metadata.xml`'s `<latest>` for the natives: the version strings sort
    lexically, so it reports an older build.
- **Hydraulic's guide is still the documented approach, but it's stale.**
  - Guide: <https://www.hydraulic.dev/blog/13-deploying-apps-with-jcef.html>
  - Sample: <https://github.com/hydraulic-software/jcef-conveyor>, last updated July 2023 (JCEF 110,
    Gradle plugin 1.4, compatibility level 10).
  - Conveyor's JVM docs still link to both.
- **Conveyor versions:** the current release is 22.3.1 and the Gradle plugin is 2.0. Kindling is on
  plugin 1.12 and compatibility level 18. Upgrading isn't strictly required.

### What bundling would look like

- Conveyor fetches the natives tarball from jcefmaven's GitHub releases using a `zip:<jar>!/<tar.gz>`
  URL and places it:
  - Windows/Linux: `jcef/` inside the app
  - macOS: `Contents/Frameworks` inside the bundle
- It also writes an empty `install.lock` marker next to the natives, so jcefmaven skips its download.

```hocon
jcef {
  releases = "https://github.com/jcefmaven/jcefmaven/releases/download/"
  // ver / commit-hash / cef-ver should come from Gradle (generated.conveyor.conf), not be hand-written
  windows.amd64 = "zip:"${jcef.releases}${jcef.ver}"/jcef-natives-windows-amd64-jcef-"${jcef.commit-hash}"+cef-"${jcef.cef-ver}".jar!/jcef-natives-windows-amd64-jcef-"${jcef.commit-hash}"+cef-"${jcef.cef-ver}".tar.gz"
  // ...same pattern for mac.amd64 (macosx-amd64), mac.aarch64 (macosx-arm64), linux.amd64.glibc (linux-amd64)
}
app {
  windows { amd64.inputs += ${jcef.windows.amd64} -> jcef
            inputs += { content = ".", to = jcef/install.lock } }
  linux   { amd64.glibc.inputs += ${jcef.linux.amd64.glibc} -> jcef
            inputs += { content = ".", to = jcef/install.lock } }
  mac {
    amd64.bundle-extras   += { from = ${jcef.mac.amd64},   to = Frameworks }
    aarch64.bundle-extras += { from = ${jcef.mac.aarch64}, to = Frameworks }
    bundle-extras += { content = ".", to = Frameworks/install.lock }
  }
}
```

`Jcef.kt` would then pick its install directory based on whether it's running under Conveyor:

- Packaged: `System.getProperty("app.dir")/jcef` on Windows/Linux, `app.dir/../Frameworks` on macOS.
  The sample's Apache-2 `JCefSetup.java` shows this.
- Development: a local directory, as now.

Conveyor downloads the natives by URL, so one machine (or the existing CI job) can still build every
platform. It re-signs and notarizes the CEF framework and helper apps with Kindling's Apple identity.

### Changes since the 2023 guide, and pitfalls

- **Version bump:** JCEF 110 → 152. The release asset naming is unchanged.
- **macOS 13 minimum:** CEF 152 requires macOS 13. Kindling already sets
  `LSMinimumSystemVersion = 14.0`, so this has no effect.
- **New helper app:** there's a fifth helper, `jcef Helper (Alerts).app`. It's handled automatically
  because the whole tarball is copied.
- **Set `root_cache_path` explicitly:** CEF 120+ locks it to a single process. The POC already does
  this.
- **Keep the version strings in sync (dangerous if they drift):**
  - jcefmaven's install check compares `install.lock` + `build_meta.json` against the `jcef-api`
    version on the classpath.
  - If they don't match, it **deletes the install directory** and re-downloads.
  - On macOS that directory is `Contents/Frameworks`, which also holds Conveyor's `Sparkle.framework`.
  - The jcefmaven version, commit hash and CEF version must stay in lockstep. Generate them from one
    place (`libs.versions.toml` → `generated.conveyor.conf`) and add a build-time check.
- **Windows DLL placement:** Conveyor compatibility levels ≥ 4/7 move EXEs/DLLs from *generic* inputs
  into the JVM directory. The sample uses platform-specific inputs, which should avoid this. Verify
  that `jcef.dll` and `jcef_helper.exe` end up under `jcef/`.
- **macOS entitlements:** the helpers need `cs.allow-jit`, `cs.allow-unsigned-executable-memory` and
  `cs.disable-library-validation`. Conveyor's JVM defaults are believed to cover these (unverified).
- **No sandbox:** JCEF hard-codes `no_sandbox = true`, so Linux `chrome-sandbox` SUID/AppArmor
  problems don't apply.
- **Linux arm64:** `libcef.so` static TLS allocation failures have been reported, worked around with
  `LD_PRELOAD` (jcefmaven #70). This doesn't matter unless Kindling ships linux-aarch64.
- **Open upstream bugs worth knowing:**
  - macOS: copy/paste/select-all (#517), Cmd+Q not terminating (#425)
  - Windows: focus not returning to Swing (#321)
  - Linux, OSR mode: menu/scroll issues (#441, #452)

### Size impact

Measured from the 152.0.6 native jars:

| Platform | Native jar | Unpacked | Estimated compressed in package |
|---|---|---|---|
| macOS arm64 | 142 MB | 321 MB | ~100 MB |
| macOS x64 | 154 MB | — | similar |
| Windows x64 | 171 MB | 372 MB | ~130 MB |
| Linux x64 | 152 MB | 359 MB | ~115 MB |

Bundling adds roughly **100–130 MB to every platform's download** and ~350 MB installed. Delta updates
keep app-only updates small, but each CEF upgrade costs close to the full amount again.

## Options going forward

| Option | Installer impact | Notes |
|---|---|---|
| **A. JCEF bundled via Conveyor** | +100–130 MB per platform | Works offline; signed with Kindling's identity. Biggest cost for a single tool. |
| **B. JCEF downloaded on first use** (what the POC does) | none | Needs network access on first use. On macOS, Kindling's signed JVM would load third-party-signed natives (jcefmaven's Developer ID), so the JVM needs `disable-library-validation`; untested under a notarized build. The install directory must not be `Contents/Frameworks`. |
| **C. JavaFX WebView in a `JFXPanel`** | about +40–60 MB | Conveyor supports JavaFX directly. It's WebKit, not Chromium, so the cytoscape bundle would need testing. Not evaluated. |
| **D. JetBrains Runtime (JBR) with built-in JCEF as the JDK** | large JDK | Conveyor ships a JetBrains JDK config, and 22.2 fixed a "JCEF sidecar files" packaging bug. But it uses JetBrains' JCEF fork with no stable public API, and ties Kindling to JBR instead of Corretto. |
| JxBrowser | — | Commercial; best Swing integration. |
| KCEF / webview_java | — | Not viable. KCEF is archived. webview_java is unmaintained and can't embed in Swing on macOS. |

If the diagram viewer remains the only embedded web content, B or C keeps the installer lean.
A becomes more reasonable if Kindling starts embedding web UI more broadly.

## Suggested next steps if work resumes

1. Close the browser when its tab is closed, and dispose `CefApp` on exit.
2. Look into the heavyweight-popup and wrapping issues: try heavyweight popups first, OSR second.
3. Serve resources through a `CefResourceHandler` instead of temp files.
4. Test on Windows and Linux, and with a real GAN diagram dump.
5. Pick a distribution option (A–C above).
   - For A: generate the `jcef.*` Conveyor values from Gradle, add the version-sync check, and verify
     signing/notarization and the Windows layout with `conveyor make`.

## References

- Hydraulic guide: <https://www.hydraulic.dev/blog/13-deploying-apps-with-jcef.html>
- Hydraulic sample: <https://github.com/hydraulic-software/jcef-conveyor>
- Conveyor JVM docs (JCEF section): <https://conveyor.hydraulic.dev/latest/configs/jvm/>
- Conveyor release notes: <https://conveyor.hydraulic.dev/latest/release-notes/>
- jcefmaven: <https://github.com/jcefmaven/jcefmaven>
- Upstream java-cef: <https://github.com/chromiumembedded/java-cef>
- Conveyor entitlements for nested helpers: <https://github.com/hydraulic-software/conveyor/issues/184>
- Conveyor with JBR+JCEF: <https://github.com/hydraulic-software/conveyor/issues/145>
- JetBrains JCEF fork: <https://github.com/JetBrains/jcef>
