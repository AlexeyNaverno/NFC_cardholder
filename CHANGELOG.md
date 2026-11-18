# Changelog

All notable changes to this project are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project
follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.0.2] - 2026-11-18

Power tools for MIFARE Classic infrastructure, researched from public sources
(NXP MIFARE4Mobile notes, NCI configuration references, open reverse engineering projects).

### Added

- Slot details: an **adb recipe** that patches the NFC-A listen UID (`LA_NFCID1`, the TLV
  `33 <length> <uid>` inside `NXP_CORE_CONF` / `NFA_DM_START_UP_CFG`) to the UID of the copied
  card, with backup, verification and restore steps. Running it from a computer with `adb root`
  makes the phone answer the NFC-A anticollision like a UID-only card; the app never writes
  system files itself.
- Header: **secure element diagnostics** through the Open Mobile API (`android.se.omapi`,
  Android 9+): lists the `eSE`/`SIM` terminals, probes a session and a basic channel, and
  explains that MIFARE applet personalization stays under OEM access rules.
- JVM unit tests for the adb recipe generator.

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
