package com.example.service

import android.content.Context
import android.service.notification.StatusBarNotification
import android.widget.FrameLayout
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import android.app.PendingIntent
import android.graphics.drawable.Icon
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.Image
import androidx.core.graphics.drawable.toBitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.FilterList
import com.example.NotificationHistoryActivity
import android.content.Intent

class NotificationPageView(
    context: Context,
    private val onHeightChanged: (Int) -> Unit
) : FrameLayout(context) {

    private var currentHeightPx: Int = 0

    init {
        addView(ComposeView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Box(modifier = Modifier.onSizeChanged { size ->
                        if (currentHeightPx != size.height) {
                            currentHeightPx = size.height
                            onHeightChanged(size.height)
                        }
                    }) {
                        NotificationScreen(context)
                    }
                }
            }
        })
    }

    fun getCurrentHeightPx(): Int {
        val density = context.resources.displayMetrics.density
        return if (currentHeightPx > 0) currentHeightPx else (450 * density).toInt()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotificationScreen(context: Context) {
    LaunchedEffect(Unit) {
        com.example.LogKeeper.writeLog("Sidebar", "Notification page viewed")
    }
    
    val notifications by AppNotificationListener.notifications.collectAsState()
    val prefs = remember { context.getSharedPreferences("NotificationPrefs", Context.MODE_PRIVATE) }
    
    // We store hidden packages in a Set string in SharedPreferences
    var hiddenPackages by remember { 
        mutableStateOf(prefs.getStringSet("hidden_packages", emptySet()) ?: emptySet())
    }
    
    // Filter dialog
    var showFilterDialog by remember { mutableStateOf(false) }
    
    // Check if permission is granted
    val hasPermission = remember { 
        android.provider.Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )?.contains(context.packageName) == true
    }

    if (!hasPermission) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                Text("Notification Access Required", color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    val intent = android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                }) {
                    Text("Grant Permission")
                }
            }
        }
        return
    }

    val visibleNotifications = notifications.filter { !hiddenPackages.contains(it.packageName) }

    Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Notifications", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Row {
                IconButton(onClick = { showFilterDialog = true }) {
                    Icon(Icons.Default.FilterList, "Filter Apps", tint = Color.White)
                }
                IconButton(onClick = { 
                    val intent = Intent(context, NotificationHistoryActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }) {
                    Icon(Icons.Default.History, "History", tint = Color.White)
                }
            }
        }
        
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(visibleNotifications, key = { it.key }) { sbn ->
                NotificationItem(context, sbn, onHideApp = { pkg ->
                    val updated = hiddenPackages.toMutableSet().apply { add(pkg) }
                    prefs.edit().putStringSet("hidden_packages", updated).apply()
                    hiddenPackages = updated
                })
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
    
    if (showFilterDialog) {
        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            title = { Text("Filter Apps in Sidebar") },
            text = {
                val pm = context.packageManager
                // Get all apps that ever posted a notification in our current active list
                val appsInList = notifications.map { it.packageName to 
                    try { pm.getApplicationLabel(pm.getApplicationInfo(it.packageName, 0)).toString() } 
                    catch(e: Exception) { it.packageName }
                }.distinctBy { it.first }
                
                LazyColumn {
                    items(appsInList) { (pkg, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = !hiddenPackages.contains(pkg),
                                onCheckedChange = { checked ->
                                    val newHidden = hiddenPackages.toMutableSet()
                                    if (checked) {
                                        newHidden.remove(pkg)
                                    } else {
                                        newHidden.add(pkg)
                                    }
                                    hiddenPackages = newHidden
                                    prefs.edit().putStringSet("hidden_packages", newHidden).apply()
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFilterDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotificationItem(context: Context, sbn: StatusBarNotification, onHideApp: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }
    
    val notification = sbn.notification
    val title = notification.extras.getString(android.app.Notification.EXTRA_TITLE) ?: "No Title"
    val text = notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
    
    val pm = context.packageManager
    val appName = try {
        pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
    } catch (e: Exception) {
        sbn.packageName
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (!expanded) {
                        expanded = true
                    } else {
                        try {
                            notification.contentIntent?.send()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                },
                onLongClick = {
                    AppNotificationListener.instance?.cancelNotification(sbn.key)
                }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = appName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.LightGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis
            )
            
            if (text.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray,
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            if (!expanded && (text.length > 30 || notification.actions?.isNotEmpty() == true)) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(1.dp))
                )
            }
            
            if (expanded && notification.actions != null && notification.actions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                
                // Group actions into remote inputs and normal actions
                val remoteInputActions = notification.actions.filter { it.remoteInputs?.isNotEmpty() == true }
                val normalActions = notification.actions.filter { it.remoteInputs.isNullOrEmpty() }
                
                if (remoteInputActions.isNotEmpty()) {
                    val replyAction = remoteInputActions.first()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            placeholder = { Text(replyAction.title?.toString() ?: "Reply...") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (replyText.isNotEmpty()) {
                                    try {
                                        val remoteInputs = replyAction.remoteInputs
                                        val intent = android.content.Intent()
                                        val bundle = android.os.Bundle()
                                        for (input in remoteInputs) {
                                            bundle.putCharSequence(input.resultKey, replyText)
                                        }
                                        android.app.RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)
                                        replyAction.actionIntent.send(context, 0, intent)
                                        replyText = ""
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) {
                            Text("Send")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                if (normalActions.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        normalActions.take(3).forEach { action ->
                            val actionTitle = action.title?.toString() ?: ""
                            if (actionTitle.isNotEmpty()) {
                                Button(
                                    onClick = {
                                        try {
                                            action.actionIntent.send()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF444444)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        actionTitle, 
                                        maxLines = 1, 
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { onHideApp(sbn.packageName) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Hide App in Sidebar", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }
            }
        }
    }
}
