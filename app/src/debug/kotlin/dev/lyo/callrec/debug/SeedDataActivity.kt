// SPDX-License-Identifier: GPL-3.0-or-later
package com.coolappstore.evercallrecorder.by.svhp.debug

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.coolappstore.evercallrecorder.by.svhp.App
import com.coolappstore.evercallrecorder.by.svhp.recorder.Strategy
import com.coolappstore.evercallrecorder.by.svhp.storage.CallDao
import com.coolappstore.evercallrecorder.by.svhp.storage.CallRecord
import com.coolappstore.evercallrecorder.by.svhp.telephony.CallMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Debug-only entrypoint that wipes the recordings DB and seeds 8 plausible
 * sample calls so we can grab clean screenshots. Trigger from a host shell:
 *
 *   adb shell am start -n com.coolappstore.evercallrecorder.by.svhp.debug/com.coolappstore.evercallrecorder.by.svhp.debug.SeedDataActivity
 *
 * Lives under `app/src/debug/` so it cannot ship in a release build.
 *
 * Each fake recording gets a tiny silent WAV (~96 KB) on disk so the playback
 * screen and MediaPlayer prepare path don't crash if we tap into one for the
 * screenshot.
 */
class SeedDataActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Activity is exported=true (required for shell `am start` to work on
        // Samsung One UI 16+ debug builds), so guard at runtime: only honour
        // invocations from the shell or from our own package. Without this
        // any other on-device app on a debug install could wipe the
        // recordings DB by firing an Intent at us.
        //
        // We use Activity.referrer (Uri "android-app://<pkg>") instead of
        // launchedFromUid because the latter is unreliable across vendors —
        // verified to return -1 on Samsung One UI 16 (API 36) for shell
        // invocations even though minSdk 31 supposedly exposes it.
        val ref = referrer?.host
        val trusted = ref == "com.android.shell" || ref == packageName
        if (!trusted) {
            Log.w(TAG, "rejected: referrer=$ref")
            Toast.makeText(this, "SeedDataActivity: untrusted caller blocked", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        Log.i(TAG, "trusted referrer=$ref — running seed")
        scope.launch {
            val count = withContext(Dispatchers.IO) { runSeed() }
            Toast.makeText(this@SeedDataActivity, "Seeded $count records", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private suspend fun runSeed(): Int {
        val app = application as App
        val container = app.container
        val dao = container.db.calls()

        val baseDir = getExternalFilesDir(null)?.resolve("recordings")
            ?: filesDir.resolve("recordings")
        baseDir.mkdirs()
        Log.i(TAG, "seeding into ${baseDir.absolutePath}")

        // Wipe existing records — we want a clean slate for screenshots.
        val all = dao.observeAllOnce()
        val ids = all.map { it.callId }
        if (ids.isNotEmpty()) {
            dao.deleteAll(ids)
            // Don't bother deleting on-disk audio for the prior seeded set —
            // they're tiny silent WAVs in the same dir, harmless.
        }

        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000

        val seeds = listOf(
            // ─── Today ──────────────────────────────────────────────────────
            Seed(
                offsetMs = -2 * 60 * 60 * 1000L, // today, 2h ago — voice memo with smart title
                durSec = 87,
                name = null,
                number = null,
                modeOverride = CallMonitorService.MODE_VOICE_MEMO,
                strategy = Strategy.SingleMic,
                transcript = VOICE_MEMO_GROCERY,
            ),
            Seed(
                offsetMs = -3 * 60 * 60 * 1000L, // today, 3h ago
                durSec = 222, // 3:42
                name = "Андрій Коваленко",
                number = "+380671234567",
                strategy = Strategy.DualUplinkDownlink,
                favorite = true,
                notes = "Обговорили ціну на партію. Він дзвонить завтра з рішенням.",
            ),
            Seed(
                offsetMs = -6 * 60 * 60 * 1000L, // today, 6h ago — phone call, new schema
                durSec = 738, // 12:18
                name = "Мама",
                number = "+380505551234",
                strategy = Strategy.DualUplinkDownlink,
                favorite = true,
                transcript = MAMA_TRANSCRIPT,
            ),
            Seed(
                offsetMs = -8 * 60 * 60 * 1000L, // today, 8h ago — voice memo (no transcript yet)
                durSec = 24,
                name = null,
                number = null,
                modeOverride = CallMonitorService.MODE_VOICE_MEMO,
                strategy = Strategy.SingleMic,
            ),
            // ─── Yesterday ──────────────────────────────────────────────────
            Seed(
                offsetMs = -1 * day - 4 * 60 * 60 * 1000L, // yesterday — 4-speaker meeting
                durSec = 1245,
                name = null,
                number = null,
                modeOverride = CallMonitorService.MODE_VOICE_MEMO,
                strategy = Strategy.SingleMic,
                transcript = MEETING_TRANSCRIPT,
            ),
            Seed(
                offsetMs = -1 * day - 7 * 60 * 60 * 1000L, // yesterday
                durSec = 321,
                name = "Олена (Моно)",
                number = "+380442000001",
                strategy = Strategy.SingleVoiceCallStereo,
            ),
            Seed(
                offsetMs = -1 * day - 14 * 60 * 60 * 1000L,
                durSec = 48,
                name = null,
                number = "+380939876543",
                strategy = Strategy.SingleVoiceCallMono,
            ),
            // ─── Earlier this week ──────────────────────────────────────────
            Seed(
                offsetMs = -2 * day - 5 * 60 * 60 * 1000L, // 3-speaker interview
                durSec = 2148,
                name = null,
                number = null,
                modeOverride = CallMonitorService.MODE_VOICE_MEMO,
                strategy = Strategy.SingleMic,
                transcript = INTERVIEW_TRANSCRIPT,
                notes = "Інтервʼю з кандидатом на позицію Backend Lead.",
            ),
            Seed(
                offsetMs = -3 * day,
                durSec = 534, // 8:54
                name = "Тарас",
                number = "+380963141592",
                strategy = Strategy.DualUplinkDownlink,
                notes = "Домовились на четвер о 16:00.",
            ),
            Seed(
                offsetMs = -4 * day,
                durSec = 1331, // 22:11
                name = "Марина (HR)",
                number = "+380957708888",
                strategy = Strategy.DualUplinkDownlink,
            ),
            // ─── Older ─────────────────────────────────────────────────────
            Seed(
                offsetMs = -7 * day,
                durSec = 540, // lecture-style voice memo with one speaker
                name = null,
                number = null,
                modeOverride = CallMonitorService.MODE_VOICE_MEMO,
                strategy = Strategy.SingleMic,
                transcript = LECTURE_TRANSCRIPT,
            ),
            Seed(
                offsetMs = -10 * day,
                durSec = 92,
                name = "Стоматолог",
                number = "+380445550099",
                strategy = Strategy.SingleMic,
            ),
            Seed(
                offsetMs = -22 * day,
                durSec = 34,
                name = "Доставка Нової Пошти",
                number = "+380500304400",
                strategy = Strategy.SingleMic,
            ),
        )

        for (seed in seeds) {
            val callId = UUID.randomUUID().toString()
            val started = now + seed.offsetMs
            val ended = started + seed.durSec * 1000L

            // Always write a tiny silent WAV for uplink. For dual strategies
            // also write a downlink track so MediaPlayer can prepare both.
            val uplink = File(baseDir, "$callId-uplink.wav")
            writeSilentWav(uplink)
            val downlink = if (seed.strategy.isDual) {
                File(baseDir, "$callId-downlink.wav").also { writeSilentWav(it) }
            } else null

            dao.upsert(
                CallRecord(
                    callId = callId,
                    startedAt = started,
                    endedAt = ended,
                    contactNumber = seed.number,
                    contactName = seed.name,
                    mode = seed.modeOverride ?: seed.strategy.name,
                    uplinkPath = uplink.absolutePath,
                    downlinkPath = downlink?.absolutePath,
                    notes = seed.notes,
                    favorite = seed.favorite,
                    transcript = seed.transcript,
                ),
            )
        }
        return seeds.size
    }

    private data class Seed(
        val offsetMs: Long,
        val durSec: Int,
        val name: String?,
        val number: String?,
        val strategy: Strategy,
        val favorite: Boolean = false,
        val notes: String? = null,
        val transcript: String? = null,
        /** Override the persisted `mode` field — e.g. [CallMonitorService.MODE_VOICE_MEMO]. */
        val modeOverride: String? = null,
    )

    /**
     * Write a 3-second mono 16-bit 16 kHz silent WAV. ~96 KB — small enough to
     * seed eight tracks with no visible storage impact, but big enough to look
     * normal in the file list and to prepare cleanly in MediaPlayer.
     */
    private fun writeSilentWav(out: File) {
        val sampleRate = 16_000
        val seconds = 3
        val numSamples = sampleRate * seconds
        val byteRate = sampleRate * 2
        val dataSize = numSamples * 2
        val totalSize = 36 + dataSize

        val buf = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(totalSize)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16)            // subchunk1 size
        buf.putShort(1)           // audio format = PCM
        buf.putShort(1)           // channels = mono
        buf.putInt(sampleRate)
        buf.putInt(byteRate)
        buf.putShort(2)           // block align
        buf.putShort(16)          // bits per sample
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataSize)
        // Zero-filled by allocate() — that's our silent payload.
        out.writeBytes(buf.array())
    }

    private suspend fun CallDao.observeAllOnce(): List<CallRecord> =
        observeAll().first()

    companion object {
        private const val TAG = "SeedData"

        // Phone call with the two canonical speakers — exercises the "self
        // bubble right + other bubble left" Telegram-style chat layout.
        private val MAMA_TRANSCRIPT = """
        {
          "title": "Дзвінок мамі: вареники на вихідні",
          "language": "uk",
          "duration_sec": 738,
          "speakers": [
            {"id": "ME", "label": "Я"},
            {"id": "THEM", "label": "Мама"}
          ],
          "segments": [
            {"start": 0.0, "end": 2.4, "speaker_id": "ME", "text": "Привіт, мам! Ти як?", "tone": "friendly", "non_speech": []},
            {"start": 2.5, "end": 5.8, "speaker_id": "THEM", "text": "Привіт, синочку. Все добре, а в тебе?", "tone": "friendly", "non_speech": []},
            {"start": 6.0, "end": 9.2, "speaker_id": "ME", "text": "Все ок. Завтра приїжджаю на вихідні.", "tone": "neutral", "non_speech": []},
            {"start": 9.5, "end": 12.0, "speaker_id": "THEM", "text": "О, чудово! Я вареники зроблю.", "tone": "excited", "non_speech": ["laugh"]},
            {"start": 12.4, "end": 14.6, "speaker_id": "ME", "text": "Дякую! З картоплею, як завжди?", "tone": "friendly", "non_speech": []},
            {"start": 14.8, "end": 17.5, "speaker_id": "THEM", "text": "І з вишнею ще трошки — як любиш.", "tone": "friendly", "non_speech": []},
            {"start": 18.0, "end": 21.4, "speaker_id": "ME", "text": "Тоді привезу домашнього сиру з ринку.", "tone": "friendly", "non_speech": []},
            {"start": 21.6, "end": 25.0, "speaker_id": "THEM", "text": "Не хвилюйся, у мене вже є. Краще щось солодке візьми.", "tone": "neutral", "non_speech": []}
          ]
        }
        """.trimIndent()

        // Voice memo, single speaker — exercises the smart-title fallback in
        // the recordings list ("Список покупок…" replaces "Голосовий запис · …").
        private val VOICE_MEMO_GROCERY = """
        {
          "title": "Список покупок і нагадування на завтра",
          "language": "uk",
          "duration_sec": 87,
          "speakers": [
            {"id": "A", "label": "Я"}
          ],
          "segments": [
            {"start": 0.0, "end": 4.2, "speaker_id": "A", "text": "Так, треба запамʼятати: молоко, хліб, сир.", "tone": "neutral", "non_speech": []},
            {"start": 4.5, "end": 9.0, "speaker_id": "A", "text": "Ще зайти у Нову Пошту забрати посилку, відділення сім.", "tone": "neutral", "non_speech": []},
            {"start": 9.2, "end": 13.6, "speaker_id": "A", "text": "Передзвонити Олі завтра по обіді про вечерю.", "tone": "neutral", "non_speech": []},
            {"start": 13.8, "end": 18.0, "speaker_id": "A", "text": "І не забути вимкнути воду у ванній перед відʼїздом.", "tone": "questioning", "non_speech": ["sigh"]}
          ]
        }
        """.trimIndent()

        // 4-speaker meeting — exercises the curated palette where Я, Оля,
        // Микола, Іван each get a distinct categorical colour.
        private val MEETING_TRANSCRIPT = """
        {
          "title": "Планування Q2: бюджет і команда",
          "language": "uk",
          "duration_sec": 1245,
          "speakers": [
            {"id": "A", "label": "Я"},
            {"id": "B", "label": "Оля"},
            {"id": "C", "label": "Микола"},
            {"id": "D", "label": "Іван"}
          ],
          "segments": [
            {"start": 0.0, "end": 4.0, "speaker_id": "A", "text": "Окей, починаємо. Перший пункт — бюджет на Q2.", "tone": "neutral", "non_speech": []},
            {"start": 4.5, "end": 9.5, "speaker_id": "B", "text": "Я підготувала табличку — ріст на 18% порівняно з Q1.", "tone": "neutral", "non_speech": []},
            {"start": 9.8, "end": 14.0, "speaker_id": "C", "text": "Це з врахуванням нових двох інженерів?", "tone": "questioning", "non_speech": []},
            {"start": 14.2, "end": 17.0, "speaker_id": "B", "text": "Так, обидва на onboarding з квітня.", "tone": "neutral", "non_speech": []},
            {"start": 17.5, "end": 23.0, "speaker_id": "D", "text": "Я б ще заклав резерв на маркетинг — у нас лонч у червні.", "tone": "neutral", "non_speech": []},
            {"start": 23.5, "end": 27.0, "speaker_id": "A", "text": "Скільки приблизно?", "tone": "questioning", "non_speech": []},
            {"start": 27.2, "end": 31.5, "speaker_id": "D", "text": "Тисяч пʼятдесят-сімдесят. Точніше скажу за тиждень.", "tone": "neutral", "non_speech": []},
            {"start": 32.0, "end": 36.0, "speaker_id": "C", "text": "Тоді треба урізати десь інше або зросте бернер-рейт.", "tone": "tense", "non_speech": []},
            {"start": 36.5, "end": 41.0, "speaker_id": "B", "text": "Можу зрізати з консультантів, ми давно ними не користуємось.", "tone": "neutral", "non_speech": []},
            {"start": 41.5, "end": 44.0, "speaker_id": "A", "text": "Звучить розумно. Закриваємо це питання.", "tone": "friendly", "non_speech": []},
            {"start": 44.5, "end": 48.0, "speaker_id": "D", "text": "Дякую. Наступний пункт — найм senior backend.", "tone": "neutral", "non_speech": []}
          ]
        }
        """.trimIndent()

        // 3-speaker interview — Я (інтервʼюер), кандидат, технічний скрінер.
        private val INTERVIEW_TRANSCRIPT = """
        {
          "title": "Інтервʼю: Backend Lead, technical screen",
          "language": "uk",
          "duration_sec": 2148,
          "speakers": [
            {"id": "A", "label": "Я"},
            {"id": "B", "label": "Кандидат"},
            {"id": "C", "label": "Денис (tech)"}
          ],
          "segments": [
            {"start": 0.0, "end": 4.0, "speaker_id": "A", "text": "Доброго дня! Дякуємо що знайшли час.", "tone": "friendly", "non_speech": []},
            {"start": 4.5, "end": 7.0, "speaker_id": "B", "text": "Дякую за запрошення.", "tone": "friendly", "non_speech": []},
            {"start": 7.5, "end": 12.0, "speaker_id": "A", "text": "Розкажіть коротко про останній проєкт — що драйвило архітектурні рішення.", "tone": "neutral", "non_speech": []},
            {"start": 12.5, "end": 28.0, "speaker_id": "B", "text": "Останні два роки був на Kafka pipeline для рекомендаційної системи. Головний челендж — exactly-once delivery при 50k events/sec. Перейшли з naive consumer на transactional Kafka Streams.", "tone": "neutral", "non_speech": []},
            {"start": 28.5, "end": 33.0, "speaker_id": "C", "text": "А як вирішували backpressure від downstream?", "tone": "questioning", "non_speech": []},
            {"start": 33.5, "end": 47.0, "speaker_id": "B", "text": "Bounded queue з drop-oldest для non-critical events, плюс окремий high-priority lane. Метрики p99 — 80мс на хот-патах.", "tone": "neutral", "non_speech": []},
            {"start": 47.5, "end": 51.0, "speaker_id": "C", "text": "Цікаво. А моніторинг — Prometheus, чи щось своє?", "tone": "neutral", "non_speech": []},
            {"start": 51.5, "end": 58.0, "speaker_id": "B", "text": "Prometheus + custom OpenTelemetry exporter. Панелі в Grafana, alerting через PagerDuty.", "tone": "neutral", "non_speech": []},
            {"start": 58.5, "end": 62.0, "speaker_id": "A", "text": "Звучить солідно. Перейдемо до системного дизайну?", "tone": "friendly", "non_speech": []}
          ]
        }
        """.trimIndent()

        // Lecture-style voice memo, single speaker — palette stays minimal,
        // smart-title shines as the row label.
        private val LECTURE_TRANSCRIPT = """
        {
          "title": "Лекція з алгоритмів: graph traversal",
          "language": "uk",
          "duration_sec": 540,
          "speakers": [
            {"id": "A", "label": "Я"}
          ],
          "segments": [
            {"start": 0.0, "end": 6.0, "speaker_id": "A", "text": "Окей, сьогоднішня тема — обхід графів і коли який алгоритм правильний.", "tone": "neutral", "non_speech": []},
            {"start": 6.5, "end": 14.0, "speaker_id": "A", "text": "BFS — це коли вам треба найкоротший шлях у unweighted graph. DFS — коли треба знайти ВСІ шляхи або обійти зі стеком.", "tone": "neutral", "non_speech": []},
            {"start": 14.5, "end": 22.0, "speaker_id": "A", "text": "Dijkstra — обовʼязково для weighted graph з невідʼємними вагами. Bellman-Ford — якщо вони можуть бути відʼємними.", "tone": "neutral", "non_speech": []},
            {"start": 22.5, "end": 30.0, "speaker_id": "A", "text": "A-зірка — коли у вас є гарна евристика. На практиці це 90% navigation і pathfinding у іграх.", "tone": "neutral", "non_speech": []},
            {"start": 30.5, "end": 36.0, "speaker_id": "A", "text": "До екзамену — підготуйте візуалізацію кожного на маленькому графі. На наступній парі — задачі.", "tone": "neutral", "non_speech": ["pause"]}
          ]
        }
        """.trimIndent()
    }
}
