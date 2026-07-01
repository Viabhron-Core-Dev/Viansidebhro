package com.example.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class CompassPageView(context: Context) : FrameLayout(context), SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    
    private var gravity: FloatArray? = null
    private var geomagnetic: FloatArray? = null
    
    private val azimuthState = mutableFloatStateOf(0f)

    init {
        com.example.LogKeeper.writeLog("Compass", "Opened compass page")
        addView(ComposeView(context).apply {
            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    CompassScreen(azimuthState.floatValue)
                }
            }
        })
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        magnetometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            gravity = event.values.clone()
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            geomagnetic = event.values.clone()
        }
        
        if (gravity != null && geomagnetic != null) {
            val r = FloatArray(9)
            val i = FloatArray(9)
            if (SensorManager.getRotationMatrix(r, i, gravity, geomagnetic)) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(r, orientation)
                // convert from radians to degrees
                var azimuthInDegrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
                if (azimuthInDegrees < 0) {
                    azimuthInDegrees += 360f
                }
                azimuthState.floatValue = azimuthInDegrees
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }
}

@Composable
fun CompassScreen(azimuth: Float) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Compass", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(32.dp))
        
        Box(contentAlignment = Alignment.Center) {
            val primaryColor = MaterialTheme.colorScheme.primary
            val onSurfaceColor = MaterialTheme.colorScheme.onSurface
            
            Canvas(modifier = Modifier.size(250.dp)) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.width / 2
                
                // Draw outer ring
                drawCircle(
                    color = onSurfaceColor.copy(alpha = 0.2f),
                    radius = radius,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
                )
                
                rotate(degrees = -azimuth) {
                    // Draw North pointer
                    drawLine(
                        color = Color.Red,
                        start = center,
                        end = Offset(center.x, 20.dp.toPx()),
                        strokeWidth = 8.dp.toPx()
                    )
                    // Draw South pointer
                    drawLine(
                        color = onSurfaceColor,
                        start = center,
                        end = Offset(center.x, size.height - 20.dp.toPx()),
                        strokeWidth = 8.dp.toPx()
                    )
                    
                    // Draw markers N, E, S, W (can be complex, let's just draw some ticks)
                    for (i in 0 until 360 step 30) {
                        rotate(degrees = i.toFloat()) {
                            val lineLength = if (i % 90 == 0) 15.dp.toPx() else 8.dp.toPx()
                            drawLine(
                                color = onSurfaceColor,
                                start = Offset(center.x, 0f),
                                end = Offset(center.x, lineLength),
                                strokeWidth = if (i % 90 == 0) 4.dp.toPx() else 2.dp.toPx()
                            )
                        }
                    }
                }
                
                // Draw center dot
                drawCircle(
                    color = primaryColor,
                    radius = 8.dp.toPx()
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "${azimuth.toInt()}°",
            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        
        val direction = when {
            azimuth >= 337.5 || azimuth < 22.5 -> "N"
            azimuth >= 22.5 && azimuth < 67.5 -> "NE"
            azimuth >= 67.5 && azimuth < 112.5 -> "E"
            azimuth >= 112.5 && azimuth < 157.5 -> "SE"
            azimuth >= 157.5 && azimuth < 202.5 -> "S"
            azimuth >= 202.5 && azimuth < 247.5 -> "SW"
            azimuth >= 247.5 && azimuth < 292.5 -> "W"
            else -> "NW"
        }
        Text(text = direction, style = MaterialTheme.typography.titleLarge)
    }
}
