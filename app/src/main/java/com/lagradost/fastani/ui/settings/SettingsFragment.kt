package com.lagradost.fastani.ui.settings

import android.content.DialogInterface
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.*

import androidx.preference.PreferenceFragmentCompat
import com.bumptech.glide.Glide
import com.lagradost.fastani.DataStore.getKeys
import com.lagradost.fastani.DataStore.removeKeys
import com.lagradost.fastani.DataStore.setKey
import com.lagradost.fastani.R
import com.lagradost.fastani.VIEW_DUR_KEY
import com.lagradost.fastani.VIEW_LST_KEY
import com.lagradost.fastani.VIEW_POS_KEY
import com.lagradost.fastani.ui.GlideApp
import java.util.ResourceBundle.clearCache
import kotlin.concurrent.thread

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)
        var easterEggsClicks = 0
        //val saveHistory = findPreference("save_history") as SwitchPreference?
        val clearHistory = findPreference("clear_history") as Preference?
        //setKey(VIEW_POS_KEY, "GGG", 2L)
        val historyItems = getKeys(VIEW_POS_KEY).size

        clearHistory?.summary = "$historyItems item${if (historyItems == 1) "" else "s"}"
        clearHistory?.setOnPreferenceClickListener {
            val alertDialog: AlertDialog? = activity?.let {
                val builder = AlertDialog.Builder(it)
                builder.apply {
                    setPositiveButton("OK",
                        DialogInterface.OnClickListener { dialog, id ->
                            val amount = removeKeys(VIEW_POS_KEY)
                            removeKeys(VIEW_DUR_KEY)
                            removeKeys(VIEW_LST_KEY)
                            if (amount != 0) {
                                Toast.makeText(
                                    context,
                                    "Cleared $amount item${if (amount == 1) "" else "s"}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            clearHistory.summary = "0 items"
                        })
                    setNegativeButton("Cancel",
                        DialogInterface.OnClickListener { dialog, id ->
                            // User cancelled the dialog
                        })
                }
                // Set other dialog properties
                builder.setTitle("Clear search history")

                // Create the AlertDialog
                builder.create()
            }
            if (getKeys(VIEW_POS_KEY).isNotEmpty()) {
                alertDialog?.show()
            }
            return@setOnPreferenceClickListener true
        }
        val clearCache = findPreference("clear_cache") as Preference?
        clearCache?.setOnPreferenceClickListener {
            val glide = Glide.get(requireContext())
            glide.clearMemory()
            thread {
                glide.clearDiskCache()
            }
            Toast.makeText(context, "Cleared image cache", Toast.LENGTH_LONG).show()
            return@setOnPreferenceClickListener true
        }
        // EASTER EGG THEME
        val versionButton = findPreference("version") as Preference?
        val coolMode = findPreference("cool_mode") as SwitchPreference?
        if (coolMode?.isChecked == true) {
            coolMode.isVisible = true
        }
        versionButton?.setOnPreferenceClickListener {
            if (easterEggsClicks == 7 && coolMode?.isChecked == false) {
                Toast.makeText(context, "Unlocked cool mode", Toast.LENGTH_LONG).show()
                coolMode.isVisible = true
            }
            easterEggsClicks++
            return@setOnPreferenceClickListener true
        }
        coolMode?.setOnPreferenceChangeListener { preference, newValue ->
            activity?.recreate()
            return@setOnPreferenceChangeListener true
        }

        /*val autoDarkMode = findPreference("auto_dark_mode") as SwitchPreferenceCompat?
        val darkMode = findPreference("dark_mode") as SwitchPreferenceCompat?
        //darkMode?.isEnabled = autoDarkMode?.isChecked != true
        darkMode?.isChecked =
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        autoDarkMode?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { preference: Preference, any: Any ->
                //darkMode?.isEnabled = any != true
                if (any == true) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                    val isDarkMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    darkMode?.isChecked = isDarkMode == Configuration.UI_MODE_NIGHT_YES
                } else {
                    if (darkMode?.isChecked == true) {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    } else {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    }
                }
                return@OnPreferenceChangeListener true
            }
        darkMode?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { preference: Preference, any: Any ->
                if (any == true) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                }
                return@OnPreferenceChangeListener true
            }
         */
    }
}