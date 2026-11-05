# RDM Eq — Feasibility & Compatibility Report

Version 1.0.0 · 1 November 2026

**Bottom line**
- RDM Eq processes real audio from **other apps** using Android's built-in Dynamics Processing effect.
  On an Android emulator this was **measured**: a separate app's audio changed by the expected amount at each band.
- It works for apps that announce their audio session. **Spotify and most music players are expected to work; YouTube is not.**
  An experimental "Global mode" may also cover YouTube, but only if BYD's audio build allows it.
- **Nothing has been tested on a real BYD head unit yet.** Every car-specific statement below is marked as unverified,
  and section 6 gives a test plan.

---

## 1. Stage 1: feasibility

### 1.1 The target platform

| Question | Finding | Status |
|---|---|---|
| What is DiLink 5? | BYD's own infotainment system built on **standard Android (AOSP), not Android Automotive OS**. Community projects tested on DiLink 5.1 report **Android 13**. | Researched, not verified on your car. The Status screen shows the real version and AAOS flag. |
| Can ordinary apps be installed? | Sometimes. Owners sideload APKs via USB or browser on many firmwares. Some 5.0 builds are reported locked. | Unverified |
| Which audio effects exist? | AOSP ships Dynamics Processing, Equalizer, Bass Boost, Virtualizer and Loudness Enhancer. Manufacturers can remove or replace them. | Unverified. RDM Eq probes this at runtime (Status → Supported audio effects). |
| Can effects attach to other apps' sessions? | Yes on stock Android, when the app shares its session ID (verified on emulator, §4). | Verified on emulator only |
| System-wide audio-processing API from BYD? | None published. No public BYD SDK. RDM Eq does not invent one. | No public API found |
| Auto-start after boot? | Standard `BOOT_COMPLETED` works on stock Android (verified on emulator). Head units often **sleep instead of rebooting**, so the app may simply keep running. Some vendor builds block boot receivers for third-party apps. | Emulator ✓ / car unverified |
| Required permissions granted to normal apps? | Yes. `MODIFY_AUDIO_SETTINGS`, foreground service and boot receiver are all "normal" permissions. Notifications need one tap on Android 13. | ✓ |

### 1.2 The approaches that were evaluated

| # | Approach | Verdict |
|---|---|---|
| 1 | **AudioEffect APIs** (Dynamics Processing, Equalizer, Bass Boost, Virtualizer) | ✅ **Used.** Dynamics Processing (API 28+) gives exact 7-band control, a preamp, a limiter and per-channel gain. The classic Equalizer is the fallback. |
| 2 | **Effects attached to other apps' audio sessions** (via the standard `OPEN/CLOSE_AUDIO_EFFECT_CONTROL_SESSION` broadcasts) | ✅ **Primary mode.** This is the official, non-root way. It only works for apps that send the broadcast. |
| 3 | **System-wide effect on the global mix (session 0)** | ⚠️ **Offered as experimental "Global mode".** Android deprecated it, and whether it changes the sound depends on the manufacturer's audio build. It attached on the emulator. On the car it must be checked by ear. |
| 4 | **Android Automotive / BYD audio APIs** (CarAudioManager, vendor DSP) | ❌ DiLink is not AAOS, so CarAudioManager isn't available to apps. No public BYD DSP API exists. Nothing was invented. |
| 5 | **Root / privileged system app** | ❌ Not implemented. Documented in §5 as the route to true system-wide EQ. |
| — | Capturing other apps' audio (AudioPlaybackCapture) and replaying it | ❌ Rejected. It would duplicate audio, adds latency, and needs per-session screen-capture consent. Apps can opt out, and it would not *replace* the original sound. |
| — | Microphone-based "EQ" | ❌ Rejected as instructed. |

**Why an ordinary app cannot equalise all audio:** Android isolates each app's audio. A normal app may insert
an effect only into an audio session whose numeric ID it knows. Apps reveal that ID voluntarily through a broadcast.
Making the effect apply to the final output of the whole system requires either the manufacturer to honour
global-mix effects, or a system-level privilege that only BYD (or root) can grant.

---

## 2. Stage 2: architecture

