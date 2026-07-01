package com.example.service

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.db.AppDatabase
import com.example.db.NotificationHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AppNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private val _notifications = MutableStateFlow<List<StatusBarNotification>>(emptyList())
        val notifications: StateFlow<List<StatusBarNotification>> = _notifications
        
        var instance: AppNotificationListener? = null
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        updateNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        updateNotifications()
        
        // Record history
        if (sbn.isClearable) {
            val prefs = getSharedPreferences("NotificationPrefs", Context.MODE_PRIVATE)
            val historyHidden = prefs.getStringSet("history_hidden_packages", prefs.getStringSet("hidden_packages", emptySet())) ?: emptySet()
            if (!historyHidden.contains(sbn.packageName)) {
                scope.launch {
                    try {
                        val notification = sbn.notification
                        val title = notification.extras.getString(android.app.Notification.EXTRA_TITLE) ?: ""
                        val text = notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
                        
                        // Ignore empty notifications
                        if (title.isBlank() && text.isBlank()) return@launch
                        
                        val pm = packageManager
                        val appName = try {
                            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
                        } catch (e: Exception) {
                            sbn.packageName
                        }
                        
                        val dao = AppDatabase.getDatabase(this@AppNotificationListener).notificationHistoryDao()
                        dao.insert(
                            NotificationHistory(
                                packageName = sbn.packageName,
                                appName = appName,
                                title = title,
                                text = text,
                                timestamp = sbn.postTime
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("AppNotificationListener", "Error saving history", e)
                        com.example.LogKeeper.writeLog("AppNotificationListener", "Error saving history: ${e.message}")
                    }
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        updateNotifications()
    }

    fun updateNotifications() {
        try {
            val current = activeNotifications?.toList()?.filter { 
                it.isClearable || it.notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0 
            } ?: emptyList()
            _notifications.value = current.sortedByDescending { it.postTime }
        } catch (e: Exception) {
            Log.e("AppNotificationListener", "Error getting notifications", e)
            com.example.LogKeeper.writeLog("AppNotificationListener", "Error getting notifications: ${e.message}")
        }
    }
}

