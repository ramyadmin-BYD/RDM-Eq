# Releasing a new RDM Eq version

Releases are published on GitHub: https://github.com/ramyadmin-BYD/RDM-Eq/releases
Installed copies of RDM Eq (1.1.0 and later) find new releases with **Check for Updates**.

## One-time facts

- **Application ID** `com.rdm.eq` and the **signing key** `keystore/rdm-eq-release.jks` must never change.
  Android only installs an update over the existing app if both match. The script refuses to publish an APK signed
  with any other key (it checks against the public fingerprint in `release-signing-cert.sha256`).
- The key and `keystore.properties` are **not** in Git (see `.gitignore`). Keep a backup outside this folder.
- `version.properties` holds `VERSION_CODE` (an integer that must go up every release) and `VERSION_NAME` (shown to users).

## Every release (about 2 minutes)

1. Make and test your code changes, then commit and push them:
   ```bash
   git add -A && git commit -m "Describe the change" && git push
   ```
2. Write release notes in `docs/release-notes/v1.2.0.md` (Markdown, shown inside the app's update dialog) and commit it.
3. Run:
   ```bash
   scripts/release.sh 1.2.0 docs/release-notes/v1.2.0.md
   ```
   The script:
   1. raises `VERSION_CODE` by 1 and sets `VERSION_NAME`;
   2. runs the unit tests and builds the signed release APK;
   3. checks the signature, signing certificate, package name, versionCode and versionName;
   4. writes `RDM-Eq-1.2.0.apk`, `SHA256SUMS.txt` and `rdm-eq-update.json` (version, SHA-256, size, certificate) to `dist/v1.2.0/`;
   5. commits the version bump, tags `v1.2.0`, pushes, and creates the GitHub Release with those three files.
4. Installed apps see the update on their next check: immediately with **Check for Updates**, or automatically within 12 hours.

Options:
- `--dry-run` builds and checks everything but publishes nothing (and restores `version.properties`).
- `--beta` publishes a pre-release, offered only to users who enabled **Include beta versions**.

## What the in-app updater accepts

A release is offered only if it is **published** (not a draft), **not a pre-release** (unless beta is on), contains
`rdm-eq-update.json` and the APK it names, and the metadata says `com.rdm.eq`, a higher `versionCode`, and the same
signing certificate as the installed app. After download, the file's size, SHA-256 (and GitHub's own asset digest,
when available), package name, version and signing certificate are checked again before Android's installer opens.

## Fixing a bad release

Don't edit or re-upload a published APK. Publish a new version with a higher number instead.
If a release must be withdrawn, delete it on GitHub (or mark it as pre-release); the updater ignores it from then on.

## Optional: publish from GitHub Actions

`.github/workflows/release.yml` can build, sign and publish from GitHub's servers (Actions tab → Release → Run workflow).
It needs the signing key stored as encrypted repository secrets (`RDM_KEYSTORE_BASE64`, `RDM_STORE_PASSWORD`,
`RDM_KEY_ALIAS`, `RDM_KEY_PASSWORD`). Until you add them, it stops without doing anything. The default and recommended path is
`scripts/release.sh`, which keeps the key on your Mac.
`.github/workflows/ci.yml` runs the unit tests and a debug build on every push (no secrets needed).
