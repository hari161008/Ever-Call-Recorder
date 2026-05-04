// SPDX-License-Identifier: GPL-3.0-or-later
package com.coolappstore.evercallrecorder.by.svhp.notify

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.coolappstore.evercallrecorder.by.svhp.R
import com.coolappstore.evercallrecorder.by.svhp.recorder.RecorderController
import com.coolappstore.evercallrecorder.by.svhp.telephony.CallMonitorService
import com.coolappstore.evercallrecorder.by.svhp.ui.MainActivity

object RecordingNotification {

    const val ID = 0xC411

    fun build(ctx: Context, outcome: RecorderController.Outcome): Notification {
        val tap = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            ctx, 1,
            Intent(ctx, CallMonitorService::class.java).setAction(CallMonitorService.ACTION_MANUAL_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = when (outcome) {
            is RecorderController.Outcome.Dual -> ctx.getString(R.string.notif_recording_text_dual)
            is RecorderController.Outcome.Single -> ctx.getString(R.string.notif_recording_text_single)
            is RecorderController.Outcome.Failed -> outcome.reason
        }
        return NotificationCompat.Builder(ctx, NotificationChannels.ID_RECORDING)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(ctx.getString(R.string.notif_recording_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(tap)
            .addAction(0, ctx.getString(R.string.notif_recording_action_stop), stop)
            .build()
    }
}
