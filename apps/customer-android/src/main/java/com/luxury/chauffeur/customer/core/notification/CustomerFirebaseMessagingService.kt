package com.luxury.chauffeur.customer.core.notification

import com.google.firebase.messaging.FirebaseMessagingService

class CustomerFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        CustomerFcmManager.onNewToken(applicationContext, token)
    }
}
