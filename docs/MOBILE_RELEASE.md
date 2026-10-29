# Mobile release and updates

The application now identifies as Nuvio Cinemana and checks Releases in
Ali92Barca/Nuvio-Cinemana-Enhanced. A workflow artifact alone is not an update:
a published, non-prerelease Release must contain an APK. The manual
`Publish mobile release` workflow tests, builds with a persistent signing key,
and publishes a universal APK plus its SHA-256 checksum. It runs only on main.
It never generates a replacement signing key silently.

Before the first release, the repository owner must configure Actions secrets:
- MOBILE_KEYSTORE_BASE64: base64 representation of the private release keystore.
- MOBILE_STORE_PASSWORD: keystore password.
- MOBILE_KEY_ALIAS: signing alias.
- MOBILE_KEY_PASSWORD: private key password.

Keep an offline backup of the keystore and passwords. Never commit them or send
them in chat. Configure secrets through GitHub Settings → Secrets and variables
→ Actions. The connected GitHub tools in this session cannot configure secrets.
Then run Actions → Publish mobile release → Run workflow on main.

`mobile-version.properties` controls both displayed and Android package versions.
For each release increase versionCode (first production code119) and versionName,
and update RELEASE_NOTES.md before running the workflow. Existing release tags
are not overwritten. The updater is bound to main-channel releases in this repo.

Package ID remains com.ali92barca.nuvio.cinemana.debug to preserve the installed
app's storage namespace. A release build is not debuggable despite that suffix.
Updating an existing test installation also requires the identical signing
certificate. Earlier CI debug keys were not explicitly preserved by this project;
compatibility with those APKs is unverified. Do not uninstall an existing app
without exporting/backing up its data. Once the first stable key is selected,
use that same key for every subsequent release.

Source project links, licenses, contributors and upstream version attribution
remain in the repository/credits. The settings footer now names this fork.

## Creating a signing key locally, if no existing project key exists

Run Java's keytool in a private folder outside the git checkout. It prompts for
passwords; choose strong passwords and keep their backups with the keystore.

```sh
keytool -genkeypair -storetype JKS -keystore nuvio-mobile-release.jks -alias nuvio-mobile -keyalg RSA -keysize 3072 -validity 10000
```

On Windows PowerShell, copy the one-line base64 keystore directly to the clipboard:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path .\nuvio-mobile-release.jks))) | Set-Clipboard
```

Paste that value only into GitHub's MOBILE_KEYSTORE_BASE64 secret; use nuvio-mobile
for MOBILE_KEY_ALIAS, and the passwords from keytool for the other two secrets.
Do not paste the base64 keystore or passwords into an issue, chat or source file.
A newly generated key does not match an older debug installation automatically.

## Validation

Branding, updater-repository defaults, version 1.0.0/build119 and navigation changes
passed 43 JVM integration tests and Android compilation in workflow 40918817703:
https://github.com/Ali92Barca/Nuvio-Cinemana-Enhanced/actions/runs/40918817703
No APK was produced in that validation run. The signed release workflow has not
been executed because the persistent signing secrets have not been supplied.
Actual visual review and an end-to-end published-release update remain pending.
