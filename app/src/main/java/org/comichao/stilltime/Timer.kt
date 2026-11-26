package org.comichao.stilltime

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.edit

/** DONE: a focus session ended and the timer keeps counting up until the user taps. */
enum class Phase { IDLE, FOCUS, BREAK, DONE }

/** Timer state lives in SharedPreferences so the UI, alarm and listener all agree. */
object Timer {
    const val MIN_MINUTES = 5
    const val MAX_MINUTES = 120
    val DEFAULT_CATEGORIES = listOf("Work", "Study", "Reading", "Other")

    private fun prefs(c: Context) = c.getSharedPreferences("stilltime", Context.MODE_PRIVATE)

    fun phase(c: Context) = Phase.valueOf(prefs(c).getString("phase", "IDLE")!!)
    fun endAt(c: Context) = prefs(c).getLong("endAt", 0)

    /** 0..1 through the running phase (0 when idle). */
    fun progress(c: Context, now: Long): Float {
        val total = when (phase(c)) {
            Phase.FOCUS -> prefs(c).getLong("curMs", 25 * 60_000L)
            Phase.BREAK -> currentBreakMinutes(c) * 60_000L
            Phase.DONE -> return 1f
            Phase.IDLE -> return 0f
        }
        return (1f - (endAt(c) - now).toFloat() / total).coerceIn(0f, 1f)
    }

    fun isBlocking(c: Context) = phase(c) == Phase.FOCUS && System.currentTimeMillis() < endAt(c)

    fun excluded(c: Context): Set<String> = prefs(c).getStringSet("excluded", emptySet())!!
    fun setExcluded(c: Context, pkgs: Set<String>) =
        prefs(c).edit { putStringSet("excluded", pkgs) }

    /** Selected focus length, used by the next session. */
    fun minutes(c: Context) = prefs(c).getInt("minutes", 25)
    fun setMinutes(c: Context, m: Int) {
        prefs(c).edit { putInt("minutes", m.coerceIn(MIN_MINUTES, MAX_MINUTES)) }
        WearSync.publish(c)
    }

    /** Whether a focus session takes over the screen. On by default. */
    fun fullscreenFocus(c: Context) = prefs(c).getBoolean("fullscreenFocus", true)
    fun setFullscreenFocus(c: Context, on: Boolean) =
        prefs(c).edit { putBoolean("fullscreenFocus", on) }

    fun themeMode(c: Context): ThemeMode =
        runCatching { ThemeMode.valueOf(prefs(c).getString("theme", null)!!) }.getOrDefault(ThemeMode.COLOR)
    fun setThemeMode(c: Context, m: ThemeMode) = prefs(c).edit { putString("theme", m.name) }

    /** The user ticked "Don't show again" on the app-pinning warning. */
    fun hidePinWarning(c: Context) = prefs(c).getBoolean("hidePinWarning", false)
    fun setHidePinWarning(c: Context, hide: Boolean) =
        prefs(c).edit { putBoolean("hidePinWarning", hide) }

    /** Length of the break in progress (only routines have breaks; a single session is focus only). */
    fun currentBreakMinutes(c: Context) = prefs(c).getInt("curBreakMinutes", 5)

    /** Selected category, used by the next session. */
    fun category(c: Context) = prefs(c).getString("category", DEFAULT_CATEGORIES[0])!!
    fun setCategory(c: Context, name: String) {
        prefs(c).edit { putString("category", name) }
        WearSync.publish(c)
    }

    fun categories(c: Context): List<String> =
        prefs(c).getString("categories", null)?.split("\n")?.filter { it.isNotBlank() }
            ?: DEFAULT_CATEGORIES

    fun addCategory(c: Context, name: String) {
        val n = name.trim()
        if (n.isEmpty() || categories(c).any { it.equals(n, ignoreCase = true) }) return
        prefs(c).edit { putString("categories", (categories(c) + n).joinToString("\n")) }
        setCategory(c, n)
    }

    /** Removes a category (at least one is always kept). Past sessions keep their label. */
    fun removeCategory(c: Context, name: String) {
        val left = categories(c) - name
        if (left.isEmpty()) return
        prefs(c).edit { putString("categories", left.joinToString("\n")) }
        if (category(c) == name) setCategory(c, left[0])
    }