```
 Media app (Spotify, local player…) ──OPEN_AUDIO_EFFECT_CONTROL_SESSION(session id)──┐
                                                                                     ▼
 EqService (foreground, receives broadcasts) ──► AudioEngine ──► DynamicsProcessing on that session
                                                   │               (24-band pre-EQ, input gain, limiter)
 UI (Compose) ◄──► EqController ◄──► Settings/Preset repositories (SharedPreferences, JSON)
                                                   └─► fallback: classic Equalizer on that session
                                                   └─► optional: Global mode on session 0
```

- **The 7 control points** are interpolated on a log-frequency axis onto 24 Dynamics Processing bands from 20 Hz to 20 kHz.
  The labels are therefore the true centre frequencies. If the classic Equalizer is in use, the curve is sampled at that device's
  real band centres, and those are listed on the Status screen.
- **Honest status.** An attached stream is called "processing" only when the effect exists, owns control,
  is enabled, its parameters were **read back from the audio system and matched**, and audio is playing.
- **Coexistence.** RDM Eq uses effect priority 100 to take control when a player's own EQ competes.
  It shows "Another audio app has priority" if it loses control.
- **No network, no root, no hidden APIs, no invented vendor APIs.**

---

## 3. Per-app compatibility

| App | Announces its session? | Expected with RDM Eq | Verified on BYD? |
|---|---|---|---|
| **Spotify** | Yes. Spotify sends the standard broadcast; this is how third-party EQs work with it. | **Processed** (session mode) | ❌ Not tested in car |
| **YouTube Music** | Inconsistent across versions/devices | Possibly processed; otherwise **bypasses** unless Global mode works | ❌ Not tested in car |
| **YouTube** | No | **Bypasses** RDM Eq. Processed only if Global mode works on DiLink. | ❌ Not tested in car |
| **Local music players** (VLC, Poweramp, Musicolet, most Media3/ExoPlayer apps) | Usually yes | **Processed** | ❌ Not tested in car |
| **RDM Test Player** (diagnostic, included) | Yes | **Processed: measured** | Emulator ✓ / car pending |
| **RDM Eq built-in test signal** | Own session | **Processed: verified** | Emulator ✓ / car pending |
| BYD's own radio / music / Bluetooth audio, Android Auto & CarPlay projections, navigation prompts | Unknown | Likely bypass (system apps often skip the broadcast) | ❌ Not tested |

Tip: a media app's own **"Equalizer"** button, where it has one (e.g. Spotify → Settings → Equalizer), opens RDM Eq with that
app's session. RDM Eq handles that too.

---

## 4. Stage 4: test results

Environment: macOS, Android Emulator **Android 17 (API 37)** set to a car-like display (**2560×1600 landscape**, 320 dpi),
plus phone sizes. Both the debug and the signed release APK were tested.

| # | Test | Result |
|---|---|---|
| 1 | Build | ✅ Debug and signed release APK build cleanly (release 2.2 MB, R8-shrunk). |
| 2 | Install & launch on emulator | ✅ Both builds. No crashes in logcat across all screens. |
| 3 | All seven faders | ✅ Drag (e.g. 60 Hz dragged to −6.0), +/−, double-tap reset. Values shown and applied. Each band **measured** (see below). |
| 4 | Preset selection & switching | ✅ Quick chips and Presets screen. The active preset is highlighted, and "· edited" shows unsaved changes. |
| 5 | Custom presets save / rename / delete / restore built-in | ✅ Saved "Song L Highway" via the UI. Rename, delete, restore, export/import covered by unit tests. |
| 6 | Settings survive restart | ✅ After force-kill: preset, curve and custom preset intact. ✅ After **full reboot**: preset restored. |
| 7 | Unsupported features reported accurately | ✅ Balance/limiter disable themselves with the classic EQ. Virtualizer and front/rear fader are explained, not offered. Loudness is marked unverified. |
| 8 | Effect cannot be created | ✅ Announcing invalid sessions made Android refuse both effects (error −3). RDM Eq logged it, showed "Effect could not be applied — see Status", and did not crash. |
| 9 | Orientation & resizing | ✅ 2560×1600 landscape, 1600×2560 portrait, phone 1080×2400 portrait and landscape. A narrow layout and scrolling were added after phone sizes looked cramped. |
| 10 | Crashes / runtime errors | ✅ None. Fixed during testing: stale sessions from killed apps (now pruned) and a misleading "Processing" status when nothing was playing (now checks playback). |
| 11 | Actual audio processing | ✅ **Measured on emulator** (below). |
| 12 | Cross-app with YouTube & Spotify **on the BYD** | ❌ **Not done: no access to the car.** See §6. |
| — | Unit tests | ✅ 26/26 pass (curve maths, headroom/clipping, band mapping, balance, preset library, CRUD, persistence, corruption fallback, import/export). |
| — | Auto-start after boot | ✅ Emulator: with the app untouched, `BOOT_COMPLETED` arrived about 54 s after boot and the foreground engine started. |
| — | Resource use | ~0 % CPU for RDM Eq while processing; the DSP runs inside Android's audio server. About 71 MB memory (debug build). |
| — | Global mode | ✅ Dynamics Processing attached to session 0 with control on the emulator. Not measured; the car needs an ear test. |
| — | Classic Equalizer fallback | ✅ Measured: bass and treble boosts shaped the spectrum as expected. |

