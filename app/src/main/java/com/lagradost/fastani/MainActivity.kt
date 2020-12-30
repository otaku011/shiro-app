package com.lagradost.fastani

import android.app.Activity
import android.app.AppOpsManager
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.getSystemService
import androidx.fragment.app.DialogFragment.STYLE_NO_FRAME
import androidx.fragment.app.Fragment
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
import com.google.android.gms.cast.framework.CastContext
import com.lagradost.fastani.ui.PlayerData
import com.lagradost.fastani.ui.PlayerEventType
import com.lagradost.fastani.ui.PlayerFragment
import com.lagradost.fastani.ui.PlayerFragment.Companion.isInPlayer
import com.lagradost.fastani.ui.result.ResultFragment
import com.lagradost.fastani.ui.result.ResultFragment.Companion.isInResults
import java.lang.Exception

val Int.toPx: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()
val Int.toDp: Int get() = (this / Resources.getSystem().displayMetrics.density).toInt()

data class EpisodePosDurInfo(
    val pos: Long,
    val dur: Long,
)

data class LastEpisodeInfo(
    val pos: Long,
    val dur: Long,
    val seenAt: Long,
    val id: String,
    val aniListId: String,
    val episodeIndex: Int,
    val seasonIndex: Int,
    val isMovie: Boolean,
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
        var isInPIPMode = false
        var navController: NavController? = null
        var statusHeight: Int = 0
        var activity: MainActivity? = null
        var canShowPipMode: Boolean = false

        var onPlayerEvent = Event<PlayerEventType>()
        var onAudioFocusEvent = Event<Boolean>()

        var focusRequest: AudioFocusRequest? = null

        fun getViewKey(data: PlayerData): String {
            return getViewKey(data.card!!.anilistId, data.seasonIndex!!, data.episodeIndex!!)
        }

        fun getViewKey(aniListId: String, seasonIndex: Int, episodeIndex: Int): String {
            return aniListId + "S" + seasonIndex + "E" + episodeIndex
        }

        fun requestAudioFocus() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                val audioManager = activity!!.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.requestAudioFocus(focusRequest!!)
            } else {
                val audioManager: AudioManager =
                    activity?.getSystemService(Context.AUDIO_SERVICE) as AudioManager;
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        }

        fun changeStatusBarState(hide: Boolean) {
            if (hide) {
                activity!!.window.setFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
                )
                statusHeight = 0
            } else {
                activity!!.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                statusHeight = activity!!.getStatusBarHeight()
            }
        }

        fun getViewPosDur(aniListId: String, seasonIndex: Int, episodeIndex: Int): EpisodePosDurInfo {
            val key = getViewKey(aniListId, seasonIndex, episodeIndex)

            return EpisodePosDurInfo(
                DataStore.getKey<Long>(VIEW_POS_KEY, key, -1L)!!,
                DataStore.getKey<Long>(VIEW_DUR_KEY, key, -1L)!!
            )
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
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(activity)

            if (settingsManager.getBoolean("save_history", true)) {
                DataStore.setKey(VIEW_POS_KEY, key, pos)
                DataStore.setKey(VIEW_DUR_KEY, key, dur)
            }

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
                    System.currentTimeMillis(),
                    card.id,
                    card.anilistId,
                    episodeIndex,
                    seasonIndex,
                    data.card.episodes == 1 && data.card.status == "FINISHED",
                    card.cdnData.seasons[seasonIndex].episodes[episodeIndex],
                    card.coverImage,
                    card.title,
                    card.bannerImage
                )
            )

            thread {
                FastAniApi.requestHome(true)
            }
        }

        fun popCurrentPage() {
            val currentFragment = activity?.supportFragmentManager!!.fragments.last()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            // No fucked animations leaving the player :)
            if (isInPlayer) {
                activity?.supportFragmentManager!!.beginTransaction()
                    //.setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                    .remove(currentFragment).commit()
            } else {
                activity?.supportFragmentManager!!.beginTransaction()
                    .setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                    .remove(currentFragment).commit()
            }
        }

        fun hideSystemUI() {
            // Enables regular immersive mode.
            // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
            // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            MainActivity.activity!!.window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
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
            loadPlayer(
                PlayerData(
                    null, null,
                    episodeIndex,
                    seasonIndex,
                    card,
                    null
                )
            )
        }

        fun loadPlayer(title: String?, url: String, startAt:Long?) {
            loadPlayer(PlayerData(title, url, null, null, null,startAt))
        }

        private fun loadPlayer(data: PlayerData) {
            activity?.supportFragmentManager?.beginTransaction()
                ?.setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                ?.add(
                    R.id.videoRoot, PlayerFragment(
                        data
                    )
                )
                ?.commit()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        }

        fun loadPage(card: FastAniApi.Card) {

            activity?.supportFragmentManager?.beginTransaction()
                ?.setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
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

        @ColorInt
        fun Context.getColorFromAttr(
            @AttrRes attrColor: Int,
            typedValue: TypedValue = TypedValue(),
            resolveRefs: Boolean = true,
        ): Int {
            theme.resolveAttribute(attrColor, typedValue, resolveRefs)
            return typedValue.data
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

    private fun enterPIPMode() {
        if (!shouldShowPIPMode() || !canShowPipMode) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                enterPictureInPictureMode(PictureInPictureParams.Builder().build())
            } catch (e: Exception) {
                enterPictureInPictureMode()
            }
        } else {
            enterPictureInPictureMode()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPIPMode()
    }

    override fun onRestart() {
        super.onRestart()
        if (isInPlayer) {
            hideSystemUI()
        }
    }

    override fun onResume() {
        super.onResume()
        if (isInPlayer) {
            hideSystemUI()
        }
    }

    private fun shouldShowPIPMode(): Boolean {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(activity)
        return settingsManager.getBoolean("pip_enabled", true) && isInPlayer
    }

    private fun hasPIPPermission(): Boolean {
        val appOps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        } else {
            return false
        }
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
            android.os.Process.myUid(),
            packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    private val callbacks = object : MediaSessionCompat.Callback() {
        override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
            if (mediaButtonEvent != null) {

                val event = mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) as KeyEvent
                println("EVENT: " + event.keyCode)
                when (event.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> onPlayerEvent.invoke(PlayerEventType.Pause)
                    KeyEvent.KEYCODE_MEDIA_PLAY -> onPlayerEvent.invoke(PlayerEventType.Play)
                    KeyEvent.KEYCODE_MEDIA_STOP -> onPlayerEvent.invoke(PlayerEventType.Pause)
                    KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> onPlayerEvent.invoke(PlayerEventType.SeekForward)
                    KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> onPlayerEvent.invoke(PlayerEventType.SeekBack)
                    KeyEvent.KEYCODE_HEADSETHOOK -> onPlayerEvent.invoke(PlayerEventType.Pause)
                }
            }
            return super.onMediaButtonEvent(mediaButtonEvent)
        }

        override fun onPlay() {
            onPlayerEvent.invoke(PlayerEventType.Play)
        }

        override fun onStop() {
            onPlayerEvent.invoke(PlayerEventType.Pause)
        }
    }

    private val myAudioFocusListener =
        AudioManager.OnAudioFocusChangeListener {
            onAudioFocusEvent.invoke(
                when (it) {
                    AudioManager.AUDIOFOCUS_GAIN -> true
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE -> true
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> true
                    else -> false
                }
            )
        }

    override fun onDestroy() {
        mediaSession?.isActive = false
        super.onDestroy()
    }

    private var mediaSession: MediaSessionCompat? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        activity = this
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        //https://stackoverflow.com/questions/29146757/set-windowtranslucentstatus-true-when-android-lollipop-or-higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
            )
        }
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(activity)
        if (settingsManager.getBoolean("cool_mode", false)) {
            theme.applyStyle(R.style.OverlayPrimaryColorBlue, true)
        }
        changeStatusBarState(settingsManager.getBoolean("statusbar_hidden", true))
        //window.statusBarColor = R.color.transparent

        //https://stackoverflow.com/questions/52594181/how-to-know-if-user-has-disabled-picture-in-picture-feature-permission
        //https://developer.android.com/guide/topics/ui/picture-in-picture
        canShowPipMode =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && // OS SUPPORT
                    packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) && // HAS FEATURE, MIGHT BE BLOCKED DUE TO POWER DRAIN
                    hasPIPPermission() // CHECK IF FEATURE IS ENABLED IN SETTINGS


        // CRASHES ON 7.0.0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).run {
                setAudioAttributes(AudioAttributes.Builder().run {
                    setUsage(AudioAttributes.USAGE_MEDIA)
                    setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    build()
                })
                setAcceptsDelayedFocusGain(true)
                setOnAudioFocusChangeListener(myAudioFocusListener)
                build()
            }
        }

        // Setting the theme
        /*
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
        }*/
        mediaSession = MediaSessionCompat(activity, "fastani").apply {

            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            // Do not let MediaButtons restart the player when the app is not visible
            setMediaButtonReceiver(null)

            // Set an initial PlaybackState with ACTION_PLAY, so media buttons can start the player
            val stateBuilder = PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PLAY_PAUSE)
            setPlaybackState(stateBuilder.build())

            // MySessionCallback has methods that handle callbacks from a media controller
            setCallback(callbacks)
        }

        mediaSession!!.isActive = true

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

        window.setBackgroundDrawableResource(R.color.background);
        val castContext = CastContext.getSharedInstance(activity!!.applicationContext)
    }
}