    /** Category and length of the session in progress (what gets recorded on completion). */
    fun currentCategory(c: Context) = prefs(c).getString("curCategory", "")!!
    fun currentFocusMs(c: Context) = prefs(c).getLong("curMs", 25 * 60_000L)

    /**
     * Once, the first time the essential apps (calls and messaging) are known, adds them to the
     * allowed list. Afterwards the user's own choices win, including un-ticking them.
     */
    @Synchronized
    fun seedExcluded(c: Context, essential: Set<String>) {
        if (prefs(c).getBoolean("essentialSeeded", false)) return
        prefs(c).edit { putStringSet("excluded", excluded(c) + essential); putBoolean("essentialSeeded", true) }
    }

    @Synchronized
    fun start(c: Context) {
        clearRoutine(c)
        val ms = minutes(c) * 60_000L
        prefs(c).edit { putString("curCategory", category(c)); putLong("curMs", ms) }
        set(c, Phase.FOCUS, System.currentTimeMillis() + ms)
    }

    /** Giving up logs the session as abandoned; it does not count towards the home stats. */
    @Synchronized
    fun stop(c: Context) {
        if (inRoutine(c)) {
            // Settle first, so a step that just ended is logged as done; then only a focus step still running is given up.
            settle(c)
            if (phase(c) == Phase.FOCUS) {
                val now = System.currentTimeMillis()
                val ms = prefs(c).getLong("curMs", 25 * 60_000L)
                val elapsed = (now - (endAt(c) - ms)) / 1000
                if (elapsed > 0) History.add(c, now, currentCategory(c), (ms / 60_000).toInt(), elapsed, false)
            }
            clearRoutine(c)
            set(c, Phase.IDLE, 0)
            return
        }
        if (phase(c) == Phase.FOCUS) {
            // Time already ran out: settle() records it as completed and moves to DONE.
            if (settle(c) != null) return
            val now = System.currentTimeMillis()
            val ms = prefs(c).getLong("curMs", 25 * 60_000L)
            val elapsed = (now - (endAt(c) - ms)) / 1000
            History.add(c, now, currentCategory(c), (ms / 60_000).toInt(), elapsed.coerceAtLeast(0), false)
        }
        set(c, Phase.IDLE, 0)
    }

    /**
     * Advances finished phases (a single focus session waits as DONE until you tap Done; a routine's break leads to idle),
     * recording completed focus sessions.
     * Returns the first phase that ended, or null if nothing did.
     */
    @Synchronized
    fun settle(c: Context): Phase? {
        val now = System.currentTimeMillis()
        if (inRoutine(c)) return settleRoutine(c, now)
        val end = endAt(c)
        when (phase(c)) {
            Phase.FOCUS -> if (now >= end) {
                val minutes = (prefs(c).getLong("curMs", 25 * 60_000L) / 60_000).toInt()
                History.add(c, end, currentCategory(c), minutes, minutes * 60L, true)
                // Keep `end` as the focus end time: the overtime counter counts up from it.
                set(c, Phase.DONE, end)
                return Phase.FOCUS
            }
            Phase.BREAK -> if (now >= end) {
                set(c, Phase.IDLE, 0)
                return Phase.BREAK
            }
            else -> {}
        }
        return null
    }

    // ---- Routines: steps that run one after another, see Routines.kt ----------------------------------------------------

    /** Whether the home screen starts a routine (true) or a single focus session (false). */
    fun useRoutine(c: Context) = prefs(c).getBoolean("useRoutine", false)
    fun setUseRoutine(c: Context, on: Boolean) = prefs(c).edit { putBoolean("useRoutine", on) }

    /** The routine the home screen will start (its name; the first one if that was deleted). */
    fun selectedRoutine(c: Context): Routine? {
        val all = Routines.list(c)
        return all.firstOrNull { it.name == prefs(c).getString("routine", null) } ?: all.firstOrNull()
    }
    fun selectRoutine(c: Context, name: String) = prefs(c).edit { putString("routine", name) }

    /** A routine is running (its steps, the position in them and its name are saved with the timer). */
    fun inRoutine(c: Context) = !prefs(c).getString("rtSteps", "").isNullOrEmpty()
    fun routineSteps(c: Context): List<Step> = RoutineCodec.decodeSteps(prefs(c).getString("rtSteps", "")!!)
    fun routineIndex(c: Context) = prefs(c).getInt("rtIndex", 0)
    fun routineName(c: Context) = prefs(c).getString("rtName", "")!!

