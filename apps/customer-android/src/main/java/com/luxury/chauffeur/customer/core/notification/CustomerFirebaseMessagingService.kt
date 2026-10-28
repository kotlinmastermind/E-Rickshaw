package com.luxury.chauffeur.customer.core.notification

import com.google.firebase.messaging.FirebaseMessagingService
import com.luxury.chauffeur.customer.core.network.ApiClient
import com.luxury.chauffeur.customer.core.network.CustomerDeviceTokenPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CustomerFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (ApiClient.getToken().isNullOrBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { ApiClient.getService().registerCustomerDeviceToken(CustomerDeviceTokenPayload(token)) }
        }
    }
}
