package com.instantcollabmaker.ui.screens

import androidx.lifecycle.ViewModel
import com.instantcollabmaker.core.Format
import com.instantcollabmaker.domain.model.CollageItem
import com.instantcollabmaker.domain.model.CollageLayout
import com.instantcollabmaker.domain.model.CollageSpec
import com.instantcollabmaker.domain.model.Person
import com.instantcollabmaker.domain.model.VideoAnalysisResult
import com.instantcollabmaker.domain.processing.CollageGenerator
import com.instantcollabmaker.domain.repository.AnalysisSession
import kotlinx.coroutines.flow.StateFlow

class ResultsViewModel(
    private val session: AnalysisSession,
    private val collageGenerator: CollageGenerator,
) : ViewModel() {

    val result: StateFlow<VideoAnalysisResult?> = session.result

    fun getPersonById(personId: String): Person? {
        return result.value?.people?.find { it.id == personId }
    }

    fun generatePersonCollage(personId: String): CollageLayout? {
        val person = getPersonById(personId) ?: return null

        val items = person.appearances.map { appearance ->
            CollageItem(
                id = appearance.id,
                frame = appearance.bestFrame,
                label = "Appearance ${appearance.index.toString().padStart(2, '0')}",
                caption = Format.timeRange(appearance.startTimestampMs, appearance.endTimestampMs),
                accentIndex = person.index,
            )
        }

        val spec = CollageSpec(
            title = person.displayName,
            subtitle = Format.count(person.appearanceCount, "appearance"),
            footerPrimary = "FrameTrace",
            footerSecondary = "${Format.seconds(person.totalScreenTimeMs)} on screen",
            items = items,
        )

        return collageGenerator.layout(spec)
    }

    fun generateFullCollage(): CollageLayout? {
        val analysisResult = result.value ?: return null

        val items = analysisResult.people.flatMap { person ->
            person.appearances.map { appearance ->
                CollageItem(
                    id = appearance.id,
                    frame = appearance.bestFrame,
                    label = person.displayName,
                    caption = Format.timecode(appearance.bestFrameTimestampMs),
                    accentIndex = person.index,
                )
            }
        }

        val spec = CollageSpec(
            title = "Full Collage",
            subtitle = "${Format.count(analysisResult.peopleCount, "person", "people")} · " +
                Format.count(analysisResult.appearanceCount, "appearance"),
            footerPrimary = "FrameTrace",
            footerSecondary = Format.clock(analysisResult.video.durationMs),
            items = items,
        )

        return collageGenerator.layout(spec)
    }
}
