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