### 4.1 Measured cross-app processing (emulator)

A **separate app** (RDM Test Player) played white noise on its own session and announced it.
RDM Eq attached Dynamics Processing to it. The final output mix was measured with Android's Visualizer
(an 8-bit FFT, so deep cuts bottom out at its noise floor). Values are average band energy in dB.

| RDM Eq setting | 40–200 Hz | 0.8–1.25 kHz | 8–16 kHz |
|---|---|---|---|
| EQ off (bypass) | 7.0 | 7.4 | 8.2 |
| Flat, EQ on | 7.6 | 8.3 | 8.2 |
| 60 Hz **+12 dB** | **16.4** | 7.5 | 8.2 |
| 1 kHz **−12 dB** | 8.6 | **0.6** | 8.2 |
| 15 kHz **+12 dB** | 7.9 | 7.5 | **17.0** |
| Rock (auto headroom) | 7.0 | **3.8** | 7.6 |
| Podcast and Speech | **1.8** | 7.1 | 5.2 |

Each band moves only its own region, by about the expected amount. This confirms the effect really changes
another app's audio on stock Android.

---

## 5. Unsupported features & known limitations

- **Apps that don't announce a session (YouTube, likely BYD system audio) bypass RDM Eq** unless Global mode works on DiLink.
- **Global mode** uses a deprecated Android mechanism. On many devices it attaches but has **no audible effect**,
  so always confirm by ear with the EQ switch.
- **Loudness compensation** follows *Android's* media volume. If the car's volume knob controls the amplifier over the vehicle bus
  instead, it won't follow the knob. The Sound screen shows the volume it reads, so you can check.
- **Balance** applies to stereo streams only, and is unavailable with the classic Equalizer.
- **No front/rear fader** (not reachable through Android effects). **No stereo widening**: the Virtualizer targets headphones and has no reliable car effect.
- **The limiter protects only RDM Eq's digital stage.** It cannot prevent distortion in BYD's amplifier or speakers.
- **Killed apps** don't announce session closure. RDM Eq keeps at most 2 sessions per app and releases older ones.
- **Background survival** on DiLink is unverified. BYD may kill background apps. The foreground notification and battery-optimisation exemption help.
- **BYD Factory Sound Reference** is neutral (flat). BYD's tuning is not published, so it isn't imitated.

### What true system-wide EQ would require
Any one of these:
1. **BYD honouring global-mix effects (session 0)**: test RDM Eq's Global mode. If it audibly works, RDM Eq is already system-wide.
2. **Privileged/system install**: RDM Eq signed with BYD's platform key or placed in `/system/priv-app` (requires root).
   That would allow output-device effects or `MODIFY_AUDIO_ROUTING`, and the effect could sit on the car's output device.
3. **A BYD vendor DSP API** exposing their amplifier EQ. None is public.
4. *Partial alternative:* granting the `DUMP` permission over adb lets some apps (e.g. Wavelet) discover hidden sessions.
   That depends on adb access to the car and is not implemented in 1.0.

