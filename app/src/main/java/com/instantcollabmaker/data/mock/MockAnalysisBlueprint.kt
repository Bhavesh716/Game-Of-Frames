package com.instantcollabmaker.data.mock

/**
 * The canonical mock analysis, expressed as plain numbers.
 *
 * These are the Sample 1 figures from the assignment: five unique people, four
 * appearances each, twenty appearances total, over a thirty-second portrait video.
 * They live here — never in a composable — so Phase 2 deletes this one file and wires
 * the real pipeline output into the same models.
 */
internal object MockAnalysisBlueprint {

    /** Duration the timings below were authored against. */
    const val REFERENCE_DURATION_MS = 30_000L

    const val FRAMES_ANALYZED = 216
    const val FACES_DETECTED = 268
    const val PROCESSING_DURATION_MS = 7_400L

    /** One person's timeline. Windows are milliseconds into the reference video. */
    data class PersonBlueprint(
        val displayName: String,
        val identityConfidence: Float,
        val windows: List<Window>,
    )

    /**
     * @param bestOffsetMs where inside the window the winning frame sits.
     * @param quality the five best-frame scoring components for that winning frame.
     */
    data class Window(
        val startMs: Long,
        val endMs: Long,
        val bestOffsetMs: Long,
        val detectedFrames: Int,
        val frontality: Float,
        val sharpness: Float,
        val eyesOpen: Float,
        val expression: Float,
        val visibility: Float,
    )

    /**
     * Totals per person: A 12.8s, B 10.4s, C 11.1s, D 9.7s, E 13.2s.
     * People overlap in time, as they would in a real multi-person video.
     */
    val people: List<PersonBlueprint> = listOf(
        PersonBlueprint(
            displayName = "Person A",
            identityConfidence = 0.96f,
            windows = listOf(
                Window(1_200, 4_100, 1_900, 21, 0.94f, 0.88f, 0.97f, 0.81f, 0.95f),
                Window(9_800, 12_700, 11_100, 20, 0.86f, 0.79f, 0.93f, 0.74f, 0.90f),
                Window(17_400, 21_000, 19_600, 25, 0.91f, 0.84f, 0.95f, 0.88f, 0.92f),
                Window(24_600, 28_000, 26_100, 24, 0.83f, 0.90f, 0.89f, 0.69f, 0.87f),
            ),
        ),
        PersonBlueprint(
            displayName = "Person B",
            identityConfidence = 0.93f,
            windows = listOf(
                Window(600, 3_200, 2_000, 18, 0.88f, 0.82f, 0.94f, 0.77f, 0.91f),
                Window(7_900, 10_100, 8_800, 16, 0.79f, 0.86f, 0.90f, 0.83f, 0.85f),
                Window(14_800, 17_600, 16_400, 19, 0.92f, 0.91f, 0.96f, 0.86f, 0.94f),
                Window(25_100, 27_900, 26_700, 20, 0.81f, 0.77f, 0.88f, 0.72f, 0.83f),
            ),
        ),
        PersonBlueprint(
            displayName = "Person C",
            identityConfidence = 0.91f,
            windows = listOf(
                Window(2_400, 5_100, 3_600, 19, 0.90f, 0.85f, 0.92f, 0.90f, 0.93f),
                Window(8_600, 11_400, 10_300, 20, 0.84f, 0.80f, 0.87f, 0.75f, 0.88f),
                Window(16_200, 18_900, 17_100, 19, 0.87f, 0.93f, 0.94f, 0.82f, 0.90f),
                Window(22_000, 24_900, 23_800, 21, 0.76f, 0.74f, 0.85f, 0.79f, 0.81f),
            ),
        ),
        PersonBlueprint(
            displayName = "Person D",
            identityConfidence = 0.89f,
            windows = listOf(
                Window(3_800, 5_900, 4_600, 15, 0.85f, 0.78f, 0.91f, 0.73f, 0.86f),
                Window(11_200, 13_600, 12_500, 17, 0.93f, 0.89f, 0.95f, 0.87f, 0.92f),
                Window(19_000, 21_500, 20_200, 18, 0.80f, 0.83f, 0.88f, 0.76f, 0.84f),
                Window(26_300, 29_000, 27_400, 19, 0.74f, 0.71f, 0.82f, 0.68f, 0.79f),
            ),
        ),
        PersonBlueprint(
            displayName = "Person E",
            identityConfidence = 0.94f,
            windows = listOf(
                Window(900, 4_400, 2_700, 25, 0.92f, 0.90f, 0.96f, 0.91f, 0.94f),
                Window(10_500, 13_600, 12_000, 22, 0.86f, 0.81f, 0.90f, 0.78f, 0.89f),
                Window(18_200, 21_600, 19_400, 24, 0.89f, 0.87f, 0.93f, 0.84f, 0.91f),
                Window(25_800, 29_000, 27_100, 23, 0.82f, 0.85f, 0.87f, 0.80f, 0.85f),
            ),
        ),
    )
}
