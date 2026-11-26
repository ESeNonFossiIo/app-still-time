package org.comichao.stilltime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** The steps of a routine as a bar: focus in the main colour, breaks in the other, each as wide as it is long. The step in progress is bright. */
@Composable
internal fun StepStrip(steps: List<Step>, current: Int = -1, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        steps.forEachIndexed { i, s ->
            val color = if (s.kind == StepKind.FOCUS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
            val alpha = when { current < 0 -> 1f; i < current -> 0.3f; i == current -> 1f; else -> 0.55f }
            Box(Modifier.weight(s.minutes.toFloat()).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(color.copy(alpha = alpha)))
        }
    }
}

/** Your routines: add, change or remove them, or start from a ready-made one. */
@Composable
internal fun RoutinesScreen(onBack: () -> Unit) {
    val c = LocalContext.current
    var routines by remember { mutableStateOf(Routines.list(c)) }
    var editing by remember { mutableStateOf<Pair<Int, Routine?>?>(null) }   // index in the list (-1: a new one) and the routine
    var deleting by remember { mutableStateOf<Int?>(null) }
    var presets by remember { mutableStateOf(false) }
    fun persist(list: List<Routine>) { routines = list; Routines.save(c, list) }

    editing?.let { (index, routine) ->
        RoutineEditor(
            initial = routine,
            taken = routines.filterIndexed { i, _ -> i != index }.map { it.name.lowercase() }.toSet(),
            onSave = { r ->
                persist(if (index < 0) routines + r else routines.mapIndexed { i, old -> if (i == index) r else old })
                if (index >= 0 && Timer.selectedRoutine(c)?.name == routine?.name) Timer.selectRoutine(c, r.name)
                editing = null
            },
            onCancel = { editing = null },
        )
        return
    }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        ScreenHeader("Routines", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "A routine runs its steps one after another without waiting for a tap, for example 25 minutes of focus, 5 of break, and again.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (routines.isEmpty()) Text("No routines yet. Add one below.", style = MaterialTheme.typography.bodyMedium)
            routines.forEachIndexed { i, r ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    r.summary + (if (r.repeat > 1) " · ${r.repeat} rounds" else "") + (r.category?.let { " · $it" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            IconButton(onClick = { editing = i to r }) { Icon(Icons.Default.Edit, "Edit ${r.name}") }
                            IconButton(onClick = { deleting = i }) { Icon(Icons.Default.Delete, "Delete ${r.name}", tint = MaterialTheme.colorScheme.error) }
                        }
                        StepStrip(r.expanded)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { editing = -1 to null }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("New routine") }
                Box {
                    OutlinedButton(onClick = { presets = true }) { Text("Add a preset"); Icon(Icons.Default.ArrowDropDown, null) }
                    DropdownMenu(presets, { presets = false }) {
                        PRESETS.forEach { p ->
                            val name = uniqueName(p.name, routines.map { it.name.lowercase() }.toSet())
                            DropdownMenuItem(
                                text = { Column { Text(p.name); Text(p.summary + if (p.repeat > 1) " · ${p.repeat} rounds" else "", style = MaterialTheme.typography.bodySmall) } },
                                onClick = { presets = false; persist(routines + p.copy(name = name)) },
                            )
                        }
                    }
                }
            }
        }
    }

    deleting?.let { i ->
        val r = routines[i]
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${r.name}\"?") },
            text = { Text("The routine is removed. Sessions it already logged stay in your stats.") },
            confirmButton = { TextButton(onClick = { deleting = null; persist(routines.filterIndexed { k, _ -> k != i }) }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

/** "Pomodoro" becomes "Pomodoro 2" when the name is taken. */
private fun uniqueName(base: String, taken: Set<String>): String {
    if (base.lowercase() !in taken) return base
    var n = 2
    while ("$base $n".lowercase() in taken) n++
    return "$base $n"
}

private class StepDraft(kind: StepKind, minutes: String) {
    var kind by mutableStateOf(kind)
    var minutes by mutableStateOf(minutes)
}

private const val MAX_STEP_MINUTES = 180

/** Edit one routine: its name, rounds, category and steps (kind, length, order). */
@Composable
private fun RoutineEditor(initial: Routine?, taken: Set<String>, onSave: (Routine) -> Unit, onCancel: () -> Unit) {
    val c = LocalContext.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var repeat by remember { mutableIntStateOf(initial?.repeat ?: 1) }
    var category by remember { mutableStateOf(initial?.category) }
    var categoryMenu by remember { mutableStateOf(false) }
    val steps = remember { mutableStateListOf<StepDraft>().apply { (initial?.steps ?: listOf(Step(StepKind.FOCUS, 25), Step(StepKind.BREAK, 5))).forEach { add(StepDraft(it.kind, it.minutes.toString())) } } }

    fun minutesOf(d: StepDraft) = d.minutes.toIntOrNull()?.coerceIn(1, MAX_STEP_MINUTES) ?: if (d.kind == StepKind.FOCUS) 25 else 5
    val result = Routine(name.trim(), steps.map { Step(it.kind, minutesOf(it)) }, repeat, category)
    val clash = name.trim().lowercase() in taken
    val canSave = name.isNotBlank() && !clash && steps.any { it.kind == StepKind.FOCUS }

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        ScreenHeader(if (initial == null) "New routine" else "Edit routine", onCancel)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(
                name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Name") },
                isError = clash, supportingText = if (clash) ({ Text("You already have a routine with this name") }) else null,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Rounds")
                    Text("The steps below are run this many times", style = MaterialTheme.typography.bodySmall)
                }
                FilledTonalIconButton(onClick = { repeat = (repeat - 1).coerceAtLeast(1) }, enabled = repeat > 1) { Text("−") }
                Text("$repeat", Modifier.width(36.dp), style = MaterialTheme.typography.titleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                FilledTonalIconButton(onClick = { repeat = (repeat + 1).coerceAtMost(20) }, enabled = repeat < 20) { Text("+") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Category")
                    Text("What the focus steps are logged as", style = MaterialTheme.typography.bodySmall)
                }
                Box {
                    FilledTonalButton(onClick = { categoryMenu = true }) { Text(category ?: "Selected one"); Icon(Icons.Default.ArrowDropDown, null) }
                    DropdownMenu(categoryMenu, { categoryMenu = false }) {
                        DropdownMenuItem(text = { Text("The one selected when I start") }, onClick = { category = null; categoryMenu = false })
                        Timer.categories(c).forEach { n -> DropdownMenuItem(text = { Text(n) }, onClick = { category = n; categoryMenu = false }) }
                    }
                }
            }

            SectionHeader("Steps")
            steps.forEachIndexed { i, d ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${i + 1}", Modifier.width(20.dp), style = MaterialTheme.typography.labelLarge)
                    StepKind.entries.forEach { k -> FilterChip(d.kind == k, { d.kind = k }, label = { Text(k.label) }) }
                    OutlinedTextField(
                        d.minutes, { if (it.length <= 3 && it.all(Char::isDigit)) d.minutes = it }, Modifier.width(76.dp), singleLine = true,
                        suffix = { Text("m") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { steps.add(i - 1, steps.removeAt(i)) }, enabled = i > 0) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                    IconButton(onClick = { steps.add(i + 1, steps.removeAt(i)) }, enabled = i < steps.size - 1) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                    IconButton(onClick = { steps.removeAt(i) }, enabled = steps.size > 1) { Icon(Icons.Default.Delete, "Delete step", tint = MaterialTheme.colorScheme.error) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { steps.add(StepDraft(StepKind.FOCUS, "25")) }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Focus") }
                OutlinedButton(onClick = { steps.add(StepDraft(StepKind.BREAK, "5")) }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Break") }
            }
            if (steps.isNotEmpty()) {
                StepStrip(result.expanded)
                Text(
                    "${result.summary}" + if (repeat > 1) " · $repeat rounds of ${minutesText(result.steps.sumOf { it.minutes })}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (steps.none { it.kind == StepKind.FOCUS }) Text("Add at least one focus step.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(onClick = { onSave(result) }, enabled = canSave) { Text("Save") }
        }
    }
}
