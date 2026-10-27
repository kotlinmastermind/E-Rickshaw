package com.luxury.chauffeur.driver.core.localization

import androidx.compose.runtime.*
import com.luxury.chauffeur.driver.core.network.DriverApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DriverLocalizationManager {
    private val translationCache = mutableStateMapOf<String, String>()
    var currentLocale by mutableStateOf("en")
        private set

    suspend fun fetchTranslations(locale: String = "en") {
        withContext(Dispatchers.IO) {
            try {
                val res = DriverApiClient.getService().getLocalizationTranslations(locale)
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
            } catch (_: Exception) {}
        }
    }

    fun getString(key: String, defaultText: String): String {
        return translationCache[key] ?: defaultText
    }
}

@Composable
fun driverLocalizedString(key: String, defaultText: String): String {
    val locale = DriverLocalizationManager.currentLocale
    LaunchedEffect(locale) {
        if (DriverLocalizationManager.getString(key, "").isEmpty()) {
            DriverLocalizationManager.fetchTranslations(locale)
        }
    }
    return DriverLocalizationManager.getString(key, defaultText)
}
