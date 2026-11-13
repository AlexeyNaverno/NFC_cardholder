# Privacy policy

NFC Cardholder does not collect, transmit or sell any personal data. The app has no network
permission at all: everything happens on the device.

## What the app stores

The data you copy from cards and tags — UID, technology data, NDEF content, application answers and
MIFARE memory that a known key unlocked — is written to a single JSON file inside the private
storage of the app (`/data/data/io.github.alexnaverno.nfccardholder/files/cards.json`). Only the
app itself (and the device owner with root access or a full device backup) can read it.

Backups to cloud services are disabled in the manifest (`android:allowBackup="false"`), so the file
never leaves the device through the Android backup mechanism. Deleting a slot removes its data from
the file; uninstalling the app removes the file completely.

## What the app transmits

While a slot is marked as transmitted, the app answers NFC readers that select one of the
registered application identifiers. The answers are exactly the data of the copied card; no other
information about you or your device is sent, and the transmission stops as soon as you clear the
slot or uninstall the app.

## Permissions

| Permission | Why |
| --- | --- |
| `android.permission.NFC` | Read tags and emulate cards. |
| `android.permission.BIND_NFC_SERVICE` | Declared on the emulation service so that only the NFC system can bind it. |

There are no other permissions, no analytics, no advertising, no crash reporting and no third party
tracking libraries.

## Questions

Open an issue at <https://github.com/AlexeyNaverno/NFC_cardholder/issues>.