---

## 6. Vehicle test plan (about 20 minutes, parked)

Record results in the table at the end. If something fails, open **Status → Diagnostic log → Copy** and keep the text.

1. **Install & open.** Note on Status → *This head unit*: Android version, "Android Automotive OS", firmware, outputs.
2. **Supported effects.** Status → *Supported audio effects*: is Dynamics Processing ✓? Classic Equalizer ✓?
3. **Engine sanity check.** Status → *Hear it for yourself* → **Pink noise**. On the Equalizer screen try Flat → Deep Bass → Treble Clarity,
   and toggle **EQ ON/OFF**. *Pass* if the sound clearly changes. (This proves the DSP runs on the head unit.)
4. **Spotify.** Play a track. Status → *Audio streams* should list `com.spotify.music` with control/enabled/verified = yes, and
   the pill should say "Processing 1 audio stream". Toggle EQ ON/OFF with a bass-heavy preset. *Pass* if audible.
5. **Local player.** Repeat step 4 with a USB/local music app.
6. **YouTube Music**, then **YouTube.** Repeat. A bypass is expected for YouTube (no stream listed, no audible change).
7. **Global mode.** Settings → turn on *Global mode*. Play YouTube. Toggle EQ ON/OFF with Deep Bass.
   *Pass* only if audible. If not audible, turn Global mode off again.
8. **App switching.** While Spotify plays, open Maps or another app for 5 minutes. Return: still processing?
9. **Restart.** Turn the car fully off, lock it, wait about 10 minutes (deep sleep), then return. Is the RDM Eq notification present?
   Does Spotify get processed without opening RDM Eq? Is the preset still selected?
10. **Loudness.** Turn Loudness on, change the volume with the car's knob, and watch the "Android media volume" % on the Sound screen.
11. **Clipping.** Choose Deep Bass with Bass boost at maximum and Auto headroom on, at moderate volume. Listen for distortion.

| Step | Pass / Fail / Notes |
|---|---|
| 1 Platform info | |
| 2 Effects available | |
| 3 Test signal audible change | |
| 4 Spotify | |
| 5 Local player | |
| 6 YouTube Music / YouTube | |
| 7 Global mode | |
| 8 App switching | |
| 9 Sleep / restart | |
| 10 Loudness follows knob | |
| 11 Clipping | |

---

## 6b. Version 1.2.0 changes

- **10 bands** (31 Hz–16 kHz, ISO octave centres) instead of 7; the DSP curve now uses 31 bands (~1/3 octave).
  1.x settings, custom presets, edited built-ins and exported preset files (7 values) are resampled to 10 bands so they sound the same.
  Tested: 1.1.1 → 1.2.0 upgrade on the emulator kept the custom preset "My Old Curve" active, with the Rock shape converted.
