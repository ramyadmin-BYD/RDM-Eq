# Equalizer: RDM Eq vs RDM Player

RDM Eq 1.3.0 copies RDM Player's equalizer (reference: RDM Player 1.4.0, `ramyadmin-BYD/RDM-Player` @ `3990481`,
files `ui/equalizer/EqualizerScreen.kt`, `playback/AudioEffectsManager.kt`, `data/prefs/AppSettings.kt`).
RDM Player was only read, never changed.

The two apps use different engines on purpose. RDM Player processes **its own** audio session. RDM Eq attaches to
**other apps'** sessions (YouTube, Spotify and so on) and keeps its own engine: DynamicsProcessing with 31 bands, and the classic
Equalizer as a fallback.

| Item | RDM Player 1.4.0 | RDM Eq 1.3.0 | Match |
|---|---|---|---|
| Bands | 31, 62, 125, 250, 500, 1k, 2k, 4k, 8k, 16k | same | ✓ |
| Band range | −12…+12 dB | −12…+12 dB (0.5 dB steps) | ✓ (RDM Eq snaps to 0.5 dB) |
| Fader | 6 dp track, bar from 0 dB, 13 dp ringed thumb, tap/drag, double-tap reset | same, plus + / − buttons | ✓ (+/− kept from 1.2.0) |
| Value text | "+3" | "+3", "+2.5" for half steps | ✓ |
| Layout | header (Save, on/off), preset chips + Custom, faders card, sliders below | same, plus response curve, status pill, preset buttons | ✓ |
| Presets | 11 | the same 11 with identical values, plus 10 in-car presets | ✓ |
| Preamp | −12…+12 dB | −12…+12 dB, plus Auto headroom | ✓ |
| Bass enhancement | 0–100 %, 8 dB × shelf (1, .9, .6, .3, .1) | same | ✓ |
| Treble enhancement | 0–100 %, 8 dB × shelf (.1, .3, .6, .9, 1) | same | ✓ |
| Effective limit | ±15 dB | ±15 dB | ✓ |
| Loudness | LoudnessEnhancer 0–10 dB | 0–10 dB input gain under the limiter (DynamicsProcessing) | ✓ same purpose, different mechanism |
| Volume normalization | DP one-band compressor (ratio 3, −24 dB, +6 dB make-up) | same parameters | ✓ (DynamicsProcessing engine only) |
| Balance | player audio processor | DP per-channel input gain (stereo only) | ✓ |
| Mono | player audio processor | — | ✕ not possible (see below) |
| ReplayGain | file tags | — | ✕ not possible (see below) |
| Save / delete presets | yes | yes, plus rename, edit built-ins, restore, export/import | ✓ |
| Persistence | DataStore | SharedPreferences (unchanged, existing data kept) | ✓ |
| Dark / light | Material 3 colour roles | same colour roles, RDM Eq's palette | ✓ |

## Differences that remain, and why

- **Mono playback**: Android audio effects process each channel separately. No public effect can mix left and right
  inside another app's stream.
- **ReplayGain**: needs the gain tags inside each music file. Only the playing app can read them.
  Use Volume normalization instead.
- **Loudness, normalization, balance and the limiter** need DynamicsProcessing. With the classic Android Equalizer fallback
  they are disabled and marked so. BYD DiLink 5 units are expected to have DynamicsProcessing (Android 10+), but this
  has not been confirmed on the car yet.
- **Speaker protection**: RDM Eq still tapers boosts below 30 Hz inside the DSP. RDM Player does not. The control values are identical.
- **Accent colour**: RDM Player lets you choose an accent colour. RDM Eq keeps its teal palette, because changing the app theme was out of scope.
