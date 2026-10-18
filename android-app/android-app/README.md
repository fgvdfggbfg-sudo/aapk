# Calculator — Android app

Offline Android app built from the website in `public_html`. All 30 calculators run
from `app/src/main/assets/www`, so the app works without internet (only the currency
rates need a connection).

## Option A: get the APK without installing anything (GitHub)

1. Create a free account at github.com and make a **new repository** (e.g. `calculator-app`).
2. On the repository page click **uploading an existing file**, then drag **all files and
   folders from inside this `android-app` folder** (including the hidden `.github` folder)
   and click **Commit changes**.
   - If the `.github` folder doesn't upload, create the file manually:
     **Add file → Create new file**, name it `.github/workflows/build-apk.yml`,
     and paste the contents of that file.
3. Open the **Actions** tab. A "Build APK" run starts automatically (about 4–6 minutes).
4. When it shows a green tick, open the run and download **Calculator-APK** under *Artifacts*.
   Unzip it — `app-debug.apk` inside is your app.
5. Send the APK to your phone, tap it, and allow "Install unknown apps" when asked.

## Option B: Android Studio

1. Install Android Studio (Ladybug or newer).
2. **File → Open** and choose this `android-app` folder. Wait for Gradle sync.
3. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
   The APK is in `app/build/outputs/apk/debug/`.

## Changing the app

| What | Where |
|---|---|
| App name | `app/src/main/res/values/strings.xml` |
| Package id (must be unique on Play Store) | `applicationId` in `app/build.gradle.kts` |
| Splash colours | `app/src/main/res/values/colors.xml` |
| Icon | `app/src/main/res/drawable/ic_launcher_*.xml` and `mipmap-*` PNGs |
| Calculator pages | `app/src/main/assets/www/*.html` (same files as the website) |
| Version for updates | `versionCode` / `versionName` in `app/build.gradle.kts` |

## Play Store release

The debug APK is fine for sharing directly. For the Play Store you need a signed
release build (AAB). Create a keystore in Android Studio (**Build → Generate Signed
Bundle / APK**) and keep the `.jks` file and passwords safe — you need the same key
for every future update.