- Slimmer faders with **+ and − both above each band**. Tested in landscape 2560×1600, phone portrait, light and dark.
- Removed the `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission and the unofficial quick-boot action (see docs/PLAY_PROTECT.md).
- Unit tests: 41 pass. Cross-app processing re-checked (effect attaches to another app's session).

## 7. In-app updates (added in 1.1.0)

**How it works.** RDM Eq reads the public release list of `github.com/ramyadmin-BYD/RDM-Eq` over HTTPS (no token in the APK).
It ignores drafts, ignores pre-releases unless "Include beta versions" is on, and requires each release to carry
`rdm-eq-update.json` (version, APK name, SHA-256, size, signing-certificate fingerprint). It offers a release only if it
is for `com.rdm.eq`, has a higher `versionCode`, and names the **same signing certificate** as the installed app.
After download, size, SHA-256, GitHub's own asset digest, package name, version and signing certificate are checked
again. Then Android's **PackageInstaller** opens its own confirmation; the user always confirms. If that API fails, RDM Eq falls back to the
system installer intent. Nothing is uninstalled; app data (presets, settings) stays because it is an in-place update.

**Android rules that apply (not limitations of RDM Eq):**
- An ordinary app **cannot update itself silently**. Android always shows "Update this app?".
- The first time, Android asks you to allow RDM Eq to **"Install unknown apps"**. If BYD's firmware hides that setting
  or blocks app-initiated installs, RDM Eq says so and points to the manual USB install. *Unverified on DiLink.*
- **Google Play Protect** (only on devices with Google Play services) may scan or **block** updates from unknown developers.
  On the Google Play emulator it blocked the release build as "potentially harmful", a known false-positive pattern for
  self-updating sideloaded apps. RDM Eq then explains this and links to the release page. If your head unit shows this, install the APK
  from GitHub by USB, and/or submit the app for review through Google's Play Protect appeal form. Many BYD units have no Play Protect. *Unverified on DiLink.*
- Android does not reopen an app after it updates itself. RDM Eq posts an "RDM Eq updated – tap to open" notification, and the engine restarts on its own.
- Users on **1.0.0** must install 1.1.x once by hand (1.0.0 has no updater). Same key, so it installs over 1.0.0 and keeps presets.

### Update tests (Android 17 emulator, 5 Nov 2026)

| Test | Result |
|---|---|
| Existing app builds; 36 unit tests (10 new for release parsing/validation) | ✅ |
| Equalizer still processes audio after the changes (effect attaches, presets apply) | ✅ (release smoke test; audio engine code unchanged) |
| About screen: developer card first, version/build read from the installed package, light and dark | ✅ |
| Update found on a **live GitHub release** (v1.1.0) by the automatic start-up check, with version, date and notes | ✅ |
| "No update available" → "You're using the latest version of RDM Eq." | ✅ |
| Later → no automatic re-prompt for that version | ✅ |
| Network failure (server down), HTTP 500, malformed release list, malformed metadata | ✅ clear message, no crash |
| Draft-only and pre-release-only releases ignored when beta is off | ✅ |
| Interrupted download → partial file kept → **resumed** with an HTTP Range request → verified | ✅ |
| Tampered APK (checksum mismatch) refused and deleted | ✅ |
| APK signed with a different key refused and deleted | ✅ |
| Stale earlier download re-verified against new metadata and discarded | ✅ |
| Duplicate downloads prevented (single download job) | ✅ by design; no second job starts while one is active |
| "Install unknown apps" permission flow → returns and continues automatically | ✅ |
| Android "Update this app?" → Cancel → back to Install | ✅ |
| **Full in-app update installed** (test build 1 → 5) with custom preset, edited curve and settings **kept**; "updated" notification; engine restarted | ✅ |
| Live GitHub release installed in-app on the emulator | ⚠️ Downloaded and verified, then **blocked by Google Play Protect** on the Google Play emulator image; RDM Eq showed the explanation and kept working |
| **Update on the BYD head unit** | ❌ Not tested: no access to the car |

### Vehicle test for updates
12. Settings → Updates → **Check for Updates**: is the latest version shown?
13. **Update Now** → does Android show "Install unknown apps" for RDM Eq? Allow it. Does "Update this app?" appear? Tap **Update**.
14. After the update: open RDM Eq from the notification. About shows the new version, and your preset and custom presets are still there.
15. If any step is blocked, note the exact message. The fallback is installing the APK from the GitHub release page via USB.

## Sources

- [DiAuto install guide (tested on BYD DiLink 5.1, Android 13)](https://github.com/shihabal3amri/DiAuto/blob/main/docs/INSTALL.md)
- [NEVPedia: installing APKs on DiLink](https://nevpedia.com/en/byd/all-models/bydmate-what-it-is-and-how-to-install)
- [XDA: BYD multimedia APK install](https://xdaforums.com/t/byd-multimedia-install-apk.4541247/)
- [Why Android equalizer apps don't work with every player (Esper)](https://www.esper.io/blog/android-equalizer-apps-inconsistent)
- [Session-0 global effects deprecation notes](https://www.github.com/pinpong/equalizer-issue-tracker/blob/master/EQUALIZER_BROADCAST.md)
- [Wavelet (session-based Android EQ) discussion](https://xdaforums.com/t/app-9-0-wavelet-headphone-specific-equalization.4097957/)
