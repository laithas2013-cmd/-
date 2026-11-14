package com.example.core.i18n

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.tawasulDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "tawasul_prefs")

class PreferencesManager(private val context: Context) {
  private val languageKey = stringPreferencesKey("app_language")
  private val themeKey = stringPreferencesKey("theme_mode")

  val languageFlow: Flow<AppLanguage> =
    context.tawasulDataStore.data.map { prefs -> AppLanguage.fromCode(prefs[languageKey]) }

  val themeModeFlow: Flow<ThemeMode> =
    context.tawasulDataStore.data.map { prefs -> ThemeMode.fromValue(prefs[themeKey]) }

  suspend fun setLanguage(language: AppLanguage) {
    context.tawasulDataStore.edit { prefs -> prefs[languageKey] = language.code }
  }

  suspend fun setThemeMode(mode: ThemeMode) {
    context.tawasulDataStore.edit { prefs -> prefs[themeKey] = mode.value }
  }
}
