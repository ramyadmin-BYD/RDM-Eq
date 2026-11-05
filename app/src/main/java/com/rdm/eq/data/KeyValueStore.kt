package com.rdm.eq.data

import android.content.Context

/** Tiny persistence abstraction so repositories can be unit-tested without Android. */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

class SharedPrefsStore(context: Context) : KeyValueStore {
    private val prefs = context.getSharedPreferences("rdm_eq", Context.MODE_PRIVATE)
    override fun getString(key: String): String? = prefs.getString(key, null)
    // commit() so values are on disk before a head unit can cut power.
    override fun putString(key: String, value: String) { prefs.edit().putString(key, value).commit() }
    override fun remove(key: String) { prefs.edit().remove(key).commit() }
}

class InMemoryStore : KeyValueStore {
    val map = HashMap<String, String>()
    override fun getString(key: String) = map[key]
    override fun putString(key: String, value: String) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
}
