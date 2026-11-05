package com.rdm.eq.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Logcat plus an in-memory ring buffer shown on the Audio Status screen. */
object DiagLog {
    private const val TAG = "RDMEq"
    private const val MAX = 200
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines

    fun i(msg: String) = add("I", msg).also { Log.i(TAG, msg) }
    fun w(msg: String, t: Throwable? = null) = add("W", msg + (t?.let { ": ${it.javaClass.simpleName} ${it.message}" } ?: "")).also { Log.w(TAG, msg, t) }
    fun e(msg: String, t: Throwable? = null) = add("E", msg + (t?.let { ": ${it.javaClass.simpleName} ${it.message}" } ?: "")).also { Log.e(TAG, msg, t) }

    @Synchronized
    private fun add(level: String, msg: String) {
        _lines.value = (_lines.value + "${fmt.format(Date())} $level $msg").takeLast(MAX)
    }
}
