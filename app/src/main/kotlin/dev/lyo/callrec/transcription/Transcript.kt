// SPDX-License-Identifier: GPL-3.0-or-later
package com.coolappstore.evercallrecorder.by.svhp.transcription

import com.coolappstore.evercallrecorder.by.svhp.core.L
import org.json.JSONObject

/**
 * Structured transcript shape we ask the cloud model to produce. Multi-speaker
 * diarization with a smart auto-title is folded into the same JSON envelope
 * we already store in the DB's `transcript` column — no schema migration
 * needed; the parser is backward-compatible with the legacy me/them/unknown
 * shape.
 *
 * Design choices:
 *  - `title` is the smart 1-line description (≤60 chars). UI uses it as the
 *    display name for voice memos that would otherwise show as a date stamp.
 *  - `speakers` is the canonical list of distinct voices the model heard.
 *    Each `Segment.speakerId` references one of these. For phone-call
 *    recordings the model is asked to use ids "ME" / "THEM"; for general
 *    recordings it may emit "A", "B", "C", … with display labels populated
 *    from any names mentioned in the conversation.
 *  - Legacy transcripts (string speaker me|them|unknown, no `speakers`,
 *    no `title`) parse via a synthesised `speakers` list and a null title.
 */
data class Transcript(
    val title: String?,
    val language: String?,
    val durationSec: Double?,
    val speakers: List<SpeakerInfo>,
    val segments: List<Segment>,
) {
    data class SpeakerInfo(
        /** Stable id within this transcript — referenced by [Segment.speakerId]. */
        val id: String,
        /** UI-facing label. Name > role > "Спікер N". */
        val label: String,
    )

    data class Segment(
        val startSec: Double,
        val endSec: Double,
        val speakerId: String,
        val text: String,
        /** "friendly" | "tense" | "neutral" | "excited" | "sad" | "angry" | null. */
        val tone: String?,
        /** "laugh" | "sigh" | "pause" | "background_music" | "background_voice" | … */
        val nonSpeech: List<String>,
    )

    companion object {
        const val LEGACY_ME = "ME"
        const val LEGACY_THEM = "THEM"
        const val LEGACY_UNKNOWN = "UNK"
    }
}

object TranscriptCodec {

    fun parse(raw: String?): Transcript? {
        if (raw.isNullOrBlank()) {
            L.d("Transcript", "parse(null/blank) → null")
            return null
        }
        val trimmed = stripCodeFences(raw.trim())
        if (!trimmed.startsWith("{")) {
            L.d("Transcript", "parse: not JSON, starts with '${trimmed.take(40)}'")
            return null
        }
        return runCatching {
            val root = JSONObject(trimmed)
            val segs = root.optJSONArray("segments") ?: return@runCatching null

            val speakersFromRoot = root.optJSONArray("speakers")?.let { arr ->
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    Transcript.SpeakerInfo(
                        id = o.optString("id").ifBlank { "S${i + 1}" },
                        label = o.optString("label").ifBlank { "Спікер ${i + 1}" },
                    )
                }
            }

            val outSegs = ArrayList<Transcript.Segment>(segs.length())
            // Tracks which legacy speaker tags we encountered so we can build
            // a synthetic `speakers` list when the JSON didn't include one.
            val legacySeen = LinkedHashSet<String>()
            for (i in 0 until segs.length()) {
                val s = segs.getJSONObject(i)
                val explicitId = s.optString("speaker_id").takeIf { it.isNotBlank() }
                val legacyTag = s.optString("speaker").takeIf { it.isNotBlank() }
                val resolvedId = explicitId
                    ?: legacyTag?.let { legacyToId(it) }
                    ?: Transcript.LEGACY_UNKNOWN
                if (speakersFromRoot == null && legacyTag != null) legacySeen += resolvedId
                outSegs += Transcript.Segment(
                    startSec = s.optDouble("start", 0.0),
                    endSec = s.optDouble("end", 0.0),
                    speakerId = resolvedId,
                    text = s.optString("text", "").trim(),
                    tone = s.optString("tone").takeIf { it.isNotBlank() },
                    nonSpeech = s.optJSONArray("non_speech")?.let { arr ->
                        List(arr.length()) { arr.optString(it) }.filter { it.isNotBlank() }
                    } ?: emptyList(),
                )
            }

            val speakers = speakersFromRoot ?: legacySeen.map { id ->
                Transcript.SpeakerInfo(id = id, label = legacyLabel(id))
            }

            val t = Transcript(
                title = root.optString("title").takeIf { it.isNotBlank() },
                language = root.optString("language").takeIf { it.isNotBlank() },
                durationSec = if (root.has("duration_sec")) root.optDouble("duration_sec") else null,
                speakers = speakers,
                segments = outSegs,
            )
            L.i(
                "Transcript",
                "parsed ${outSegs.size} segs / ${speakers.size} speakers, lang=${t.language}, title=${t.title?.take(40) ?: "-"}",
            )
            t
        }.onFailure { L.w("Transcript", "parse failed: ${it.message}") }.getOrNull()
    }

    /**
     * Cheap title extractor — used by the recordings list to avoid full
     * transcript parsing on every recomposition of every row. Returns the
     * `title` JSON field or null.
     */
    fun extractTitle(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = stripCodeFences(raw.trim())
        if (!trimmed.startsWith("{")) return null
        return runCatching {
            JSONObject(trimmed).optString("title").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun legacyToId(legacy: String): String = when (legacy.lowercase()) {
        "me" -> Transcript.LEGACY_ME
        "them" -> Transcript.LEGACY_THEM
        else -> Transcript.LEGACY_UNKNOWN
    }

    private fun legacyLabel(id: String): String = when (id) {
        Transcript.LEGACY_ME -> "Я"
        Transcript.LEGACY_THEM -> "Співрозмовник"
        else -> "?"
    }

    private fun stripCodeFences(s: String): String {
        if (!s.startsWith("```")) return s
        val firstNewline = s.indexOf('\n')
        if (firstNewline < 0) return s
        val withoutOpen = s.substring(firstNewline + 1)
        val closeIdx = withoutOpen.lastIndexOf("```")
        return if (closeIdx >= 0) withoutOpen.substring(0, closeIdx).trim() else withoutOpen
    }
}
