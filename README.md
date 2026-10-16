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

## Practice beta85 delivery candidate

Version 0.7.85 / code85 starts the audited bundled frontend and local SDK from
the existing trusted HTTPS asset origin. This explicit consumer-beta exception
avoids waiting for a remote HTML deployment; backend data and money gates remain
authoritative. The optional Render route retains the existing exact-origin guard.

Returning users may need to sign in with their existing account/password because
Render-origin sessions/localStorage do not transfer to the bundled asset origin.
The physical APK84→85 upgrade/session flow is unverified. This candidate has no
verified APK artifact until the consumer Android workflow completes; existing
published artifact links above remain evidence for their stated older versions.

## Practice beta86 delivery candidate

Version 0.7.86 / code86 keeps Update on the existing bundled HTTPS asset URL
when release metadata advertises no newer APK; market query and hash remain.
A newer verified binary still follows the existing APK update route. Beta85 is
not the final promoted candidate because its Update action could open older
Render HTML. Its published artifact remains immutable; this fix needs beta86.

No session migration is performed. Returning Render-origin users may need to
sign in with their existing account/password. Physical85→86 upgrade/session and
prediction acceptance remain unverified. Beta86 native CI/artifact verification
is pending; previous artifact evidence applies only to its original version.
