package com.example.service

import android.content.Context
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

class CalculatorPageView(context: Context) : FrameLayout(context) {

    init {
        addView(ComposeView(context).apply {
            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    CalculatorScreen()
                }
            }
        })
    }
}

@Composable
fun CalculatorScreen() {
    var display by remember { mutableStateOf("0") }
    var operand1 by remember { mutableStateOf<Double?>(null) }
    var operator by remember { mutableStateOf<String?>(null) }
    var isNewOperand by remember { mutableStateOf(false) }

    fun onNumber(number: String) {
        if (isNewOperand) {
            display = number
            isNewOperand = false
        } else {
            display = if (display == "0") number else display + number
        }
    }

    fun onOperator(op: String) {
        val currentVal = display.toDoubleOrNull() ?: 0.0
        if (operator != null && operand1 != null && !isNewOperand) {
            val result = when (operator) {
                "+" -> operand1!! + currentVal
                "-" -> operand1!! - currentVal
                "*" -> operand1!! * currentVal
                "/" -> if (currentVal != 0.0) operand1!! / currentVal else 0.0
                else -> currentVal
            }
            display = formatResult(result)
            operand1 = result
        } else {
            operand1 = currentVal
        }
        operator = op
        isNewOperand = true
    }

    fun onEqual() {
        val currentVal = display.toDoubleOrNull() ?: 0.0
        if (operator != null && operand1 != null) {
            val result = when (operator) {
                "+" -> operand1!! + currentVal
                "-" -> operand1!! - currentVal
                "*" -> operand1!! * currentVal
                "/" -> if (currentVal != 0.0) operand1!! / currentVal else 0.0
                else -> currentVal
            }
            display = formatResult(result)
            operand1 = null
            operator = null
            isNewOperand = true
        }
    }

    fun onClear() {
        display = "0"
        operand1 = null
        operator = null
        isNewOperand = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                .padding(16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Text(
                text = display,
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.End,
                maxLines = 1
            )
        }

        // Keypad
        val buttons = listOf(
            listOf("C", "±", "%", "/"),
            listOf("7", "8", "9", "*"),
            listOf("4", "5", "6", "-"),
            listOf("1", "2", "3", "+"),
            listOf("0", ".", "=")
        )

        buttons.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { btn ->
                    val isWide = btn == "0"
                    Button(
                        onClick = {
                            when (btn) {
                                "C" -> onClear()
                                "=" -> onEqual()
                                "+", "-", "*", "/" -> onOperator(btn)
                                "±" -> {
                                    if (display.startsWith("-")) {
                                        display = display.substring(1)
                                    } else if (display != "0") {
                                        display = "-$display"
                                    }
                                }
                                "%" -> {
                                    val currentVal = display.toDoubleOrNull() ?: 0.0
                                    display = formatResult(currentVal / 100)
                                }
                                else -> onNumber(btn)
                            }
                        },
                        modifier = Modifier
                            .weight(if (isWide) 2f else 1f)
                            .aspectRatio(if (isWide) 2f else 1f, matchHeightConstraintsFirst = false),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when {
                                btn in listOf("/", "*", "-", "+", "=") -> MaterialTheme.colorScheme.primary
                                btn in listOf("C", "±", "%") -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            contentColor = when {
                                btn in listOf("/", "*", "-", "+", "=") -> MaterialTheme.colorScheme.onPrimary
                                btn in listOf("C", "±", "%") -> MaterialTheme.colorScheme.onSecondary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    ) {
                        Text(text = btn, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}

fun formatResult(res: Double): String {
    val stringRes = res.toString()
    return if (stringRes.endsWith(".0")) {
        stringRes.substring(0, stringRes.length - 2)
    } else {
        stringRes
    }
}
