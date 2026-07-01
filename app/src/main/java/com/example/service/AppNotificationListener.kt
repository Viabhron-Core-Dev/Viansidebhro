package com.example.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AppNotificationListener : NotificationListenerService() {

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
        }
    }
}
