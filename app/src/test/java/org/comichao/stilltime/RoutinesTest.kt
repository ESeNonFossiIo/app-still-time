package org.comichao.stilltime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Routines: the steps, how a running one catches up with the clock, and how they are stored. */
class RoutinesTest {
    private val f25 = Step(StepKind.FOCUS, 25)
    private val b5 = Step(StepKind.BREAK, 5)
    private val min = 60_000L

    @Test fun repeatingRunsTheStepsAgain() {
        val r = Routine("Deep", listOf(Step(StepKind.FOCUS, 50), Step(StepKind.BREAK, 10)), repeat = 3)
        assertEquals(6, r.expanded.size)
        assertEquals(180, r.totalMinutes)
        assertEquals(150, r.focusMinutes)
        assertEquals(3, r.focusCount)
        assertEquals("3 focus · 3h 0m", r.summary)
    }

    @Test fun theClassicPomodoroIsFourFocusesAndALongBreak() {
        val p = PRESETS.first { it.name == "Pomodoro" }
        assertEquals(8, p.expanded.size)
        assertEquals(4, p.focusCount)
        assertEquals(100 + 30, p.totalMinutes)
        assertEquals(15, p.expanded.last().minutes)
    }

    @Test fun nothingHasEndedYetSoNothingMoves() {
        val steps = listOf(f25, b5)
        val p = catchUp(steps, 0, stepStart = 0, now = 10 * min)
        assertEquals(0, p.index)
        assertTrue(p.finished.isEmpty())
    }

    @Test fun aFinishedFocusIsReportedWithItsEndTimeAndTheBreakBeginsRightAfter() {
        val steps = listOf(f25, b5, f25)
        val p = catchUp(steps, 0, stepStart = 0, now = 26 * min)
        assertEquals(1, p.index)
        assertEquals(25 * min, p.stepStart)   // the break began when the focus ended, not when the phone noticed
        assertEquals(listOf(FocusDone(25 * min, 25)), p.finished)
    }

    @Test fun aPhoneThatSleptThroughSeveralStepsCatchesUpInOneGo() {
        val steps = listOf(f25, b5, f25, b5)
        val p = catchUp(steps, 0, stepStart = 0, now = 58 * min)
        assertEquals(3, p.index)   // 25 + 5 + 25 = 55 minutes done, the last break is running
        assertEquals(55 * min, p.stepStart)
        assertEquals(listOf(FocusDone(25 * min, 25), FocusDone(55 * min, 25)), p.finished)
    }

    @Test fun aRoutineThatEndedIsMarkedAsOver() {
        val steps = listOf(f25, b5)
        val p = catchUp(steps, 0, stepStart = 0, now = 31 * min)
        assertEquals(steps.size, p.index)
        assertEquals(1, p.finished.size)
    }

    @Test fun catchingUpFromTheMiddleUsesTheStepsOwnStart() {
        val steps = listOf(f25, b5, f25)
        // the break (step 1) began at minute 25; it ends at 30, the last focus at 55
        val p = catchUp(steps, 1, stepStart = 25 * min, now = 56 * min)
        assertEquals(3, p.index)
        assertEquals(listOf(FocusDone(55 * min, 25)), p.finished)
    }

    @Test fun stepsSurviveBeingSaved() {
        val steps = listOf(f25, b5, Step(StepKind.BREAK, 15))
        assertEquals("F25,B5,B15", RoutineCodec.encodeSteps(steps))
        assertEquals(steps, RoutineCodec.decodeSteps("F25,B5,B15"))
    }

    @Test fun badStepCodesAreSkipped() {
        assertEquals(listOf(f25), RoutineCodec.decodeSteps("F25,X10,B,B0,Fabc"))
        assertTrue(RoutineCodec.decodeSteps("").isEmpty())
    }

    @Test fun routinesSurviveBeingSaved() {
        val list = listOf(Routine("Mine", listOf(f25, b5), repeat = 4, category = "Study"), Routine("Plain", listOf(f25)))
        assertEquals(list, RoutineCodec.fromJson(RoutineCodec.toJson(list)))
    }

    @Test fun brokenOrEmptyDataGivesNoRoutinesRatherThanACrash() {
        assertTrue(RoutineCodec.fromJson("not json").isEmpty())
        assertTrue(RoutineCodec.fromJson("""[{"name":"","steps":"F25"},{"name":"No steps","steps":""}]""").isEmpty())
    }

    @Test fun minutesReadAsHoursWhenLong() {
        assertEquals("45m", minutesText(45))
        assertEquals("2h 5m", minutesText(125))
    }
}
