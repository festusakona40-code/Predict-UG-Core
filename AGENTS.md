# AGENTS.md — Predict UG Public Consumer Mirror

## Purpose

This repository is the clean-history public CI/build mirror for the Predict UG consumer Android app.

The private repository `festusakona40-code/Predict-Ug` remains canonical for production backend, payments, merchant ingestion, migrations, signing material and private operations.

## Hard boundary

Never add any of the following here:

- Supabase production migration history
- service-role or secret keys
- provider credentials or merchant codes
- production signing keys/keystores
- merchant SMS ingestion implementation
- private operational runbooks
- customer/private merchant data

Do not weaken `Public Source Guard` to make CI pass.

## Work loop

1. Inspect current source and latest CI result.
2. Make the smallest consumer-safe change.
3. Run/trigger the public source guard and Android consumer CI.
4. Inspect exact failing step/log if anything fails.
5. Fix root cause and rerun.
6. Verify APK/AAB presence, SMS-permission isolation, checksums and signing-certificate fingerprint.
7. Keep documentation/provenance current.

## Android contract

- Consumer app only.
- The consumer app must not request SMS-reading or SMS-receive permissions.
- No merchant-only launcher/components.
- Java 17.
- Android API 36.
- Gradle 9.6.
- Node 22 for bundled browser dependency preparation.
- Release promotion requires a dedicated protected production signing identity; the public beta/debug signing identity is not a production release key.

## Money safety

This mirror does not authorize or enable real money. Production money, deposits, withdrawals and cash-staking remain controlled by the private backend and external legal/provider gates.
