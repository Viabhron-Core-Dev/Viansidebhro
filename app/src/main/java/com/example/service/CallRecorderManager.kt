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

import androidx.documentfile.provider.DocumentFile
import android.net.Uri
import android.os.ParcelFileDescriptor

class CallRecorderManager(private val context: Context, private val prefs: SharedPreferences) {

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentRecordFile: File? = null
    private var currentRecordPfd: ParcelFileDescriptor? = null

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
            val formatStr = prefs.getString("call_recorder_format", "MPEG_4") ?: "MPEG_4"
            val quality = prefs.getInt("call_recorder_quality", 128000)
            val saveFolderStr = prefs.getString("call_recorder_save_folder", "") ?: ""
            
            val ext = if (formatStr == "THREE_GPP") "3gp" else "m4a"
            val mime = if (formatStr == "THREE_GPP") "audio/3gpp" else "audio/mp4"
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "CALL_$timeStamp.$ext"

            if (saveFolderStr.isNotEmpty()) {
                try {
                    val uri = Uri.parse(saveFolderStr)
                    val documentFile = DocumentFile.fromTreeUri(context, uri)
                    if (documentFile != null && documentFile.exists()) {
                        val newFile = documentFile.createFile(mime, fileName)
                        if (newFile != null) {
                            currentRecordPfd = context.contentResolver.openFileDescriptor(newFile.uri, "w")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CallRecorder", "Failed SAF", e)
                }
            }

            if (currentRecordPfd == null) {
                val recordsDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), ".Records")
                if (!recordsDir.exists()) {
                    recordsDir.mkdirs()
                }
                val nomedia = File(recordsDir, ".nomedia")
                if (!nomedia.exists()) {
                    nomedia.createNewFile()
                }
                currentRecordFile = File(recordsDir, fileName)
            }

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                val audioSource = prefs.getInt("call_recorder_audio_source", MediaRecorder.AudioSource.VOICE_RECOGNITION)
                setAudioSource(audioSource)
                
                if (formatStr == "THREE_GPP") {
                    setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                } else {
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                }
                
                setAudioEncodingBitRate(quality)
                setAudioSamplingRate(if (formatStr == "THREE_GPP") 8000 else 44100)
                
                if (currentRecordPfd != null) {
                    setOutputFile(currentRecordPfd!!.fileDescriptor)
                } else {
                    setOutputFile(currentRecordFile?.absolutePath)
                }
                
                prepare()
                start()
            }
            isRecording = true
            val savePath = currentRecordFile?.absolutePath ?: "SAF Directory"
            Log.d("CallRecorder", "Started recording to $savePath")
            com.example.LogKeeper.writeLog("CallRecorder", "Started recording to $savePath")
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
            try {
                currentRecordPfd?.close()
            } catch (e: Exception) {
                // Ignore
            }
            currentRecordPfd = null
            mediaRecorder = null
            isRecording = false
        }
    }
}
