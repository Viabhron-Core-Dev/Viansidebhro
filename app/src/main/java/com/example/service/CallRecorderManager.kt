package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.telephony.TelephonyCallback
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallRecorderManager(private val context: Context, private val prefs: SharedPreferences) {

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentRecordFile: File? = null

    private var phoneStateListener: PhoneStateListener? = null
    private var telephonyCallback: TelephonyCallback? = null

    @SuppressLint("MissingPermission")
    fun startListening() {
        if (!prefs.getBoolean("call_recorder_enabled", false)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    handleCallState(state, null)
                }
            }
            try {
                telephonyManager.registerTelephonyCallback(context.mainExecutor, telephonyCallback!!)
            } catch (e: Exception) {
                Log.e("CallRecorder", "Failed to register callback", e)
                com.example.LogKeeper.writeLog("CallRecorder", "Failed to register callback: ${e.message}")
            }
        } else {
            phoneStateListener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    handleCallState(state, phoneNumber)
                }
            }
            try {
                @Suppress("DEPRECATION")
                telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
            } catch (e: Exception) {
                Log.e("CallRecorder", "Failed to register listener", e)
                com.example.LogKeeper.writeLog("CallRecorder", "Failed to register listener: ${e.message}")
            }
        }
    }

    fun stopListening() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyCallback?.let { telephonyManager.unregisterTelephonyCallback(it) }
            } else {
                phoneStateListener?.let { 
                    @Suppress("DEPRECATION")
                    telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE) 
                }
            }
        } catch (e: Exception) {}
        stopRecording()
    }

    private fun handleCallState(state: Int, phoneNumber: String?) {
        if (!prefs.getBoolean("call_recorder_enabled", false)) {
            stopRecording()
            return
        }

        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                startRecording()
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                stopRecording()
            }
            TelephonyManager.CALL_STATE_RINGING -> {
                // Not recording yet
            }
        }
    }

    private fun startRecording() {
        if (isRecording) return
        
        try {
            val recordsDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), ".Records")
            if (!recordsDir.exists()) {
                recordsDir.mkdirs()
            }
            val nomedia = File(recordsDir, ".nomedia")
            if (!nomedia.exists()) {
                nomedia.createNewFile()
            }

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            currentRecordFile = File(recordsDir, "CALL_$timeStamp.m4a")

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                // VOICE_RECOGNITION is often best to capture both sides on modern Android
                val audioSource = prefs.getInt("call_recorder_audio_source", MediaRecorder.AudioSource.VOICE_RECOGNITION)
                setAudioSource(audioSource)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                
                // Increase sensitivity / quality
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                
                setOutputFile(currentRecordFile?.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            Log.d("CallRecorder", "Started recording to ${currentRecordFile?.absolutePath}")
            com.example.LogKeeper.writeLog("CallRecorder", "Started recording to ${currentRecordFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e("CallRecorder", "Failed to start recording", e)
            com.example.LogKeeper.writeLog("CallRecorder", "Failed to start recording: ${e.message}")
            stopRecording()
        }
    }

    private fun stopRecording() {
        if (!isRecording) return
        try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
            Log.d("CallRecorder", "Stopped recording")
            com.example.LogKeeper.writeLog("CallRecorder", "Stopped recording")
        } catch (e: Exception) {
            Log.e("CallRecorder", "Failed to stop recording", e)
            com.example.LogKeeper.writeLog("CallRecorder", "Failed to stop recording: ${e.message}")
        } finally {
            mediaRecorder = null
            isRecording = false
        }
    }
}
