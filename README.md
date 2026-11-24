<div align="center">

<img src="fastlane/metadata/android/en-US/images/featureGraphic.png" alt="QRshare: share and scan QR codes from the Android share sheet." />

[![Build signed release APK](https://github.com/pandasuryanarayan/QRshare/actions/workflows/release.yml/badge.svg)](https://github.com/pandasuryanarayan/QRshare/actions/workflows/release.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue)](LICENSE)

</div>

---

**An all-in-one QR app for Android: generate codes and read them — no ads, no
tracking, no Google Play Services, and under 1.3 MB.**

Share a link from any app and get a code instantly. Scan with the camera, or
decode a code straight out of a screenshot someone sent you. Build proper
payloads instead of raw text — Wi-Fi, contacts, UPI payments, calendar events,
locations, email, SMS, 2FA. Choose how much error correction you want, or let
the app pick the strongest level that fits. Save it, copy it, share it, and find
it again in history.

Forked from [mrwm/QRshare](https://github.com/mrwm/QRshare), which was a
generate-only app. This fork adds the other half.

## What changed from upstream

### Reading codes (the old "anti-feature")

- **Camera scanner** built on CameraX + ZXing — no ML Kit, so it still builds on F-Droid.
- **Scan from an image**: decode a QR out of a screenshot or photo.
- **Quick Settings tile** and a **launcher shortcut** that open the scanner directly.
- After a scan you can open the result, copy it, or save a file out of it.

### Generating richer codes

- **Templates** that build correct payloads instead of raw text:
  Wi-Fi, contact (vCard 3.0), **UPI payment**, calendar event (VEVENT), location,
  email, SMS, phone, and 2FA (`otpauth://`).
- **Selectable error correction** (L / M / Q / H) with automatic fallback.

### The 1 KB file limit is gone

The upstream code hardcoded `ErrorCorrectionLevel.H`, which caps a symbol at
1,273 bytes, and its size guard only showed a Toast before handing oversized data
to the encoder — the blank screen / ANR its own changelog warned about.

- ECC is now chosen from the payload size: **2,331 bytes at M**, **2,953 at L**.
- The limit is enforced *before* encoding, with a live `bytes / capacity` readout.
- Files are **deflated before Base64**, so small files fit where they never did.
- Packed files use a `QRZ1|mime|codec|base64` envelope and can be unpacked again in-app.

### Fixes

- Text files keep their exact bytes — the old reader appended `\n` to every line and
  ignored the charset.
- The exported PNG is rendered at a fixed 1024 px instead of the screen width, so
  dense symbols keep enough pixels per module on low-resolution phones.
- `assert` guards (stripped in release builds) replaced with real null checks.
- `StringUtil extends AppCompatActivity` replaced by plain helpers.
- FileProvider narrowed from the whole cache directory to `cache/qrcodes/`.
- Layout weights fixed (`0dp` + weight), stray launcher-icon background removed.
- `ACTION_SEND_MULTIPLE` is supported: several shared items become pages.

### Also new

- History of everything generated or scanned (JSON, capped at 200 entries).
- Save to gallery (Android 10+), copy image, share image.
- Quick Settings tile, static app shortcuts, clipboard shortcut.
- Unit tests for capacity maths, payload escaping and the file round-trip.

## Build

Requires JDK 17+ and the Android SDK (compileSdk 36).

```bash
./gradlew assembleDebug          # debug build
./gradlew testReleaseUnitTest    # unit tests
./gradlew assembleRelease        # needs signing env vars, see below
```

### Signed release builds

**The four secrets below are required.** The workflow fails without them rather
than quietly signing with a fresh key: Android only accepts an update if the
signature matches the installed copy, so a rotating key forces every user to
uninstall before installing.

Add them under *Settings → Secrets and variables → Actions → New repository secret*:

| Secret | What it is |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 release.keystore` |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key password |

Once they are set, every build — local, push, or tag — is signed with the same
key, and APKs install over each other as normal updates.

Generate a key once and keep it safe — every future update must be signed with it:

```bash
keytool -genkeypair -v -keystore release.keystore -alias qrshare \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass 'YOUR_PASSWORD' -keypass 'YOUR_PASSWORD' \
  -dname "CN=QRshare, O=Personal, C=IN"
base64 -w0 release.keystore   # paste into the KEYSTORE_BASE64 secret
```

Locally:

```bash
KEYSTORE_PATH=release.keystore KEYSTORE_PASSWORD=... KEY_ALIAS=qrshare \
KEY_PASSWORD=... ./gradlew assembleRelease
```

## Release optimisation

The `release` build type runs the most aggressive pass available:

- R8 full mode (`android.enableR8.fullMode=true`)
- `isMinifyEnabled` + `isShrinkResources` + `isCrunchPngs`
- `proguard-android-optimize.txt` plus app rules
- v1 + v2 + v3 APK signatures

## License

[Apache License 2.0](LICENSE) — the same licence as the upstream project, so the
fork inherits it unchanged.

This project is a fork of [mrwm/QRshare](https://github.com/mrwm/QRshare). Both
are Apache-2.0, so there is no relicensing step and no conflict of terms. If you
redistribute a modified build, keep the copyright and licence notices and state
that the files were changed. Attribution to the original author is kept here and
in the introduction above.
