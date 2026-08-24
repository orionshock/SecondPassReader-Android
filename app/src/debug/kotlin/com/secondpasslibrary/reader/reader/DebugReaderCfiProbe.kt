package com.secondpasslibrary.reader.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.cfi.EpubCfiResolution
import com.secondpasslibrary.reader.reader.cfi.EpubCfiSelection
import com.secondpasslibrary.reader.reader.domain.ReaderEngine
import kotlinx.coroutines.launch

private const val RESULT_LIMIT = 120

@Composable
internal fun ReaderCfiProbe(engine: ReaderEngine, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    var cfiText by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("No CFI operation yet") }
    Column(modifier) {
        TextButton(onClick = { expanded = true }) { Text("CFI") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            OutlinedTextField(
                value = cfiText,
                onValueChange = { cfiText = it },
                label = { Text("EPUB CFI") },
                modifier = Modifier.widthIn(max = 420.dp),
                singleLine = true
            )
            ProbeAction("Capture visible point") {
                engine.cfiNavigator.currentPosition().also { outcome ->
                    (outcome as? EpubCfiOutcome.Success)?.value?.let { cfiText = it.value }
                    result = outcome.describe()
                }
            }
            ProbeAction("Capture selection") {
                engine.cfiNavigator.currentSelection().also { outcome ->
                    (outcome as? EpubCfiOutcome.Success)?.value?.cfi?.let { cfiText = it.value }
                    result = outcome.describe()
                }
            }
            ProbeAction("Resolve CFI") {
                result = cfiText.toCfi()?.let { engine.cfiNavigator.resolve(it).describe() }
                    ?: "Invalid CFI value"
            }
            ProbeAction("Go to CFI") {
                result = cfiText.toCfi()?.let { engine.cfiNavigator.goTo(it).describe() }
                    ?: "Invalid CFI value"
            }
            ProbeAction("Show active resource") {
                result = when (val position = engine.cfiNavigator.currentPosition()) {
                    is EpubCfiOutcome.Failure -> position.describe()

                    is EpubCfiOutcome.Success -> {
                        cfiText = position.value.value
                        engine.cfiNavigator.resolve(position.value).describe()
                    }
                }
            }
            DropdownMenuItem(text = { Text(result.take(RESULT_LIMIT)) }, onClick = {})
        }
    }
}

@Composable
private fun ProbeAction(label: String, operation: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    DropdownMenuItem(text = { Text(label) }, onClick = { scope.launch { operation() } })
}

private fun String.toCfi(): EpubCfi? = runCatching { EpubCfi(trim()) }.getOrNull()

private fun EpubCfiOutcome<*>.describe(): String = when (this) {
    is EpubCfiOutcome.Failure -> "Failure: ${reason.name}"

    is EpubCfiOutcome.Success -> when (val result = value) {
        null, Unit -> "Success"

        is EpubCfi -> result.value

        is EpubCfiSelection -> "Selection: ${result.selectedText.take(RESULT_LIMIT)}"

        is EpubCfiResolution -> buildString {
            append(result.resourceHref)
            result.selectedText?.let { append(": ").append(it.take(RESULT_LIMIT)) }
        }

        else -> "Success"
    }
}
