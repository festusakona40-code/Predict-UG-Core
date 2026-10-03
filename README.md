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
