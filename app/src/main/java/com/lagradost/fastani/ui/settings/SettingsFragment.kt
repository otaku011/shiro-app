package com.lagradost.fastani.ui.settings

import android.content.*
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.*

import androidx.preference.PreferenceFragmentCompat
import com.bumptech.glide.Glide
import com.lagradost.fastani.*
import com.lagradost.fastani.AniListApi.Companion.getAllSeasons
import com.lagradost.fastani.AniListApi.Companion.getSeason
import com.lagradost.fastani.DataStore.getKeys
import com.lagradost.fastani.DataStore.removeKeys
import com.lagradost.fastani.MainActivity.Companion.isInResult
import com.lagradost.fastani.MainActivity.Companion.md5
import com.lagradost.fastani.R
import com.lagradost.fastani.VIEW_LST_KEY
import kotlin.concurrent.thread

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)
        var easterEggClicks = 0
        //val saveHistory = findPreference("save_history") as SwitchPreference?
        val clearHistory = findPreference("clear_history") as Preference?
        //setKey(VIEW_POS_KEY, "GGG", 2L)
        val historyItems = getKeys(VIEW_POS_KEY).size + getKeys(VIEWSTATE_KEY).size

        clearHistory?.summary = "$historyItems item${if (historyItems == 1) "" else "s"}"
        clearHistory?.setOnPreferenceClickListener {
            val alertDialog: AlertDialog? = activity?.let {
                val builder = AlertDialog.Builder(it)
                builder.apply {
                    setPositiveButton("OK",
                        DialogInterface.OnClickListener { dialog, id ->
                            val amount = removeKeys(VIEW_POS_KEY) + removeKeys(VIEWSTATE_KEY)
                            removeKeys(VIEW_LST_KEY)
                            removeKeys(VIEW_DUR_KEY)
                            if (amount != 0) {
                                Toast.makeText(
                                    context,
                                    "Cleared $amount item${if (amount == 1) "" else "s"}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            thread {
                                FastAniApi.requestHome(true)
                            }
                            clearHistory.summary = "0 items"
                        })
                    setNegativeButton("Cancel",
                        DialogInterface.OnClickListener { dialog, id ->
                            // User cancelled the dialog
                        })
                }
                // Set other dialog properties
                builder.setTitle("Clear watch history")

                // Create the AlertDialog
                builder.create()
            }
            if (getKeys(VIEW_POS_KEY).isNotEmpty() || getKeys(VIEWSTATE_KEY).isNotEmpty()) {
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

        val donatorId = findPreference("donator_id") as Preference?
        val id: String = Settings.Secure.getString(context?.contentResolver, Settings.Secure.ANDROID_ID)

        val encodedString = id.md5()
        donatorId?.summary = if (isInResult) "Thanks for the donation :D" else encodedString
        donatorId?.setOnPreferenceClickListener {
            val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData = ClipData.newPlainText("ID", encodedString)
            clipboard.primaryClip = clip
            Toast.makeText(
                context!!,
                "Copied donor ID, give this to the devs to enable donor mode (if you have donated)",
                Toast.LENGTH_LONG
            ).show()
            return@setOnPreferenceClickListener true
        }


        val anilistButton = findPreference("anilist_setting_btt") as Preference?
        anilistButton?.setOnPreferenceClickListener {
            AniListApi.authenticate()
            return@setOnPreferenceClickListener true
        }

        // Changelog
        val changeLog = findPreference("changelog") as Preference?
        changeLog?.setOnPreferenceClickListener {
            val alertDialog: AlertDialog? = activity?.let {
                val builder = AlertDialog.Builder(it)
                builder.apply {
                    setPositiveButton("OK") { _, _ -> }
                }
                // Set other dialog properties
                builder.setTitle(getString(R.string.version_code))
                builder.setMessage(getString(R.string.changelog))
                // Create the AlertDialog
                builder.create()
            }
            alertDialog?.show()
            return@setOnPreferenceClickListener true
        }
        val checkUpdates = findPreference("check_updates") as Preference?
        checkUpdates?.setOnPreferenceClickListener {
            thread {
                val update = FastAniApi.getAppUpdate()
                activity?.runOnUiThread {
                    if (update.shouldUpdate && update.updateVersion != null && update.updateURL != null) {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.updateURL)))
                        Toast.makeText(context, "New version (${update.updateVersion}) found", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "No updates found :(", Toast.LENGTH_LONG).show()
                    }
                }
            }
            return@setOnPreferenceClickListener true
        }
        val statusBarHidden = findPreference("statusbar_hidden") as SwitchPreference?
        statusBarHidden?.setOnPreferenceChangeListener { _, newValue ->
            MainActivity.changeStatusBarState(newValue == true)
            return@setOnPreferenceChangeListener true
        }

        // EASTER EGG THEME
        val versionButton = findPreference("version") as Preference?
        val coolMode = findPreference("cool_mode") as SwitchPreference?
        if (coolMode?.isChecked == true) {
            coolMode.isVisible = true
        }
        versionButton?.setOnPreferenceClickListener {
            if (easterEggClicks == 7 && coolMode?.isChecked == false) {
                Toast.makeText(context, "Unlocked cool mode", Toast.LENGTH_LONG).show()
                coolMode.isVisible = true
            }
            easterEggClicks++
            return@setOnPreferenceClickListener true
        }
        coolMode?.setOnPreferenceChangeListener { preference, newValue ->
            activity?.recreate()
            return@setOnPreferenceChangeListener true
        }
        val allowRotation = findPreference("rotation_enabled") as SwitchPreference?
        allowRotation?.setOnPreferenceChangeListener { preference, newValue ->
            if (newValue == true) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
            } else {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
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
                    AppCompatDelegate.setDefaultNightMode(AppCompatD        video_next_holder.isClickable = isClickelegate.MODE_NIGHT_YES)
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                }
                return@OnPreferenceChangeListener true
            }
         */
    }
}