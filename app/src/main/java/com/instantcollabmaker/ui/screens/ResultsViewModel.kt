package com.instantcollabmaker.ui.screens

import androidx.lifecycle.ViewModel
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

    /** Renames a person for the rest of this video's session — see [AnalysisSession.renamePerson]. */
    fun renamePerson(personId: String, newName: String) {
        session.renamePerson(personId, newName)
    }

    fun generatePersonCollage(personId: String): CollageLayout? {
        val person = getPersonById(personId) ?: return null

        val items = person.appearances.map { appearance ->
            CollageItem(
                id = appearance.id,
                frame = appearance.bestFrame,
                label = "${person.displayName}, appearance ${appearance.index}",
                accentIndex = person.index,
            )
        }

        return collageGenerator.layout(CollageSpec(items = items))
    }

    /**
     * The unique-person collage: every person exactly once, never one tile per
     * appearance — that distinction is the whole point of [generatePersonCollage]
     * existing separately.
     *
     * Items are ordered by appearance count, most first, so a person who appeared far
     * more often naturally lands in the layout's hero slot — the "slightly more visual
     * prominence for more appearances" the layout allows for, without ever making one
     * person's tile dramatically larger than everyone else's.
     */
    fun generateFullCollage(): CollageLayout? {
        val analysisResult = result.value ?: return null

        val items = analysisResult.people
            .sortedByDescending { it.appearanceCount }
            .map { person ->
                CollageItem(
                    id = person.id,
                    frame = person.representativeFrame,
                    label = person.displayName,
                    accentIndex = person.index,
                )
            }

        return collageGenerator.layout(CollageSpec(items = items))
    }
}
