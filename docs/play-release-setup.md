# Play Store release setup

Release builds are signed from GitHub secrets. Every push to `main` builds the app, publishes a GitHub
release, and uploads the AAB to the Play Console **Internal testing** track. Promote it to Production
yourself in Play Console: nothing reaches store users without your click.

## 1. One-time: first upload is manual

Google's API cannot create an app or make its very first upload. If SpendTracker
(`com.spendtracker.app`) has never been uploaded:

1. Play Console → **Create app**, complete the store listing, content rating, data safety form and
   privacy-policy URL. The app reads bank notifications, so the listing and data-safety form must say
   these are processed on-device only.
2. **Test and release → Internal testing → Create new release**, upload an AAB built by CI
   (from the GitHub release or the run's `SpendTracker-Release-Files` artifact), add testers, roll out.
3. Choose **Play App Signing** when prompted (recommended).

After that, CI uploads every new version automatically.

## 2. Signing key → GitHub secrets

The repository is public and used to contain `release-keystore.jks` with its password, so that key
must be treated as **compromised**.

Pick one:

- **A. New upload key (recommended when the app uses Play App Signing).** Create a key on your own
  computer:
  ```bash
  keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
  ```
  If the app already exists in Play Console, request an upload-key reset there
  (**Setup → App signing → Request upload key reset**) and give Google the certificate from
  `keytool -export -rfc -keystore upload.jks -alias upload -file upload_certificate.pem`.
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

## 3. Play upload credentials

1. Google Cloud console (for the project linked to Play Console) → enable **Google Play Android
   Developer API** → **IAM → Service accounts → Create**; create a JSON key and download it.
2. Play Console → **Users and permissions → Invite new users** → the service account's email → grant
   **Release apps to testing tracks** (and **Release to production** only if you later want CI to
   publish to production) for SpendTracker.
3. GitHub secret `PLAY_SERVICE_ACCOUNT_JSON` = the full contents of the JSON key file.

Never paste keys or passwords into chats, issues or commits.

## 4. Releasing

- Bump `versionCode` (must always increase) and `versionName` in `app/build.gradle.kts`, and update the
  release tag/notes in `.github/workflows/build-apk.yml`.
- Merge to `main`. CI tests, signs, publishes the GitHub release and uploads to Internal testing.
- Play Console → Internal testing → check the build → **Promote release → Production**.
- To re-run an upload for the current `main` (e.g. after adding secrets), run the workflow manually from
  the Actions tab on `main`.
