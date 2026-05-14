package com.example.finlern.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "notification_preferences")

class NotificationPreferencesManager(private val context: Context) {
    companion object {
        private val DIRECT_MESSAGES_ENABLED = booleanPreferencesKey("direct_messages_enabled")
        private val TOPIC_NOTIFICATIONS_ENABLED = booleanPreferencesKey("topic_notifications_enabled")
    }

    val directMessagesEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DIRECT_MESSAGES_ENABLED] ?: true
    }

    val topicNotificationsEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[TOPIC_NOTIFICATIONS_ENABLED] ?: true
    }

    suspend fun setDirectMessagesEnabled(enabled: Boolean) {
        try {
            context.dataStore.edit { preferences ->
                preferences[DIRECT_MESSAGES_ENABLED] = enabled
            }
        } catch (e: Exception) {
            Log.e("NotificationPrefs", "Error saving direct message preference: ${e.message}")
            throw e
        }
    }

    suspend fun setTopicNotificationsEnabled(enabled: Boolean) {
        try {
            context.dataStore.edit { preferences ->
                preferences[TOPIC_NOTIFICATIONS_ENABLED] = enabled
            }
        } catch (e: Exception) {
            Log.e("NotificationPrefs", "Error saving topic notification preference: ${e.message}")
            throw e
        }
    }
} 