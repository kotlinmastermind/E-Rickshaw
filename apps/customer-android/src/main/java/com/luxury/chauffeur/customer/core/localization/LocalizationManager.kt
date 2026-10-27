package com.luxury.chauffeur.customer.core.localization

import androidx.compose.runtime.*
import com.luxury.chauffeur.customer.core.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LocalizationManager {
    private val translationCache = mutableStateMapOf<String, String>()
    var currentLocale by mutableStateOf("en")
        private set

    suspend fun fetchTranslations(locale: String = "en") {
        withContext(Dispatchers.IO) {
            try {
                val res = ApiClient.getService().getLocalizationTranslations(locale)
                if (res.isSuccessful && res.body() != null) {
                    val mapObj = res.body()!!["translations"] as? Map<*, *>
                    if (mapObj != null) {
                        withContext(Dispatchers.Main) {
                            currentLocale = locale
                            translationCache.clear()
                            mapObj.forEach { (k, v) ->
                                if (k is String && v is String) {
                                    translationCache[k] = v
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Fallback to cached default
            }
        }
    }

    fun getString(key: String, defaultText: String): String {
        return translationCache[key] ?: defaultText
    }
}

@Composable
fun localizedString(key: String, defaultText: String): String {
    val locale = LocalizationManager.currentLocale
    LaunchedEffect(locale) {
        if (LocalizationManager.getString(key, "").isEmpty()) {
            LocalizationManager.fetchTranslations(locale)
        }
    }
    return LocalizationManager.getString(key, defaultText)
}