    /** Name of a routine that has just run to the end, and when; the alert says so once. */
    fun routineFinished(c: Context): String? =
        prefs(c).getString("rtDoneName", null)?.takeIf { System.currentTimeMillis() - prefs(c).getLong("rtDoneAt", 0) < 60_000 }

    /** The step after the current one, or null on the last. */
    fun nextStep(c: Context): Step? = routineSteps(c).getOrNull(routineIndex(c) + 1)

    @Synchronized
    fun startRoutine(c: Context, r: Routine) {
        val steps = r.expanded
        if (steps.isEmpty()) return
        prefs(c).edit {
            putString("rtSteps", RoutineCodec.encodeSteps(steps)); putInt("rtIndex", 0); putString("rtName", r.name)
            putString("curCategory", r.category ?: category(c)); remove("rtDoneName")
        }
        beginStep(c, steps[0], System.currentTimeMillis())
    }

    /** Skip the break in progress and start the next step now. */
    @Synchronized
    fun skipStep(c: Context) {
        if (!inRoutine(c) || phase(c) != Phase.BREAK) return
        val now = System.currentTimeMillis()
        goTo(c, routineSteps(c), routineIndex(c) + 1, now)
    }

    private fun beginStep(c: Context, step: Step, startAt: Long) {
        if (step.kind == StepKind.FOCUS) {
            prefs(c).edit { putLong("curMs", step.ms) }
            set(c, Phase.FOCUS, startAt + step.ms)
        } else {
            prefs(c).edit { putInt("curBreakMinutes", step.minutes) }
            set(c, Phase.BREAK, startAt + step.ms)
        }
    }

    /** Begin step [index] at [startAt], or finish the routine when there is none left. */
    private fun goTo(c: Context, steps: List<Step>, index: Int, startAt: Long) {
        if (index >= steps.size) {
            val name = routineName(c)
            clearRoutine(c)
            prefs(c).edit { putString("rtDoneName", name); putLong("rtDoneAt", System.currentTimeMillis()) }
            set(c, Phase.IDLE, 0)
        } else {
            prefs(c).edit { putInt("rtIndex", index) }
            beginStep(c, steps[index], startAt)
        }
    }

    private fun clearRoutine(c: Context) = prefs(c).edit { remove("rtSteps"); remove("rtIndex"); remove("rtName"); remove("rtDoneName") }

    /** A step ended: log the focus ones, then move on to whatever the clock says is current. Returns the phase that ended first. */
    private fun settleRoutine(c: Context, now: Long): Phase? {
        val ended = phase(c)
        if ((ended != Phase.FOCUS && ended != Phase.BREAK) || now < endAt(c)) return null
        val steps = routineSteps(c)
        val index = routineIndex(c)
        val p = catchUp(steps, index, endAt(c) - steps[index].ms, now)
        p.finished.forEach { History.add(c, it.endedAt, currentCategory(c), it.minutes, it.minutes * 60L, true) }
        goTo(c, steps, p.index, p.stepStart)
        return ended
    }

    /** What to say when a step ends: the one that follows, or that the routine is over. Null if there is nothing to say. */
    fun alertText(c: Context, ended: Phase): String? {
        routineFinished(c)?.let { return "Routine complete: $it. Well done." }
        if (inRoutine(c)) {
            val i = routineIndex(c)
            val steps = routineSteps(c)
            val step = steps.getOrNull(i) ?: return null
            return (if (ended == Phase.FOCUS) "Focus complete. " else "Break over. ") +
                "Next: ${step.kind.label.lowercase()}, ${step.minutes} min (step ${i + 1} of ${steps.size})."
        }
        return when (ended) {
            Phase.FOCUS -> "Focus complete."
            Phase.BREAK -> "Break over."
            else -> null
        }
    }

    private fun set(c: Context, phase: Phase, end: Long) {
        prefs(c).edit { putString("phase", phase.name); putLong("endAt", end) }
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            c, 0, Intent(c, AlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // DONE has no alarm: its `end` is in the past and it waits for the user.
        if (end > 0 && phase != Phase.DONE) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, pi) else am.cancel(pi)
        WearSync.publish(c)
    }
}
