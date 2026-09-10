package de.graetz.electronote.math

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import android.graphics.Bitmap

/**
 * Calculator / handwriting-formula-recognition / function-plotter panel, mirroring the
 * iPad app's three-tab Mathe module. All state is hoisted by the caller so the
 * "recognize handwriting" action can dismiss this dialog, run a canvas lasso-selection,
 * and reopen it with the recognized text filled in.
 */
@Composable
fun MathPanelDialog(
    expression: String,
    onExpressionChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onRecognizeHandwriting: () -> Unit,
    onInsertPlot: (Bitmap) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    var calcResult by remember { mutableStateOf<String?>(null) }
    var calcError by remember { mutableStateOf<String?>(null) }

    var plotExpression by remember { mutableStateOf("sin(x)") }
    var xMinText by remember { mutableStateOf("-10") }
    var xMaxText by remember { mutableStateOf("10") }
    var plotError by remember { mutableStateOf<String?>(null) }

    fun evaluateCalc() {
        calcError = null
        calcResult = try {
            val value = MathEvaluator.evaluate(expression)
            if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
        } catch (e: Exception) {
            calcError = e.message ?: "Fehler"
            null
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(20.dp).width(360.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Mathe", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "Schließen")
                    }
                }
                Spacer(Modifier.height(4.dp))
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Rechner") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Erkennung") })
                    Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Plotter") })
                }
                Spacer(Modifier.height(16.dp))

                when (tab) {
                    0 -> {
                        OutlinedTextField(
                            value = expression,
                            onValueChange = { onExpressionChange(it); calcResult = null; calcError = null },
                            placeholder = { Text("z.B. 3*sin(pi/2) + sqrt(16)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { evaluateCalc() }) { Text("= Auswerten") }
                        calcResult?.let {
                            Text("= $it", style = MaterialTheme.typography.headlineSmall)
                        }
                        calcError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    1 -> {
                        Text("Formel mit Stift/Finger auf dem Notizbuch umkreisen — sie wird erkannt und in den Rechner übernommen.")
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = onRecognizeHandwriting) { Text("Bereich auswählen…") }
                    }
                    2 -> {
                        OutlinedTextField(
                            value = plotExpression,
                            onValueChange = { plotExpression = it; plotError = null },
                            placeholder = { Text("f(x) = sin(x)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = xMinText,
                                onValueChange = { xMinText = it },
                                placeholder = { Text("x min") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = xMaxText,
                                onValueChange = { xMaxText = it },
                                placeholder = { Text("x max") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))

                        val xMin = xMinText.toDoubleOrNull()
                        val xMax = xMaxText.toDoubleOrNull()
                        if (xMin != null && xMax != null && xMax > xMin) {
                            val samples = remember(plotExpression, xMin, xMax) {
                                FunctionPlotRenderer.sample(plotExpression, xMin, xMax, 200)
                            }
                            Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                                val validYs = samples.filterNotNull()
                                if (validYs.isNotEmpty()) {
                                    var yMin = validYs.min()
                                    var yMax = validYs.max()
                                    if (yMin == yMax) { yMin -= 1; yMax += 1 }
                                    fun toOffset(x: Double, y: Double): Offset {
                                        val px = ((x - xMin) / (xMax - xMin)).toFloat() * size.width
                                        val py = size.height - ((y - yMin) / (yMax - yMin)).toFloat() * size.height
                                        return Offset(px, py)
                                    }
                                    var last: Offset? = null
                                    for (i in samples.indices) {
                                        val y = samples[i]
                                        if (y == null) { last = null; continue }
                                        val x = xMin + (xMax - xMin) * i / (samples.size - 1)
                                        val point = toOffset(x, y)
                                        last?.let { drawLine(Color(0xFF007AFF), it, point, strokeWidth = 4f) }
                                        last = point
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = {
                                val bitmap = FunctionPlotRenderer.render(plotExpression, xMin, xMax)
                                onInsertPlot(bitmap)
                                onDismiss()
                            }) { Text("Als Bild einfügen") }
                        } else {
                            plotError = "Ungültiger x-Bereich"
                        }
                        plotError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}
