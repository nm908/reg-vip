# Touch Assist

Android project. The repository root must contain `app/`, `build.gradle.kts`, `settings.gradle.kts`, and `.github/`. Do not upload this folder inside another `TouchAssist/` directory.

## Build APK with GitHub Actions
1. Upload the *contents* of this ZIP to the root of your GitHub repository (not the ZIP itself and not a nested `TouchAssist/` folder).
2. Open the Actions tab and run **Build Touch Assist APK**, or push a commit to `main`/`master`.
3. Open the completed run and download `TouchAssist-debug-apk` under Artifacts.

This workflow builds a debug APK artifact; it does not publish a public GitHub Release.
