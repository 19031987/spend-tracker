# Release setup

Release builds are signed from GitHub secrets. Every push to `main` runs the unit tests, builds a
signed APK and publishes it as a GitHub release tagged `v<versionName>`. Nothing is uploaded to Google
Play.

## 1. Signing key → GitHub secrets

The repository is public and used to contain `release-keystore.jks` with its password, so that key
must be treated as **compromised**.

Pick one:

- **A. New key (recommended).** Create a key on your own computer:
  ```bash
  keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
  ```
  Note: an APK signed with the new key cannot be installed over a sideloaded APK signed with the old
  key: Android requires uninstalling first, which deletes the app's local data. The app can export
  a full backup (Settings → Data → Export backup, v2.2.0+) and import it after reinstalling, so export first (or choose B) if your
  on-phone history matters.
- **B. Keep the old key for now.** Recover it from git history
  (`git show f6ecb60:release-keystore.jks > release-keystore.jks`) and put it in the secrets below.
  Nothing changes for installed copies, but the key stays public, so move to A soon.

Then add these in GitHub → **Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | output of `base64 -w0 upload.jks` (macOS: `base64 -i upload.jks`) |
| `RELEASE_STORE_PASSWORD` | keystore password |
| `RELEASE_KEY_ALIAS` | key alias (`upload`, or `spendtracker` for the old key) |
| `RELEASE_KEY_PASSWORD` | key password |

Without `RELEASE_KEYSTORE_BASE64`, pull-request builds still compile and test (unsigned output), but a
push to `main` fails on purpose rather than publishing an unsigned release.

For local signed builds, create an untracked `keystore.properties` in the repo root:
```
storeFile=/absolute/path/to/upload.jks
storePassword=…
keyAlias=upload
keyPassword=…
```

Never paste keys or passwords into chats, issues or commits.

## 2. Releasing

- Bump `versionCode` (must always increase) and `versionName` in `app/build.gradle.kts`, and add the
  new version's notes at the top of the release `body` in `.github/workflows/build-apk.yml`.
- Merge to `main`. CI tests, signs and publishes the GitHub release; download the APK from its Assets.
- To re-run the release for the current `main` (e.g. after adding secrets), run the workflow manually
  from the Actions tab on `main`.
