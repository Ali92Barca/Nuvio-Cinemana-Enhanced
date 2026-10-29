# Bingers experimental integration — Mobile only

Status: **research complete for initial integration design; free-account write access not yet verified**.

## Verified source
- Scroblarr: https://github.com/0xSysR3ll/scroblarr (branch `develop`).
- Docs: `website/docs/configuration/bingers.md` describe email magic-link login at `https://bingers.app/mobile-signin`, session cookies, and watch-history sync.
- Client: `packages/backend/src/integrations/bingers/BingersClient.ts` uses `https://api.bingers.app/sync/pull` and `/sync/push` for watched entries, and supports rewatch play counts.
- Scroblarr is MIT licensed. Preserve copyright notice if incorporating code.
- **Unknown:** whether a free Bingers account can perform these sync operations. Login alone does not prove write access.

## Proposed in-app flow
1. User enables optional Bingers experimental integration.
2. Open official Bingers sign-in page in external browser.
3. Complete login via official email magic link; keep credentials/tokens private.
4. Validate authenticated session and permission to sync on a free account.
5. On completed playback, resolve movie or episode identity, deduplicate, and send a watched entry only if permitted.
6. Report clear success, authentication failure, or subscription-required response. Do not bypass subscription restrictions.
7. Disconnect clears local session; history remains on Bingers.

## Security
- Never request users to send magic links, cookies or passwords through chat.
- Never log or commit session cookies; protect local session material.
- Do not send credentials to a custom relay server.
- Avoid altering watch history merely to test connectivity; use an explicitly authorized test title when write verification is necessary.
- Feature stays disabled by default until tested.

## Scope
This is optional and **must not block** completion of the primary Cinemana Mobile app. No TV implementation yet.
