package com.bullionlive

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class FetchReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_FETCH_DATA = "com.bullionlive.ACTION_FETCH_DATA"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_FETCH_DATA) {
            FetchService.enqueueWork(context, Intent(context, FetchService::class.java))
        }
    }
}
