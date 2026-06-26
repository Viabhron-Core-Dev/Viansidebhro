package com.example

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class ShortcutHandlerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent.getStringExtra("ACTION_TYPE")
        if (action == "PICK") {
            val pickIntent = Intent(Intent.ACTION_PICK_ACTIVITY)
            pickIntent.putExtra(Intent.EXTRA_INTENT, Intent(Intent.ACTION_CREATE_SHORTCUT))
            pickIntent.putExtra(Intent.EXTRA_TITLE, "Select Shortcut")
            startActivityForResult(pickIntent, 100)
        } else {
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) {
            if (requestCode == 100) {
                val comp = data.component
                if (comp != null) {
                    val createIntent = Intent(Intent.ACTION_CREATE_SHORTCUT)
                    createIntent.component = comp
                    startActivityForResult(createIntent, 101)
                } else {
                    finish()
                }
            } else if (requestCode == 101) {
                val shortcutIntent = data.getParcelableExtra<Intent>(Intent.EXTRA_SHORTCUT_INTENT)
                val name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME) ?: "Shortcut"
                
                if (shortcutIntent != null) {
                    val json = org.json.JSONObject()
                    json.put("url", shortcutIntent.toUri(Intent.URI_INTENT_SCHEME))
                    val id = "shortcut:$name:${json.toString()}"
                    
                    val serviceIntent = Intent(this, com.example.service.FloatingReaderService::class.java)
                    serviceIntent.action = "ADD_ELEMENT"
                    serviceIntent.putExtra("element_id", id)
                    
                    val folderUuid = intent.getStringExtra("FOLDER_UUID")
                    val isElementCallback = intent.getBooleanExtra("IS_ELEMENT_CALLBACK", false)
                    
                    if (folderUuid != null) {
                        serviceIntent.putExtra("FOLDER_UUID", folderUuid)
                    }
                    if (isElementCallback) {
                        serviceIntent.putExtra("IS_ELEMENT_CALLBACK", true)
                    }
                    
                    startService(serviceIntent)
                }
                finish()
            }
        } else {
            finish()
        }
    }
}
