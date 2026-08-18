package com.whatstats.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.whatstats.ui.feedback.FeedbackNotificationHelper
import com.whatstats.util.DebugLogManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WhatStatFirebaseMessagingService : FirebaseMessagingService() {
    @Inject
    lateinit var notificationHelper: FeedbackNotificationHelper

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        DebugLogManager.log("WhatStatFirebaseMessagingService.onNewToken | token=${token.take(10)}...")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        DebugLogManager.log(
            "WhatStatFirebaseMessagingService.onMessageReceived | from=${remoteMessage.from} data=${remoteMessage.data}",
        )
        // Se la notifica riguarda un feedback reply o contiene il payload appropriato
        notificationHelper.showReplyNotification()
    }
}
