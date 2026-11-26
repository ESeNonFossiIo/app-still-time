package org.comichao.stilltime

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

enum class StepKind(val code: Char, val label: String) {
    FOCUS('F', "Focus"), BREAK('B', "Break");

    companion object { fun of(code: Char) = entries.firstOrNull { it.code == code } }
}

/** One block of a routine: focus or rest, for [minutes]. */
data class Step(val kind: StepKind, val minutes: Int) {
    val ms get() = minutes * 60_000L
}

/**
 * A routine: steps that run one after another without waiting for a tap, for example 25 focus, 5 break, 25 focus,
 * 5 break. [repeat] runs the whole list that many times. Focus steps are logged under [category], or under the
 * category chosen on the home screen when it is null.
 */
data class Routine(val name: String, val steps: List<Step>, val repeat: Int = 1, val category: String? = null) {
    /** Every step in order, with the list repeated. */
    val expanded: List<Step> get() = List(repeat.coerceAtLeast(1)) { steps }.flatten()
    val totalMinutes get() = expanded.sumOf { it.minutes }
    val focusMinutes get() = expanded.filter { it.kind == StepKind.FOCUS }.sumOf { it.minutes }
    val focusCount get() = expanded.count { it.kind == StepKind.FOCUS }

    /** "4 focus · 2h 0m" */
    val summary get() = "$focusCount focus · ${minutesText(totalMinutes)}"
}

fun minutesText(m: Int) = if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"

/** A finished focus step: when it ended and how long it was. */
data class FocusDone(val endedAt: Long, val minutes: Int)

/** Where a running routine stands after catching up with the clock. [index] equal to the step count means it is over. */
data class Progress(val index: Int, val stepStart: Long, val finished: List<FocusDone>)

/**
 * Walk a running routine forward to [now]. The step at [index] began at [stepStart]; every step that ended by
 * [now] is passed, in order, and the focus ones among them are reported so each can be logged. A phone that slept
 * through several steps catches up in one go, with the right end time for each.
 */
fun catchUp(steps: List<Step>, index: Int, stepStart: Long, now: Long): Progress {
    var i = index
    var start = stepStart
    val done = ArrayList<FocusDone>()
    while (i < steps.size && start + steps[i].ms <= now) {
        val end = start + steps[i].ms
        if (steps[i].kind == StepKind.FOCUS) done.add(FocusDone(end, steps[i].minutes))
        start = end
        i++
    }
    return Progress(i, start, done)
}

/** Routines are stored as JSON, each with its steps as a short code ("F25,B5,F25,B15"). */
object RoutineCodec {
    fun encodeSteps(steps: List<Step>) = steps.joinToString(",") { "${it.kind.code}${it.minutes}" }

    fun decodeSteps(text: String): List<Step> = text.split(",").mapNotNull { t ->
        val kind = t.firstOrNull()?.let(StepKind::of) ?: return@mapNotNull null
        t.drop(1).toIntOrNull()?.takeIf { it > 0 }?.let { Step(kind, it) }
    }

    fun toJson(list: List<Routine>): String = JSONArray(list.map { r ->
        JSONObject().put("name", r.name).put("steps", encodeSteps(r.steps)).put("repeat", r.repeat).apply { r.category?.let { put("category", it) } }
    }).toString()

    fun fromJson(text: String): List<Routine> = runCatching {
        JSONArray(text).let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.mapNotNull { o ->
            val steps = decodeSteps(o.optString("steps"))
            if (o.optString("name").isBlank() || steps.isEmpty()) null
            else Routine(o.getString("name"), steps, o.optInt("repeat", 1).coerceIn(1, 20), o.optString("category").ifEmpty { null })
        }
    }.getOrDefault(emptyList())
}

private fun focus(m: Int) = Step(StepKind.FOCUS, m)
private fun rest(m: Int) = Step(StepKind.BREAK, m)

/** Ready-made routines to start from. */
val PRESETS = listOf(
    Routine("Pomodoro", listOf(focus(25), rest(5), focus(25), rest(5), focus(25), rest(5), focus(25), rest(15))),
    Routine("Deep work", listOf(focus(50), rest(10)), repeat = 3),
    Routine("Short sprints", listOf(focus(15), rest(5)), repeat = 4),
)

/** The user's routines (the Pomodoro one until they change them). */
object Routines {
    private fun prefs(c: Context) = c.getSharedPreferences("stilltime", Context.MODE_PRIVATE)

    fun list(c: Context): List<Routine> =
        prefs(c).getString("routines", null)?.let { RoutineCodec.fromJson(it) } ?: listOf(PRESETS[0])

    fun save(c: Context, routines: List<Routine>) = prefs(c).edit { putString("routines", RoutineCodec.toJson(routines)) }
}
