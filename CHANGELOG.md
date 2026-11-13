# Changelog

All notable changes to this project are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project
follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.0.1] - 2026-11-13

First public pre-release.

### Added

- One screen with ten slots; each slot has three actions: **copy**, **transmit**, **delete**.
- Copy: reader mode over NFC-A/B/F/V that stores the UID, the technology list, ATQA/SAK,
  historical bytes, the NDEF message (as data and as text), every ISO-DEP application that answers
  a SELECT, and the MIFARE memory a known key unlocks (Classic sectors and Ultralight pages).
- Transmit: a `HostApduService` that answers a reader with the recorded answers of the copied
  card, replays recorded APDU pairs, answers SELECT by AID with the stored FCI, and serves copied
  NDEF as an NFC Forum Type 4 Tag. Only one slot is transmitted at a time.
- Runtime AID registration on top of the statically declared AIDs, with a clear message when
  Android refuses it.
- Slot details dialog: rename the card, add an AID by hand, inspect the raw data.
- Russian and English interface.
- Gradle build with a version catalog, JVM unit tests for the APDU and emulation logic,
  GitHub Actions build and release workflows, generated launcher artwork, F-Droid metadata
  (fastlane layout) and a privacy policy.

### Known limitations

- Android can only emulate ISO-DEP (ISO 14443-4) applications: MIFARE Classic, Ultralight and NTAG
  data is copied but cannot be transmitted.
- The phone always presents its own UID in card emulation mode.
- Payment cards are not supported.
