package com.example.fastani

import android.content.pm.ActivityInfo
import android.content.res.Resources
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlin.concurrent.thread

import androidx.navigation.Navigation
import androidx.preference.AndroidResources
import com.example.fastani.ui.PlayerFragment
import com.example.fastani.ui.result.ResultFragment

val Int.toPx: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()
val Int.toDp: Int get() = (this / Resources.getSystem().displayMetrics.density).toInt()

class MainActivity : AppCompatActivity() {

    companion object {
        var navController: NavController? = null
        var statusHeight: Int = 0
        var activity: MainActivity? = null

        fun loadPage(card: FastAniApi.Card) {

            activity?.supportFragmentManager?.beginTransaction()
                ?.replace(R.id.homeRoot, ResultFragment(card))
                ?.commit()
            /*
            activity?.runOnUiThread {
                val _navController = Navigation.findNavController(activity!!, R.id.nav_host_fragment)
                _navController?.navigateUp()
                _navController?.navigate(R.layout.fragment_results,null,null)
            }
*/
            // NavigationUI.navigateUp(navController!!,R.layout.fragment_results)
        }
    }

    override fun onBackPressed() {
        if(supportFragmentManager.fragments.size > 2) {
            val currentFragment = supportFragmentManager.fragments.last()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            supportFragmentManager.beginTransaction().remove(currentFragment).commit()
        }
        else {
            super.onBackPressed()
        }
    }

    fun getStatusBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        statusHeight = getStatusBarHeight()
        activity = this
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        // Setting the theme
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val autoDarkMode = settingsManager.getBoolean("auto_dark_mode", true)
        val darkMode = settingsManager.getBoolean("dark_mode", false)

        if (autoDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        } else {
            if (darkMode) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val navView: BottomNavigationView = findViewById(R.id.nav_view)

        DataStore.init(this)
        thread {
            FastAniApi.init()
        }


        navController = findNavController(R.id.nav_host_fragment)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_search, R.id.navigation_settings
            )
        )
        //setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController!!)
    }
}