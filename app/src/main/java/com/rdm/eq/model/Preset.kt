package com.rdm.eq.model

data class Preset(
    val id: String,
    val name: String,
    val gains: List<Float>,
    val builtIn: Boolean,
    val description: String = "",
    /** True for a built-in preset whose values the user has changed. */
    val modified: Boolean = false,
)

object BuiltInPresets {
    const val FLAT_ID = "flat"
    const val BYD_REFERENCE_ID = "byd_reference"

    private fun p(id: String, name: String, desc: String, vararg g: Float) =
        Preset(id, name, g.toList(), builtIn = true, description = desc)

    // Values are dB at 31, 62, 125, 250, 500 Hz, 1, 2, 4, 8, 16 kHz.
    // The first eleven are RDM Player's presets with identical names and values (RDM Player 1.4.0,
    // EqPresets.builtIn), so a curve sounds the same in both apps. The rest are RDM Eq's in-car presets.
    val all: List<Preset> = listOf(
        p(FLAT_ID, "Flat", "No change to the signal.", 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        p("rock", "Rock", "Classic V-shape: strong low end and crisp guitars.", 5f, 4f, 3f, 1f, -1f, -1f, 1f, 3f, 4f, 5f),
        p("pop", "Pop", "Forward, present mids for vocals and hooks.", -1f, 0f, 2f, 4f, 5f, 4f, 2f, 0f, -1f, -1f),
        p("jazz", "Jazz", "Warm bass, relaxed mids, airy cymbals.", 4f, 3f, 1f, 2f, -1f, -1f, 0f, 1f, 3f, 4f),
        p("classical", "Classical", "Wide, natural orchestral balance.", 5f, 4f, 3f, 2f, -1f, -1f, 0f, 2f, 3f, 4f),
        p("electronic", "Electronic", "Deep bass, bright top and a lifted 1 kHz bite.", 5f, 4f, 1f, 0f, -2f, 2f, 1f, 1f, 4f, 5f),
        p("hiphop", "Hip-Hop", "Heavy sub-bass and punchy kick.", 5f, 4f, 1f, 3f, -1f, -1f, 1f, -1f, 2f, 3f),
        p("vocal", "Vocal", "Brings singers forward over the band.", -2f, -3f, -3f, 1f, 4f, 4f, 3f, 1f, 0f, -2f),
        p("acoustic", "Acoustic", "Body and sparkle for guitars and voices.", 4f, 4f, 3f, 1f, 2f, 1f, 3f, 3f, 3f, 2f),
        p("bass_boost", "Bass Boost", "More low end, everything else untouched.", 6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f),
        p("treble_boost", "Treble Boost", "More detail and air, lows untouched.", 0f, 0f, 0f, 0f, 0f, 1f, 3f, 4f, 5f, 6f),
        // ---- RDM Eq in-car presets ----
        p(
            BYD_REFERENCE_ID, "BYD Factory Sound Reference",
            "Neutral reference: leaves the factory system's own tuning untouched. " +
                "BYD's factory DSP settings are not published or measured, so this preset does not imitate them.",
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
        ),
        p("balanced", "Balanced", "Gentle lift at both ends for an even, relaxed sound.", 1.5f, 1.5f, 1f, 0.5f, 0f, 0f, 0.5f, 1f, 1f, 1.5f),
        p("speech", "Podcast and Speech", "Cuts boom, lifts intelligibility for talk.", -4f, -4f, -2.5f, -0.5f, 1f, 2.5f, 3f, 2f, 0.5f, -1f),
        p("edm", "EDM", "Deep drops and sizzling highs.", 5f, 5f, 3.5f, 1f, -1f, -1f, 0f, 2f, 3.5f, 4f),
        p("deep_bass", "Deep Bass", "Extra sub-bass extension.", 7f, 7f, 4.5f, 2f, 0f, -0.5f, 0f, 0f, 0f, 0f),
        p("bass_punch", "Bass Punch", "Upper-bass impact without boom.", 3f, 3f, 5.5f, 3.5f, 0.5f, -1f, 0f, 0.5f, 0.5f, 0.5f),
        p("treble", "Treble Clarity", "More detail and air in the top end.", 0f, 0f, 0f, -0.5f, -0.5f, 0f, 1.5f, 3f, 4f, 4.5f),
        p("night", "Night Driving", "Clear voices, softer highs for low-fatigue listening.", -1f, -1f, 0f, 0.5f, 0.5f, 1f, 2f, 1f, 0f, -1.5f),
        p("highway", "Highway Driving", "Compensates for road and tyre noise masking bass and presence.", 3.5f, 3.5f, 3f, 2f, 1f, 0f, 1f, 2f, 2f, 1.5f),
        p("audiophile", "Audiophile", "Subtle, natural tilt with a touch of warmth and air.", 2.5f, 2.5f, 1.5f, 0.5f, 0f, 0f, -0.5f, 0f, 1f, 1.5f),
    )

    /**
     * Values these built-ins had in RDM Eq 1.2.0. If the saved current curve still equals one of them
     * (the user picked the preset and never moved a fader), it is moved to the new value on upgrade.
     */
    val valuesIn120: Map<String, List<Float>> = mapOf(
        "rock" to listOf(4f, 4f, 3f, 0.5f, -1f, -1.5f, 0.5f, 2f, 3f, 3.5f),
        "pop" to listOf(1f, 1f, 2f, 1.5f, 0.5f, -0.5f, 1f, 2f, 2f, 2f),
        "jazz" to listOf(2.5f, 2.5f, 1.5f, 0.5f, 0f, 1f, 0f, 0.5f, 2f, 2.5f),
        "classical" to listOf(2.5f, 2.5f, 1.5f, 0.5f, -0.5f, 0f, 0f, 1f, 2f, 3f),
        "acoustic" to listOf(2f, 2f, 1.5f, 1f, 1f, 0.5f, 1.5f, 2f, 2f, 1.5f),
        "vocal" to listOf(-2f, -2f, -1f, 0f, 1f, 2f, 3f, 2.5f, 1.5f, 0.5f),
        "hiphop" to listOf(5f, 5f, 4f, 2f, 0f, -1f, 0.5f, 0.5f, 1f, 1.5f),
    )

    /** New gains for a saved curve that is still an untouched 1.2.0 preset, else null. */
    fun migratedGains(activeId: String, gains: List<Float>, modified: Boolean): List<Float>? {
        val old = valuesIn120[activeId] ?: return null
        if (modified || gains != old) return null
        return byId(activeId)?.gains
    }

    fun byId(id: String): Preset? = all.firstOrNull { it.id == id }
}
