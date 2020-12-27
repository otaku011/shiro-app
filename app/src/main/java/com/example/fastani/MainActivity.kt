package com.example.fastani

import android.content.pm.ActivityInfo
import android.content.res.Resources
import android.os.Bundle
import android.view.View
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
import com.example.fastani.ui.PlayerData
import com.example.fastani.ui.PlayerFragment
import com.example.fastani.ui.PlayerFragment.Companion.isInPlayer
import com.example.fastani.ui.result.ResultFragment
import com.example.fastani.ui.result.ResultFragment.Companion.isInResults

val Int.toPx: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()
val Int.toDp: Int get() = (this / Resources.getSystem().displayMetrics.density).toInt()

data class EpisodePosDurInfo(
    val pos: Long,
    val dur: Long,
)

data class LastEpisodeInfo(
    val pos: Long,
    val dur: Long,
    val id: String,
    val aniListId: String,
    val episodeIndex: Int,
    val seasonIndex: Int,
    val episode: FastAniApi.FullEpisode,
    val coverImage: FastAniApi.CoverImage,
    val title: FastAniApi.Title,
    val bannerImage: String,
)

data class NextEpisode(
    val isFound: Boolean,
    val episodeIndex: Int,
    val seasonIndex: Int,
)

data class BookmarkedTitle(
    val id: String,
    val anilistId: String,
    val description: String,
    val title: FastAniApi.Title,
    val coverImage: FastAniApi.CoverImage,
)

class MainActivity : AppCompatActivity() {

    companion object {
        var navController: NavController? = null
        var statusHeight: Int = 0
        var activity: MainActivity? = null

        fun getViewKey(data: PlayerData): String {
            return getViewKey(data.card!!.anilistId, data.seasonIndex!!, data.episodeIndex!!)
        }

        fun getViewKey(aniListId: String, seasonIndex: Int, episodeIndex: Int): String {
            return aniListId + "S" + seasonIndex + "E" + episodeIndex
        }

        fun getViewPosDur(aniListId: String, seasonIndex: Int, episodeIndex: Int): EpisodePosDurInfo {
            val key = getViewKey(aniListId, seasonIndex, episodeIndex)

            return EpisodePosDurInfo(
                DataStore.getKey<Long>(VIEW_POS_KEY, key, -1L)!!,
                DataStore.getKey<Long>(VIEW_DUR_KEY, key, -1L)!!)
        }

        fun canPlayNextEpisode(card: FastAniApi.Card, seasonIndex: Int, episodeIndex: Int): NextEpisode {
            val canNext = card.cdnData.seasons[seasonIndex].episodes.size > (episodeIndex + 1)

            return if (!canNext) {
                if (card.cdnData.seasons.size > (seasonIndex + 1)) {
                    NextEpisode(true, 0, seasonIndex + 1)
                } else {
                    NextEpisode(false, 0, 0)
                }
            } else {
                NextEpisode(true, episodeIndex + 1, seasonIndex)
            }
        }

        fun setViewPosDur(data: PlayerData, pos: Long, dur: Long) {
            val key = getViewKey(data)
            DataStore.setKey(VIEW_POS_KEY, key, pos)
            DataStore.setKey(VIEW_DUR_KEY, key, dur)

            // HANDLES THE LOGIC FOR NEXT EPISODE
            var episodeIndex = data.episodeIndex!!
            var seasonIndex = data.seasonIndex!!
            val maxValue = 95
            var canContinue: Boolean = (pos * 100 / dur) > maxValue
            var isFound: Boolean = true
            var _pos = pos
            var _dur = dur

            val card = data.card!!
            while (canContinue) { // IF PROGRESS IS OVER 95% CONTINUE SEARCH FOR NEXT EPISODE
                val next = canPlayNextEpisode(card, seasonIndex, episodeIndex)
                if (next.isFound) {
                    val nextPro = getViewPosDur(card.anilistId, next.seasonIndex, next.episodeIndex)
                    seasonIndex = next.seasonIndex
                    episodeIndex = next.episodeIndex
                    if ((nextPro.pos * 100) / dur <= maxValue) {
                        canContinue = false
                        isFound = true
                    }
                } else {
                    canContinue = false
                    isFound = false
                }
            }

            if (!isFound) return

            DataStore.setKey(
                VIEW_LST_KEY,
                data.card.anilistId,
                LastEpisodeInfo(
                    _pos,
                    _dur,
                    card.id,
                    card.anilistId,
                    episodeIndex,
                    seasonIndex,
                    card.cdnData.seasons[seasonIndex].episodes[episodeIndex],
                    card.coverImage,
                    card.title,
                    card.bannerImage
                )
            )
        }

        fun popCurrentPage() {
            val currentFragment = activity?.supportFragmentManager!!.fragments.last()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            activity?.supportFragmentManager!!.beginTransaction().setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit).remove(currentFragment).commit()
        }

        fun hideSystemUI() {
            // Enables regular immersive mode.
            // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
            // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            MainActivity.activity!!.getWindow().decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    // Set the content to appear under the system bars so that the
                    // content doesn't resize when the system bars hide and show.
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    // Hide the nav bar and status bar
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    )
        }


        // Shows the system bars by removing all the flags
// except for the ones that make the content appear under the system bars.
        fun showSystemUI() {
            MainActivity.activity!!.getWindow().decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }

        fun loadPlayer(episodeIndex: Int, seasonIndex: Int, card: FastAniApi.Card) {
            loadPlayer(PlayerData(
                null, null,
                episodeIndex,
                seasonIndex,
                card))
        }

        fun loadPlayer(title: String, url: String) {
            loadPlayer(PlayerData(title, url, null, null, null))
        }

        private fun loadPlayer(data: PlayerData) {
            activity?.supportFragmentManager?.beginTransaction()?.setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                ?.add(R.id.videoRoot, PlayerFragment(
                    data))
                ?.commit()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        }

        fun loadPage(card: FastAniApi.Card) {

            activity?.supportFragmentManager?.beginTransaction()?.setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
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
        println("BACK PRESSED!!!!")

        if (isInResults || isInPlayer) {
            popCurrentPage()
        } else {
            super.onBackPressed()
        }
    }

    private fun getStatusBarHeight(): Int {
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