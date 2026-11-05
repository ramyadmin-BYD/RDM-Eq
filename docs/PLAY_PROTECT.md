# Google Play Protect warning ("Harmful app blocked")

## What it is

Google Play Protect runs on Android devices that have Google Play services. It scans apps that were not installed
from the Play Store. RDM Eq is new, comes from an unknown developer certificate, and can download and install its own updates
(`REQUEST_INSTALL_PACKAGES` + internet). That combination resembles "dropper" malware to Google's automated scanner,
so it may warn or block, even though RDM Eq only ever installs its own APK, verified by checksum and signing certificate.

**This cannot be switched off from inside the app, and RDM Eq does not try to hide from or trick the scanner.**
The warning goes away when Google reviews the app or the developer becomes known to Google.

Many BYD DiLink head units have no Google Play services, so no Play Protect. On those units this warning never appears.

## What changed in RDM Eq 1.2.0 to lower the risk

- Removed `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, a permission Google treats as sensitive. The Status screen now opens
  the normal battery settings list instead, which needs no permission.
- Removed the unofficial `QUICKBOOT_POWERON` boot action (the standard boot event is kept).
- Remaining permissions are only the ones the features need: audio effects, foreground service, notifications,
  start after boot, internet (update check only) and install packages (in-app updates only).

## What you can do (in order)

### 1. Ask Google to review the app (free, about 10 minutes)

1. Open Google's developer guidance page:
   https://developers.google.com/android/play-protect/warning-dev-guidance
2. Read the checklist on that page, then click **File a Play Protect appeal**.
3. Fill in the form with your details and the information below. Attach or link the APK from the latest release.

**Text you can paste into the appeal (English is required):**

> App name: RDM Eq
> Package name: com.rdm.eq
> Signing certificate SHA-256: feb2d3fb7945cf0248738c1176d8ee4f08cdcdc998b2d6cfbe0670bffabee3c0
> Download: https://github.com/ramyadmin-BYD/RDM-Eq/releases/latest
> Source code: https://github.com/ramyadmin-BYD/RDM-Eq (public)
>
> RDM Eq is an open-source audio equalizer for BYD DiLink car head units. It uses Android's public AudioEffect
> APIs (DynamicsProcessing / Equalizer) on audio sessions that media apps announce, and a foreground service to receive
> those announcements.
>
> It requests REQUEST_INSTALL_PACKAGES and INTERNET only for its in-app updater. The updater reads this project's
> public GitHub Releases over HTTPS, downloads only RDM Eq's own APK, verifies its SHA-256 checksum and that it is signed with
> the same certificate as the installed app, and then uses PackageInstaller, which always shows Android's user
> confirmation. It never installs any other app, never installs silently, and contains no third-party SDKs, analytics,
> ads or remote code loading.
>
> The app was blocked as harmful during an update installation. We believe this is a false positive and ask for a review.

### 2. If the warning appears while you install by hand

- If the dialog offers **More details → Install anyway**, that installs the app normally.
- If it only offers **Got it** ("Harmful app blocked"), the app cannot be installed on that device until Google's review
  changes the verdict. Turning off "Scan apps with Play Protect" in the Play Store settings is a choice only the
  device owner should make. It lowers protection for *all* apps, so it is not recommended as a routine step.

### 3. Longer term

Google is introducing developer identity verification for apps installed outside the Play Store. Registering as a
verified developer with the same signing key, or publishing RDM Eq on Google Play (even as a closed test), makes
Play Protect treat the app as coming from a known developer. Check Google's current rules on
https://developer.android.com before choosing.
