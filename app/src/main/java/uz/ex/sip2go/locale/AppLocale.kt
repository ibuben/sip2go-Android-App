package uz.ex.sip2go.locale

import android.content.Context
import android.content.res.Configuration
import uz.ex.sip2go.data.SettingsStore
import java.util.Locale

object AppLocale {
    fun wrap(context: Context, languageTag: String?): Context {
        if (languageTag.isNullOrBlank()) {
            return context
        }
        val locale = Locale.forLanguageTag(languageTag)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    fun withAppLocale(context: Context): Context {
        return try {
            wrap(context, SettingsStore.readAppLanguageTag(context))
        } catch (e: Exception) {
            context
        }
    }
}
