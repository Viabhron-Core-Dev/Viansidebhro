import re

new_code = """
package com.example.service

import android.content.Context
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat

class CalculatorPageView(context: Context) : FrameLayout(context) {
    init {
        com.example.LogKeeper.writeLog("Calculator", "Opened calculator page")
        addView(ComposeView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
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
    var expression by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("") }
    var expressionCompleted by remember { mutableStateOf(false) }

    fun evaluateExpression(expr: String): String {
        try {
            if (expr.isEmpty()) return ""
            // Replace x and ÷ with * and /
            val cleanExpr = expr.replace("x", "*").replace("÷", "/").replace(",", "")
            
            // Simple evaluator
            val result = evalBasic(cleanExpr)
            
            val formatter = DecimalFormat("#,###.########")
            return formatter.format(result)
        } catch (e: Exception) {
            return "Error"
        }
    }

    fun onInput(char: String) {
        if (expressionCompleted) {
            if (char in listOf("+", "-", "x", "÷", "%")) {
                expression = resultText.replace("=", "").trim() + char
            } else {
                expression = char
            }
            expressionCompleted = false
            resultText = ""
        } else {
            expression += char
            val res = evaluateExpression(expression)
            if (res != "Error" && res.isNotEmpty() && expression.any { it in listOf('+', '-', 'x', '÷') }) {
                resultText = "=$res"
            } else {
                resultText = ""
            }
        }
    }

    fun onClear() {
        expression = ""
        resultText = ""
        expressionCompleted = false
    }

    fun onDelete() {
        if (expressionCompleted) {
            expression = ""
            resultText = ""
            expressionCompleted = false
        } else if (expression.isNotEmpty()) {
            expression = expression.dropLast(1)
            val res = evaluateExpression(expression)
            if (res != "Error" && res.isNotEmpty() && expression.any { it in listOf('+', '-', 'x', '÷') }) {
                resultText = "=$res"
            } else {
                resultText = ""
            }
        }
    }

    fun onEqual() {
        if (expression.isNotEmpty()) {
            val res = evaluateExpression(expression)
            if (res != "Error") {
                resultText = "=$res"
                expressionCompleted = true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        // Display
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = formatExpression(expression),
                fontSize = if (expressionCompleted) 32.sp else 48.sp,
                color = if (expressionCompleted) Color.Gray else Color.White,
                textAlign = TextAlign.End,
                lineHeight = 40.sp,
                maxLines = 3
            )
            if (resultText.isNotEmpty()) {
                Text(
                    text = resultText,
                    fontSize = if (expressionCompleted) 48.sp else 32.sp,
                    color = if (expressionCompleted) Color.White else Color.Gray,
                    textAlign = TextAlign.End,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Divider(color = Color.DarkGray, thickness = 1.dp)
        Spacer(modifier = Modifier.height(16.dp))

        // Keypad
        val buttons = listOf(
            listOf("C", "DEL", "%", "÷"),
            listOf("7", "8", "9", "x"),
            listOf("4", "5", "6", "-"),
            listOf("1", "2", "3", "+"),
            listOf("0", "00", ".", "=")
        )
        buttons.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                row.forEach { btn ->
                    val isOperator = btn in listOf("÷", "x", "-", "+", "=")
                    val isTopAction = btn in listOf("C", "DEL", "%")
                    
                    val textColor = when {
                        isOperator -> Color(0xFFF06A35) // Orange color from screenshot
                        isTopAction -> Color(0xFFF06A35)
                        else -> Color.White
                    }
                    val bgColor = when {
                        btn == "=" -> Color(0xFFF06A35)
                        else -> Color.Transparent
                    }
                    val finalTextColor = if (btn == "=") Color.White else textColor
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(4.dp)
                            .background(bgColor, CircleShape)
                            .clickable {
                                when (btn) {
                                    "C" -> onClear()
                                    "DEL" -> onDelete()
                                    "=" -> onEqual()
                                    else -> onInput(btn)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (btn == "DEL") {
                            Icon(Icons.Default.Backspace, contentDescription = "Delete", tint = finalTextColor)
                        } else {
                            Text(
                                text = btn, 
                                fontSize = 28.sp, 
                                fontWeight = if (isOperator || isTopAction) FontWeight.Medium else FontWeight.Normal,
                                color = finalTextColor
                            )
                        }
                    }
                }
            }
        }
    }
}

fun formatExpression(expr: String): String {
    // A simple formatter that tries to format numbers with commas while preserving operators
    val regex = Regex("(\\\\d+\\\\.?\\\\d*)")
    return regex.replace(expr) { matchResult ->
        try {
            val numStr = matchResult.value
            if (numStr.contains(".")) {
                val parts = numStr.split(".")
                val formatter = DecimalFormat("#,###")
                formatter.format(parts[0].toLong()) + "." + parts[1]
            } else {
                val formatter = DecimalFormat("#,###")
                formatter.format(numStr.toLong())
            }
        } catch (e: Exception) {
            matchResult.value
        }
    }
}

fun evalBasic(str: String): Double {
    return object : Any() {
        var pos = -1
        var ch = 0

        fun nextChar() {
            ch = if (++pos < str.length) str[pos].toInt() else -1
        }

        fun eat(charToEat: Int): Boolean {
            while (ch == ' '.toInt()) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < str.length) throw RuntimeException("Unexpected: " + ch.toChar())
            return x
        }

        fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                if (eat('+'.toInt())) x += parseTerm() // addition
                else if (eat('-'.toInt())) x -= parseTerm() // subtraction
                else return x
            }
        }

        fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                if (eat('*'.toInt())) x *= parseFactor() // multiplication
                else if (eat('/'.toInt())) x /= parseFactor() // division
                else if (eat('%'.toInt())) x %= parseFactor() // modulo
                else return x
            }
        }

        fun parseFactor(): Double {
            if (eat('+'.toInt())) return parseFactor() // unary plus
            if (eat('-'.toInt())) return -parseFactor() // unary minus
            var x: Double
            val startPos = pos
            if (eat('('.toInt())) { // parentheses
                x = parseExpression()
                eat(')'.toInt())
            } else if (ch >= '0'.toInt() && ch <= '9'.toInt() || ch == '.'.toInt()) { // numbers
                while (ch >= '0'.toInt() && ch <= '9'.toInt() || ch == '.'.toInt()) nextChar()
                x = str.substring(startPos, pos).toDouble()
            } else {
                throw RuntimeException("Unexpected: " + ch.toChar())
            }
            return x
        }
    }.parse()
}
"""

with open('app/src/main/java/com/example/service/CalculatorPageView.kt', 'w') as f:
    f.write(new_code.strip() + "\n")

print("Patched.")
