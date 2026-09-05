package com.instantcollabmaker.data.mock

import android.net.Uri
import com.instantcollabmaker.domain.model.VideoInfo

/**
 * The "Try Sample Video" entry.
 *
 * Phase 1 ships no media asset, so this is a synthetic descriptor with a stable
 * placeholder [Uri]. It exists so the whole flow is demonstrable on a device with an
 * empty gallery. Phase 2 can point this at a bundled asset and change nothing else.
 */
object SampleVideoProvider {

    val uri: Uri = Uri.parse("frametrace://sample/sample_01.mp4")

    fun sample(): VideoInfo = VideoInfo(
        uri = uri,
        displayName = "sample_01.mp4",
        durationMs = 30_000L,
        width = 1080,
        height = 1920,
        sizeBytes = 19_284_992L,
        isSample = true,
    )

    fun isSample(candidate: Uri): Boolean = candidate == uri
}
