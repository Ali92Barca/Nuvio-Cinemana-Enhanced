# Bingers — Android mobile experimental integration

The Android mobile overlay now contains the linking UI and a completion-only watch-history sync client. It is not yet verified against a real free Bingers account. No paid entitlement is assumed or bypassed.

## User flow

In Settings → Tracking, open Bingers, request a sign-in email, copy the fresh official magic-link URL without opening it, and paste it **inside the application**. Never send magic links, cookies or passwords in chat. Successful linking validates the session and stores it locally under Android Keystore encryption. Enable automatic synchronization separately after linking.

The integration follows Nuvio's existing completion threshold: a STOP event at 80% or above queues the movie/episode. START, PAUSE and seek reconciliation do not write Bingers history. This is completed-watch synchronization, not live playback progress or a full Bingers library provider.

- Match Bingers catalog entities by external IDs (TMDB/IMDb); otherwise use a unique title-and-year match with no conflicting identity.
- Resolve the exact catalog season/episode ID; never send a show ID as an episode ID.
- Read remote history before a write. Already-watched entries are skipped without incrementing play counts.
- Persist a batch and operation ID before sending it; retry exactly the same batch after network interruption.
- Store pending items encrypted per Nuvio account/profile; keep unresolved titles separately so one mismatch does not block other shows.
- Authentication/subscription/permission failures disable further automatic writes and show an error. HTTP 429 and offline failures retain pending work for manual retry.
- Re-linking the same Bingers account preserves pending work; linking a different account clears the old queue. Disconnect clears local credentials/queues without deleting remote history.

No credentials are sent to a relay, catalog host, analytics logger or GitHub. Dedicated Bingers HTTP transport refuses redirects. Session material and queues are encrypted at rest with per-profile authenticated encryption.

## Source and limitations

Protocol reference: [Scroblarr Bingers integration](https://github.com/0xSysR3ll/scroblarr/tree/develop/packages/backend/src/integrations/bingers), MIT, copyright 2026 0xsysr3ll. See [license notice](SCROBLARR_NOTICE.txt).

Automatic rewatch counts, ratings, watchlists, full library import, live progress, background Android service and TV support are not implemented. Existing Trakt/Simkl integrations continue independently.

## Acceptance still required

The user must link their own Bingers account in the APK and verify a genuinely completed movie/episode. A successful session check proves authentication only, not free-account write permission. A service response requiring payment/permission is surfaced without attempting to work around it. Fixture tests do not certify live account access.
