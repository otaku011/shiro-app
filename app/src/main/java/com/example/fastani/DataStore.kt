package com.example.fastani

import android.content.Context
import android.content.SharedPreferences
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule

const val PREFERENCES_NAME: String = "rebuild_preference"
const val VIEW_POS_KEY: String = "view_pos" // VIDEO POSITION
const val VIEW_DUR_KEY: String = "view_dur" // VIDEO DURATION
const val VIEW_LST_KEY: String = "view_lst" // LAST WATCHED, ONE PER TITLE ID

object DataStore {

    val mapper: JsonMapper = JsonMapper.builder().addModule(KotlinModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    private var localContext: Context? = null

    fun getSharedPrefs(): SharedPreferences {
        return getPreferences(localContext!!)
    }

    fun init(_context: Context) {
        localContext = _context
    }

    fun getFolderName(folder: String, path: String): String {
        return "${folder}/${path}"
    }

    fun getKeys(path: String) {
        val keys = getSharedPrefs().all.keys
        
    }

    fun <T> setKey(path: String, value: T) {
        val editor: SharedPreferences.Editor = getSharedPrefs().edit()
        editor.putString(path, mapper.writeValueAsString(value))
        editor.apply()
    }

    fun <T> setKey(folder: String, path: String, value: T) {
        setKey(getFolderName(folder, path), value)
    }

    inline fun <reified T : Any> String.toKotlinObject(): T {
        return mapper.readValue(this, T::class.java)
    }

    // GET KEY GIVEN PATH AND DEFAULT VALUE, NULL IF ERROR
    inline fun <reified T : Any> getKey(path: String, defVal: T?): T? {
        try {
            val json: String = getSharedPrefs().getString(path, null) ?: return defVal
            return json.toKotlinObject()
        } catch (e: Exception) {
            return null
        }
    }

    inline fun <reified T : Any> getKey(path: String): T? {
        return getKey(path, null)
    }

    inline fun <reified T : Any> getKey(folder: String, path: String): T? {
        return getKey(getFolderName(folder, path), null)
    }

    inline fun <reified T : Any> getKey(folder: String, path: String, defVal: T?): T? {
        return getKey(getFolderName(folder, path), defVal)
    }
}