# Predict UG Core

Clean-history public consumer build mirror for Predict UG.

This repository contains only the source needed to build and test the public consumer Android app. The production database, migration history, payment-provider operations, merchant SMS implementation, signing material, deployment credentials, and private operational runbooks remain in the private Predict UG repository.

## Public/private boundary

- No production secrets or signing keys belong here.
- No merchant SMS source belongs here.
- No production Supabase migrations belong here.
- Real-money feature flags are controlled by the private backend and are not enabled by this repository.
- CI verifies that the consumer APK does not request SMS-reading permissions.

## Build

GitHub Actions is the canonical public build path.

This repository is public for transparent consumer builds and CI. No open-source license has been granted yet.

## Verified practice beta 0.7.86

[Download Android practice beta 0.7.86](https://raw.githubusercontent.com/festusakona40-code/Predict-UG-Core/3cd131822afa8cda882be2636c78008ba665ad64/predict-ug.apk).

This consumer/code86 release opens the audited bundled frontend and local SDK at the existing trusted HTTPS asset origin. Update keeps that bundled interface when no newer verified APK is advertised. Native bridge/origin trust is unchanged. Real money remains disabled.

Verified public main build: [38854939827](https://github.com/festusakona40-code/Predict-UG-Core/actions/runs/38854939827). Four native JUnit tests passed, including the launch policy; APK/AAB, no-SMS permission boundary and signer verification passed. Full downloaded APK size48,076,511bytes and SHA256`a3e6b48520beaeec7d7ad7ad04ce93f8dc8bb0e35aa6f75d772a165a4d96d4c5` matched CI metadata; bundled HTML and SDK match the audited source exactly.

The existing Android Debug beta signing certificate is retained. This is a controlled practice beta, not a permanent production-signed release. Returning Render-origin users may need to sign in with their existing account/password; sessions are not migrated across origins. Physical install/upgrade, phone OTP and full customer prediction acceptance remain unverified.

Beta85 is retained unchanged at tag`public-beta-v0.7.85`; its Update action could reopen older Render HTML, so use86. The Render browser site is a separate delivery surface; APK verification does not certify its current source revision.
