# The app is not obfuscated in release builds (isMinifyEnabled = false), so no
# keep rules are strictly required. They are kept here in case shrinking is
# enabled later: the HCE service is instantiated by the system through the
# manifest and must never be renamed or removed.
-keep class io.github.alexnaverno.nfccardholder.hce.CardEmulatorService { *; }
-keepclassmembers class io.github.alexnaverno.nfccardholder.model.** { *; }
