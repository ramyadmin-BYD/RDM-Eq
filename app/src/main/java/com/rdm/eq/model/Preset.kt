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
    val all: List<Preset> = listOf(
        p(FLAT_ID, "Flat", "No change to the signal.", 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        p(
            BYD_REFERENCE_ID, "BYD Factory Sound Reference",
            "Neutral reference: leaves the factory system's own tuning untouched. " +
                "BYD's factory DSP settings are not published or measured, so this preset does not imitate them.",
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
        ),
        p("balanced", "Balanced", "Gentle lift at both ends for an even, relaxed sound.", 1.5f, 1.5f, 1f, 0.5f, 0f, 0f, 0.5f, 1f, 1f, 1.5f),
        p("pop", "Pop", "Punchy bass and bright, forward vocals.", 1f, 1f, 2f, 1.5f, 0.5f, -0.5f, 1f, 2f, 2f, 2f),
        p("rock", "Rock", "Classic V-shape: tight low end and crisp guitars.", 4f, 4f, 3f, 0.5f, -1f, -1.5f, 0.5f, 2f, 3f, 3.5f),
        p("jazz", "Jazz", "Warm upright bass, smooth mids, airy cymbals.", 2.5f, 2.5f, 1.5f, 0.5f, 0f, 1f, 0f, 0.5f, 2f, 2.5f),
        p("classical", "Classical", "Wide, natural orchestral balance.", 2.5f, 2.5f, 1.5f, 0.5f, -0.5f, 0f, 0f, 1f, 2f, 3f),
        p("acoustic", "Acoustic", "Body and sparkle for guitars and voices.", 2f, 2f, 1.5f, 1f, 1f, 0.5f, 1.5f, 2f, 2f, 1.5f),
        p("vocal", "Vocal Clarity", "Brings singers forward over the band.", -2f, -2f, -1f, 0f, 1f, 2f, 3f, 2.5f, 1.5f, 0.5f),
        p("speech", "Podcast and Speech", "Cuts boom, lifts intelligibility for talk.", -4f, -4f, -2.5f, -0.5f, 1f, 2.5f, 3f, 2f, 0.5f, -1f),
        p("hiphop", "Hip-Hop", "Heavy sub-bass and punchy kick.", 5f, 5f, 4f, 2f, 0f, -1f, 0.5f, 0.5f, 1f, 1.5f),
        p("edm", "EDM", "Deep drops and sizzling highs.", 5f, 5f, 3.5f, 1f, -1f, -1f, 0f, 2f, 3.5f, 4f),
        p("deep_bass", "Deep Bass", "Extra sub-bass extension.", 7f, 7f, 4.5f, 2f, 0f, -0.5f, 0f, 0f, 0f, 0f),
        p("bass_punch", "Bass Punch", "Upper-bass impact without boom.", 3f, 3f, 5.5f, 3.5f, 0.5f, -1f, 0f, 0.5f, 0.5f, 0.5f),
        p("treble", "Treble Clarity", "More detail and air in the top end.", 0f, 0f, 0f, -0.5f, -0.5f, 0f, 1.5f, 3f, 4f, 4.5f),
        p("night", "Night Driving", "Clear voices, softer highs for low-fatigue listening.", -1f, -1f, 0f, 0.5f, 0.5f, 1f, 2f, 1f, 0f, -1.5f),
        p("highway", "Highway Driving", "Compensates for road and tyre noise masking bass and presence.", 3.5f, 3.5f, 3f, 2f, 1f, 0f, 1f, 2f, 2f, 1.5f),
        p("audiophile", "Audiophile", "Subtle, natural tilt with a touch of warmth and air.", 2.5f, 2.5f, 1.5f, 0.5f, 0f, 0f, -0.5f, 0f, 1f, 1.5f),
    )

    fun byId(id: String): Preset? = all.firstOrNull { it.id == id }
}
