package com.lagradost.shiro.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.animation.AnimationUtils
import com.lagradost.shiro.*
import com.lagradost.shiro.MainActivity.Companion.getViewPosDur
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.upstream.*
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.Util
import kotlinx.android.synthetic.main.player.*
import kotlinx.android.synthetic.main.player_custom_layout.*
import android.view.animation.AlphaAnimation
import com.lagradost.shiro.MainActivity.Companion.getColorFromAttr
import android.app.RemoteAction
import android.graphics.drawable.Icon
import android.content.Intent
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context.AUDIO_SERVICE
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import com.google.android.exoplayer2.Player.DefaultEventListener
import android.content.res.Resources
import android.database.ContentObserver
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.preference.PreferenceManager
import androidx.transition.Fade
import androidx.transition.Transition
import android.view.*
import android.view.View.*
import android.view.animation.AccelerateInterpolator
import android.widget.ProgressBar
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import androidx.appcompat.app.AlertDialog
import androidx.transition.TransitionManager
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.lagradost.shiro.ShiroApi.Companion.USER_AGENT
import com.lagradost.shiro.ShiroApi.Companion.getVideoLink
import com.lagradost.shiro.MainActivity.Companion.activity
import com.lagradost.shiro.MainActivity.Companion.hideKeyboard
import com.lagradost.shiro.MainActivity.Companion.hideSystemUI
import com.lagradost.shiro.R
import java.io.File
import kotlin.collections.ArrayList
import kotlin.concurrent.thread
import kotlin.math.*


const val STATE_RESUME_WINDOW = "resumeWindow"
const val STATE_RESUME_POSITION = "resumePosition"
const val STATE_PLAYER_FULLSCREEN = "playerFullscreen"
const val STATE_PLAYER_PLAYING = "playerOnPlay"
const val ACTION_MEDIA_CONTROL = "media_control"
const val EXTRA_CONTROL_TYPE = "control_type"
const val PLAYBACK_SPEED = "playback_speed"

// TITLE AND URL OR CARD MUST BE PROVIDED
// EPISODE AND SEASON SHOULD START AT 0
data class PlayerData(
    @JsonProperty("title") val title: String?,
    @JsonProperty("url") val url: String?,

    @JsonProperty("episodeIndex") var episodeIndex: Int?,
    @JsonProperty("seasonIndex") var seasonIndex: Int?,
    @JsonProperty("card") val card: ShiroApi.AnimePageData?,
    @JsonProperty("startAt") val startAt: Long?,
    @JsonProperty("slug") val slug: String,
)

enum class PlayerEventType(val value: Int) {
    Stop(-1),
    Pause(0),
    Play(1),
    SeekForward(2),
    SeekBack(3),
    SkipCurrentChapter(4),
    NextEpisode(5),
    PlayPauseToggle(6)
}

class PlayerFragment() : Fragment() {
    var data: PlayerData? = null
    private val mapper = JsonMapper.builder().addModule(KotlinModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()

    private class SettingsContentObserver(handler: Handler?) : ContentObserver(handler) {
        private val audioManager = activity?.getSystemService(AUDIO_SERVICE) as AudioManager
        override fun onChange(selfChange: Boolean) {
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val progressBarRight = activity?.findViewById<ProgressBar>(R.id.progressBarRight)
            progressBarRight?.progress = currentVolume * 100 / maxVolume
        }
    }

    private val volumeObserver = SettingsContentObserver(
        Handler(
            Looper.getMainLooper()
        )
    )

    companion object {
        var isInPlayer: Boolean = false
        var onLeftPlayer = Event<Boolean>()
        fun newInstance(data: PlayerData) =
            PlayerFragment().apply {
                arguments = Bundle().apply {
                    //println(data)
                    putString("data", mapper.writeValueAsString(data))
                }
            }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onAttach(context: Context) {
        super.onAttach(context)
        arguments?.getString("data")?.let {
            data = mapper.readValue(it, PlayerData::class.java)
        }

    }

    private var isLocked = false
    private var isShowing = true
    private lateinit var exoPlayer: SimpleExoPlayer

    // private val url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
    private var currentWindow = 0
    private var playbackPosition: Long = 0
    private var isFullscreen = false
    private var isPlayerPlaying = true
    private var currentX = 0F
    private var currentY = 0F
    private var isMovingStartTime = 0L
    private var skipTime = 0L
    private var hasPassedSkipLimit = false
    private var preventHorizontalSwipe = false
    private var hasPassedVerticalSwipeThreshold = false

    private var playbackSpeed = DataStore.getKey<Float>(PLAYBACK_SPEED_KEY, 1f)
    private val settingsManager = PreferenceManager.getDefaultSharedPreferences(MainActivity.activity)
    private val swipeEnabled = settingsManager.getBoolean("swipe_enabled", true)
    private val swipeVerticalEnabled = settingsManager.getBoolean("swipe_vertical_enabled", true)
    private val skipOpEnabled = settingsManager.getBoolean("skip_op_enabled", false)
    private val doubleTapEnabled = settingsManager.getBoolean("double_tap_enabled", false)
    private val playBackSpeedEnabled = settingsManager.getBoolean("playback_speed_enabled", false)
    private val playerResizeEnabled = settingsManager.getBoolean("player_resize_enabled", false)
    private val doubleTapTime = settingsManager.getInt("dobule_tap_time", 10)
    private val fastForwardTime = settingsManager.getInt("fast_forward_button_time", 10)
    private val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_FILL,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    )
    private var resizeMode = DataStore.getKey<Int>(RESIZE_MODE_KEY, 0)

    // width as it's rotated
    private var width = Resources.getSystem().displayMetrics.heightPixels
    private var height = Resources.getSystem().displayMetrics.widthPixels
    private var prevDiffX = 0.0

    abstract class DoubleClickListener(ctx: PlayerFragment) : OnTouchListener {
        // The time in which the second tap should be done in order to qualify as
        // a double click

        private var doubleClickQualificationSpanInMillis: Long = 300L
        private var timestampLastClick: Long = 0
        private var clicksLeft = 0
        private var clicksRight = 0
        private var fingerLeftScreen = true
        private val ctx = ctx
        abstract fun onDoubleClickRight(clicks: Int)
        abstract fun onDoubleClickLeft(clicks: Int)
        abstract fun onSingleClick()
        abstract fun onMotionEvent(event: MotionEvent)

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            thread {
                activity?.runOnUiThread {
                    onMotionEvent(event)
                }

                if (event.action == MotionEvent.ACTION_UP) {
                    fingerLeftScreen = true
                }
                if (event.action == MotionEvent.ACTION_DOWN) {
                    fingerLeftScreen = false

                    if (ctx.doubleTapEnabled && !ctx.isLocked) {
                        timestampLastClick = SystemClock.elapsedRealtime()
                        Thread.sleep(doubleClickQualificationSpanInMillis)
                        if ((SystemClock.elapsedRealtime() - timestampLastClick) < doubleClickQualificationSpanInMillis) {
                            if (event.rawX >= ctx.width / 2) {
                                //println("${event.rawX} ${ctx.width}")
                                clicksRight++
                                activity?.runOnUiThread {
                                    onDoubleClickRight(clicksRight)
                                }
                            } else {
                                clicksLeft++
                                activity?.runOnUiThread {
                                    onDoubleClickLeft(clicksLeft)
                                }
                            }
                        } else if (clicksLeft == 0 && clicksRight == 0 && fingerLeftScreen) {
                            activity?.runOnUiThread {
                                onSingleClick()
                            }
                        } else {
                            clicksLeft = 0
                            clicksRight = 0
                        }
                    } else {
                        Thread.sleep(100L)
                        if (fingerLeftScreen) {
                            activity?.runOnUiThread {
                                onSingleClick()
                            }
                        }
                    }
                }
            }
            return true
        }


    }

    private fun canPlayNextEpisode(): Boolean {
        if (data?.card == null || data?.seasonIndex == null || data?.episodeIndex == null) {
            return false
        }
        return try {
            data!!.card!!.episodes!!.size > data!!.episodeIndex!! + 1
            //MainActivity.canPlayNextEpisode(data?.card!!, data?.seasonIndex!!, data?.episodeIndex!!).isFound
        } catch (e: NullPointerException) {
            false
        }
    }

    private fun getCurrentEpisode(): ShiroApi.ShiroEpisodes? {
        return data?.card?.episodes?.get(data?.episodeIndex!!)//data?.card!!.cdnData.seasons.getOrNull(data?.seasonIndex!!)?.episodes?.get(data?.episodeIndex!!)
    }

    private fun getCurrentTitle(): String {
        if (data?.title != null) return data?.title!!

        val isMovie: Boolean = data?.card!!.episodes!!.size == 1 && data?.card?.status == "finished"
        // data?.card!!.cdndata?.seasons.size == 1 && data?.card!!.cdndata?.seasons[0].episodes.size == 1
        var preTitle = ""
        if (!isMovie) {
            preTitle = "Episode ${data?.episodeIndex!! + 1} · "
        }
        // Replaces with "" if it's null
        return preTitle + data?.card?.name
    }

    private fun getCurrentUrl(): String? {
        println("MAN::: " + data?.url)
        if (data?.url != null) return data?.url!!
        return getCurrentEpisode()?.videos?.getOrNull(0)?.video_id?.let { getVideoLink(it) }
    }

    fun savePos() {
        if (this::exoPlayer.isInitialized) {
            if (((data?.slug != null
                        && data?.seasonIndex != null
                        && data?.episodeIndex != null) || data?.card != null)
                && exoPlayer.duration > 0 && exoPlayer.currentPosition > 0
            ) {
                MainActivity.setViewPosDur(data!!, exoPlayer.currentPosition, exoPlayer.duration)
            }
        }
    }

    override fun onDestroy() {
        savePos()
        // DON'T SAVE DATA OF TRAILERS

        isInPlayer = false
        onLeftPlayer.invoke(true)
        MainActivity.showSystemUI()
        MainActivity.onPlayerEvent -= ::handlePlayerEvent
        MainActivity.onAudioFocusEvent -= ::handleAudioFocusEvent
        requireActivity().contentResolver.unregisterContentObserver(volumeObserver);
        super.onDestroy()
        //MainActivity.showSystemUI()
    }

    private fun updateLock() {
        video_locked_img.setImageResource(if (isLocked) R.drawable.video_locked else R.drawable.video_unlocked)
        video_locked_img.setColorFilter(
            if (isLocked) requireContext().getColorFromAttr(R.attr.colorPrimary)
            else Color.WHITE
        )

        val isClick = !isLocked
        exo_play.isClickable = isClick
        exo_pause.isClickable = isClick
        exo_ffwd.isClickable = isClick
        exo_prev.isClickable = isClick
        video_go_back.isClickable = isClick
        exo_progress.isClickable = isClick
        next_episode_btt.isClickable = isClick
        playback_speed_btt.isClickable = isClick
        skip_op.isClickable = isClick

        val fadeTo = if (!isLocked) 1f else 0f
        val fadeAnimation = AlphaAnimation(1f - fadeTo, fadeTo)

        fadeAnimation.duration = 100
        fadeAnimation.fillAfter = true

        shadow_overlay.startAnimation(fadeAnimation)

    }

    private var receiver: BroadcastReceiver? = null
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        MainActivity.isInPIPMode = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            // Hide the full-screen UI (controls, etc.) while in picture-in-picture mode.
            player_holder.alpha = 0f
            receiver = object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    if (ACTION_MEDIA_CONTROL != intent.action) {
                        return
                    }
                    handlePlayerEvent(intent.getIntExtra(EXTRA_CONTROL_TYPE, 0))
                }
            }
            val filter = IntentFilter()
            filter.addAction(
                ACTION_MEDIA_CONTROL
            )
            activity!!.registerReceiver(receiver, filter)
            updatePIPModeActions()
        } else {
            // Restore the full-screen UI.
            player_holder.alpha = 1f
            receiver?.let {
                activity!!.unregisterReceiver(it)
            }
            hideSystemUI()
            hideKeyboard()
        }
    }

    private fun getPen(code: PlayerEventType): PendingIntent {
        return getPen(code.value)
    }

    private fun getPen(code: Int): PendingIntent {
        return PendingIntent.getBroadcast(
            activity,
            code,
            Intent("media_control").putExtra("control_type", code),
            0
        )
    }

    private fun getRemoteAction(id: Int, title: String, event: PlayerEventType): RemoteAction {
        return RemoteAction(
            Icon.createWithResource(activity, id),
            title,
            title,
            getPen(event)
        )
    }

    private fun updatePIPModeActions() {
        if (!MainActivity.isInPIPMode || !this::exoPlayer.isInitialized) return

        val actions: ArrayList<RemoteAction> = ArrayList()

        actions.add(getRemoteAction(R.drawable.go_back_30, "Go Back", PlayerEventType.SeekBack))

        if (exoPlayer.isPlaying) {
            actions.add(getRemoteAction(R.drawable.netflix_pause, "Pause", PlayerEventType.Pause))
        } else {
            actions.add(getRemoteAction(R.drawable.netflix_play, "Play", PlayerEventType.Play))
        }

        actions.add(getRemoteAction(R.drawable.go_forward_30, "Go Forward", PlayerEventType.SeekForward))
        activity!!.setPictureInPictureParams(PictureInPictureParams.Builder().setActions(actions).build())
    }

    private fun onClickChange() {
        isShowing = !isShowing

        click_overlay.visibility = if (isShowing) GONE else VISIBLE

        val fadeTo = if (isShowing) 1f else 0f
        val fadeAnimation = AlphaAnimation(1f - fadeTo, fadeTo)

        fadeAnimation.duration = 100
        fadeAnimation.fillAfter = true

        if (!isLocked) {
            video_holder.startAnimation(fadeAnimation)
            shadow_overlay.startAnimation(fadeAnimation)
        }
        video_lock_holder.startAnimation(fadeAnimation)
    }

    private fun handleAudioFocusEvent(event: Boolean) {
        if (!event) exoPlayer.pause()
    }

    private fun handlePlayerEvent(event: PlayerEventType) {
        handlePlayerEvent(event.value)
    }

    private fun handlePlayerEvent(event: Int) {
        when (event) {
            PlayerEventType.Play.value -> exoPlayer.play()
            PlayerEventType.Pause.value -> exoPlayer.pause()
            PlayerEventType.SeekBack.value -> seekTime(-30000L)
            PlayerEventType.SeekForward.value -> seekTime(30000L)
        }
    }

    private fun forceLetters(inp: Int, letters: Int = 2): String {
        val added: Int = letters - inp.toString().length
        return if (added > 0) {
            "0".repeat(added) + inp.toString()
        } else {
            inp.toString()
        }
    }

    private fun convertTimeToString(time: Double): String {
        val sec = time.toInt()
        val rsec = sec % 60
        val min = ceil((sec - rsec) / 60.0).toInt()
        val rmin = min % 60
        val h = ceil((min - rmin) / 60.0).toInt()
        //int rh = h;// h % 24;
        return (if (h > 0) forceLetters(h) + ":" else "") + (if (rmin >= 0 || h >= 0) forceLetters(rmin) + ":" else "") + forceLetters(
            rsec
        )
    }

    fun handleMotionEvent(motionEvent: MotionEvent) {
        // TIME_UNSET   ==   -9223372036854775807L
        // No swiping on unloaded
        // https://exoplayer.dev/doc/reference/constant-values.html
        if (isLocked || exoPlayer.duration == -9223372036854775807L || (!swipeEnabled && !swipeVerticalEnabled)) return
        val audioManager = requireActivity().getSystemService(AUDIO_SERVICE) as AudioManager

        when (motionEvent.action) {
            MotionEvent.ACTION_DOWN -> {
                currentX = motionEvent.rawX
                currentY = motionEvent.rawY
                //println("DOWN: " + currentX)
                isMovingStartTime = exoPlayer.currentPosition
            }
            MotionEvent.ACTION_MOVE -> {
                if (swipeVerticalEnabled) {
                    val distanceMultiplierY = 2F
                    val distanceY = (motionEvent.rawY - currentY) * distanceMultiplierY
                    val diffY = distanceY * 2.0 / height

                    // Forces 'smooth' moving preventing a bug where you
                    // can make it think it moved half a screen in a frame

                    if (abs(diffY) >= 0.2 && !hasPassedSkipLimit) {
                        hasPassedVerticalSwipeThreshold = true
                        preventHorizontalSwipe = true

                    }
                    if (hasPassedVerticalSwipeThreshold && abs(diffY) >= 0.1) {
                        if (currentX > width * 0.5) {
                            progressBarLeftHolder.alpha = 1f
                            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val newVolume =
                                minOf(maxVolume, currentVolume + (maxVolume / 20) * if (diffY > 0) -1 else 1)
                            val newVolumeAdjusted =
                                if (diffY > 0) AudioManager.ADJUST_LOWER else AudioManager.ADJUST_RAISE
                            //println(newVolume.toFloat() / maxVolume)
                            if (audioManager.isVolumeFixed) {
                                // Lmao might earrape, we'll see in bug reports
                                exoPlayer.volume = minOf(1f, maxOf(newVolume.toFloat() / maxVolume, 0f))
                            } else {
                                //audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, newVolumeAdjusted, 0)
                            }
                            progressBarLeft.progress = newVolume * 100 / maxVolume
                            currentY = motionEvent.rawY
                        } else {
                            progressBarRightHolder.alpha = 1f
                            val alpha = minOf(0.95f, brightness_overlay.alpha + 0.05f * if (diffY > 0) 1 else -1)
                            brightness_overlay.alpha = alpha
                            progressBarRight.progress = ((1f - alpha) * 100).toInt()

                            currentY = motionEvent.rawY
                        }
                    }
                }

                if (swipeEnabled) {
                    val distanceMultiplierX = 2F
                    val distanceX = (motionEvent.rawX - currentX) * distanceMultiplierX
                    val diffX = distanceX * 2.0 / width
                    if (abs(diffX - prevDiffX) > 0.5) {
                        return
                    }
                    prevDiffX = diffX

                    skipTime = ((exoPlayer.duration * (diffX * diffX) / 10) * (if (diffX < 0) -1 else 1)).toLong()
                    if (isMovingStartTime + skipTime < 0) {
                        skipTime = -isMovingStartTime
                    } else if (isMovingStartTime + skipTime > exoPlayer.duration) {
                        skipTime = exoPlayer.duration - isMovingStartTime
                    }
                    if ((abs(skipTime) > 3000 || hasPassedSkipLimit) && !preventHorizontalSwipe) {
                        hasPassedSkipLimit = true
                        val timeString =
                            "${convertTimeToString((isMovingStartTime + skipTime) / 1000.0)} [${(if (abs(skipTime) < 1000) "" else (if (skipTime > 0) "+" else "-"))}${
                                convertTimeToString(abs(skipTime / 1000.0))
                            }]"
                        timeText.alpha = 1f
                        timeText.text = timeString
                    } else {
                        timeText.alpha = 0f
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                val transition: Transition = Fade()
                transition.duration = 1000

                TransitionManager.beginDelayedTransition(player_holder, transition)

                if (abs(skipTime) > 7000 && !preventHorizontalSwipe && swipeEnabled) {
                    exoPlayer.seekTo(maxOf(minOf(skipTime + isMovingStartTime, exoPlayer.duration), 0))
                }
                hasPassedSkipLimit = false
                hasPassedVerticalSwipeThreshold = false
                preventHorizontalSwipe = false
                prevDiffX = 0.0
                skipTime = 0

                timeText.animate().alpha(0f).setDuration(200)
                    .setInterpolator(AccelerateInterpolator()).start()
                progressBarRightHolder.animate().alpha(0f).setDuration(200)
                    .setInterpolator(AccelerateInterpolator()).start()
                progressBarLeftHolder.animate().alpha(0f).setDuration(200)
                    .setInterpolator(AccelerateInterpolator()).start()
                //val fadeAnimation = AlphaAnimation(1f, 0f)
                //fadeAnimation.duration = 100
                //fadeAnimation.fillAfter = true
                //progressBarLeftHolder.startAnimation(fadeAnimation)
                //progressBarRightHolder.startAnimation(fadeAnimation)
                //timeText.startAnimation(fadeAnimation)

            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        requireActivity().contentResolver
            .registerContentObserver(
                android.provider.Settings.System.CONTENT_URI, true, volumeObserver
            )

        MainActivity.onPlayerEvent += ::handlePlayerEvent
        MainActivity.onAudioFocusEvent += ::handleAudioFocusEvent

        hideKeyboard()

        updateLock()
        video_lock.setOnClickListener {
            isLocked = !isLocked
            val fadeTo = if (isLocked) 0f else 1f

            val fadeAnimation = AlphaAnimation(1f - fadeTo, fadeTo)
            fadeAnimation.duration = 100
            //   fadeAnimation.startOffset = 100
            fadeAnimation.fillAfter = true
            video_holder.startAnimation(fadeAnimation)

            updateLock()
        }
        /*
        player_holder.setOnTouchListener(OnTouchListener { v, event -> // ignore all touch events
            !isShowing
        })*/
        //println("RESIZE $resizeMode")
        player_view.resizeMode = resizeModes[resizeMode!!]
        if (playerResizeEnabled) {
            resize_player_holder.visibility = VISIBLE
            resize_player.setOnClickListener {
                resizeMode = Math.floorMod(resizeMode!! + 1, resizeModes.size)
                //println("RESIZE $resizeMode")
                DataStore.setKey(RESIZE_MODE_KEY, resizeMode)
                player_view.resizeMode = resizeModes[resizeMode!!]
                //exoPlayer.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            }
        } else {
            resize_player_holder.visibility = GONE
        }

        class Listener : DoubleClickListener(this) {
            // Declaring a seekAnimation here will cause a bug

            override fun onDoubleClickRight(clicks: Int) {
                if (!isLocked) {
                    val seekAnimation = AlphaAnimation(1f, 0f)
                    seekAnimation.duration = 1200
                    seekAnimation.fillAfter = true
                    seekTime(doubleTapTime * 1000L)
                    timeTextRight.text = "+ ${convertTimeToString((clicks * doubleTapTime).toDouble())}"
                    timeTextRight.alpha = 1f
                    timeTextRight.startAnimation(seekAnimation)
                }
            }

            override fun onDoubleClickLeft(clicks: Int) {
                if (!isLocked) {
                    val seekAnimation = AlphaAnimation(1f, 0f)
                    seekAnimation.duration = 1200
                    seekAnimation.fillAfter = true
                    seekTime(doubleTapTime * -1000L)
                    timeTextLeft.text = "- ${convertTimeToString((clicks * doubleTapTime).toDouble())}"
                    timeTextLeft.alpha = 1f
                    timeTextLeft.startAnimation(seekAnimation)
                }
            }

            override fun onSingleClick() {
                onClickChange()
                hideSystemUI()
            }

            override fun onMotionEvent(event: MotionEvent) {
                handleMotionEvent(event)
            }

        }

        click_overlay.setOnTouchListener(
            Listener()
        )

        player_holder.setOnTouchListener(
            Listener()
        )

        player_holder.setOnClickListener {
            onClickChange()
            /*if(!isShowing) {
                video_holder.postDelayed({
                    video_holder.setVisibility(View.INVISIBLE);
                    video_lock_holder.setVisibility(View.INVISIBLE);
                }, 100);
            }*/

            //isClickable WILL CAUSE UI BUG
            /*  exo_play.isClickable = isShowing

              exo_pause.isClickable = isShowing
              //exo_pause.isFocusable = isShowing
              exo_ffwd.isClickable = isShowing
              //exo_ffwd.isFocusable = isShowing
              exo_prev.isClickable = isShowing
              //exo_prev.isFocusable = isShowing
              video_lock.isClickable = isShowing
              //video_lock.isFocusable = isShowing
              video_go_back.isClickable = isShowing
              //video_go_back.isFocusable = isShowing
              exo_progress.isClickable = isShowing*/
            //  exo_progress.isFocusable = isShowing
        }

        isInPlayer = true
        retainInstance = true // OTHERWISE IT WILL CAUSE A CRASH

        video_go_back.setOnClickListener {
            MainActivity.popCurrentPage()
        }
        video_go_back_holder.setOnClickListener {
            println("video_go_back_pressed")
            MainActivity.popCurrentPage()
        }
        exo_rew_text.text = fastForwardTime.toString()
        exo_ffwd_text.text = fastForwardTime.toString()
        exo_rew.setOnClickListener {
            val rotateLeft = AnimationUtils.loadAnimation(context, R.anim.rotate_left)
            exo_rew.startAnimation(rotateLeft)
            seekTime(fastForwardTime * -1000L)
        }
        exo_ffwd.setOnClickListener {
            val rotateRight = AnimationUtils.loadAnimation(context, R.anim.rotate_right)
            exo_ffwd.startAnimation(rotateRight)
            seekTime(fastForwardTime * 1000L)
        }

        playback_speed_holder.visibility = if (playBackSpeedEnabled) VISIBLE else GONE
        playback_speed_btt.setOnClickListener {
            lateinit var dialog: AlertDialog
            val speedsText = arrayOf("0.5x", "0.75x", "1x", "1.25x", "1.5x", "1.75x", "2x")
            val speedsNumbers = arrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
            val builder = AlertDialog.Builder(requireContext())
            builder.setTitle("Pick playback speed")

            builder.setSingleChoiceItems(speedsText, speedsNumbers.indexOf(playbackSpeed)) { _, which ->

                //val speed = speedsText[which]
                //Toast.makeText(requireContext(), "$speed selected.", Toast.LENGTH_SHORT).show()

                playbackSpeed = speedsNumbers[which]
                DataStore.setKey(PLAYBACK_SPEED_KEY, playbackSpeed)
                val param = PlaybackParameters(playbackSpeed!!)
                exoPlayer.setPlaybackParameters(param)

                dialog.dismiss()
            }
            dialog = builder.create()
            dialog.show()

        }

        if (skipOpEnabled) {
            skip_op_holder.visibility = VISIBLE
            skip_op.setOnClickListener {
                seekTime(85000L)
            }
        }

        if (savedInstanceState != null) {
            currentWindow = savedInstanceState.getInt(STATE_RESUME_WINDOW)
            playbackPosition = savedInstanceState.getLong(STATE_RESUME_POSITION)
            isFullscreen = savedInstanceState.getBoolean(STATE_PLAYER_FULLSCREEN)
            isPlayerPlaying = savedInstanceState.getBoolean(STATE_PLAYER_PLAYING)
            resizeMode = savedInstanceState.getInt(RESIZE_MODE_KEY)
            playbackSpeed = savedInstanceState.getFloat(PLAYBACK_SPEED)
        }
    }

    private fun seekTime(time: Long) {
        exoPlayer.seekTo(maxOf(minOf(exoPlayer.currentPosition + time, exoPlayer.duration), 0))
    }

    private fun releasePlayer() {
        val alphaAnimation = AlphaAnimation(0f, 1f)
        alphaAnimation.duration = 100
        alphaAnimation.fillAfter = true
        loading_overlay.startAnimation(alphaAnimation)
        video_go_back_holder.visibility = VISIBLE
        if (this::exoPlayer.isInitialized) {
            isPlayerPlaying = exoPlayer.playWhenReady
            playbackPosition = exoPlayer.currentPosition
            currentWindow = exoPlayer.currentWindowIndex
            exoPlayer.release()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (this::exoPlayer.isInitialized) {
            outState.putInt(STATE_RESUME_WINDOW, exoPlayer.currentWindowIndex)
            outState.putLong(STATE_RESUME_POSITION, exoPlayer.currentPosition)
        }
        outState.putBoolean(STATE_PLAYER_FULLSCREEN, isFullscreen)
        outState.putBoolean(STATE_PLAYER_PLAYING, isPlayerPlaying)
        outState.putInt(RESIZE_MODE_KEY, resizeMode!!)
        outState.putFloat(PLAYBACK_SPEED, playbackSpeed!!)
        savePos()
        super.onSaveInstanceState(outState)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initPlayer() {
        view?.setOnTouchListener { _, _ -> return@setOnTouchListener true } // VERY IMPORTANT https://stackoverflow.com/questions/28818926/prevent-clicking-on-a-button-in-an-activity-while-showing-a-fragment
        thread {
            var currentUrl = getCurrentUrl()
            if (currentUrl == null) {
                requireActivity().runOnUiThread {
                    Toast.makeText(activity, "Error getting link", Toast.LENGTH_LONG).show()
                    //MainActivity.popCurrentPage()
                }
                currentUrl = ""
            }
            try {
                requireActivity().runOnUiThread {
                    val isOnline =
                        currentUrl?.startsWith("https://") == true || currentUrl?.startsWith("http://") == true

                    class CustomFactory : DataSource.Factory {
                        override fun createDataSource(): DataSource {
                            return if (isOnline) {
                                val dataSource = DefaultHttpDataSourceFactory(USER_AGENT).createDataSource()
                                /*FastAniApi.currentHeaders?.forEach {
                                    dataSource.setRequestProperty(it.key, it.value)
                                }*/
                                dataSource.setRequestProperty("Referer", "https://cherry.subsplea.se/")
                                dataSource
                            } else {
                                DefaultDataSourceFactory(requireContext(), USER_AGENT).createDataSource()
                            }
                        }
                    }
                    if (data?.card != null || (data?.slug != null && data?.episodeIndex != null && data?.seasonIndex != null)) {
                        val pro = getViewPosDur(
                            if (data?.card != null) data?.card!!.slug else data?.slug!!,
                            data?.episodeIndex!!
                        )
                        playbackPosition =
                            if (pro.pos > 0 && pro.dur > 0 && (pro.pos * 100 / pro.dur) < 95) { // UNDER 95% RESUME
                                pro.pos
                            } else {
                                0L
                            }
                    } else if (data?.startAt != null) {
                        playbackPosition = data?.startAt!!
                    }
                    video_title?.text = getCurrentTitle()
                    if (canPlayNextEpisode()) {
                        next_episode_btt.visibility = VISIBLE
                        next_episode_btt.setOnClickListener {
                            savePos()
                            val next =
                                data!!.card!!.episodes!!.size > data!!.episodeIndex!! + 1
                            val key = MainActivity.getViewKey(
                                data?.card!!.slug,
                                data!!.episodeIndex!! + 1
                            )
                            DataStore.removeKey(VIEW_POS_KEY, key)
                            DataStore.removeKey(VIEW_DUR_KEY, key)

                            data?.seasonIndex = 0
                            data?.episodeIndex = data!!.episodeIndex!! + 1
                            releasePlayer()
                            initPlayer()
                        }
                    }
                    // this to make the button visible in the editor
                    else {
                        next_episode_btt.visibility = GONE
                    }

                    if (isOnline) {
                        currentUrl = currentUrl?.replace(" ", "%20")
                    }
                    val _mediaItem = MediaItem.Builder()
                        //Replace needed for android 6.0.0  https://github.com/google/ExoPlayer/issues/5983
                        .setMimeType(MimeTypes.APPLICATION_MP4)

                    if (isOnline) {
                        _mediaItem.setUri(currentUrl)
                    } else {
                        _mediaItem.setUri(Uri.fromFile(File(currentUrl)))
                    }

                    val mediaItem = _mediaItem.build()
                    val trackSelector = DefaultTrackSelector(requireContext())
                    // Disable subtitles
                    trackSelector.parameters = DefaultTrackSelector.ParametersBuilder(requireContext())
                        .setRendererDisabled(C.TRACK_TYPE_VIDEO, true)
                        .setRendererDisabled(C.TRACK_TYPE_TEXT, true)
                        .setDisabledTextTrackSelectionFlags(C.TRACK_TYPE_TEXT)
                        .clearSelectionOverrides()
                        .build()

                    val _exoPlayer =
                        SimpleExoPlayer.Builder(this.requireContext())
                            .setTrackSelector(trackSelector)

                    _exoPlayer.setMediaSourceFactory(DefaultMediaSourceFactory(CustomFactory()))

                    exoPlayer = _exoPlayer.build().apply {
                        playWhenReady = isPlayerPlaying
                        seekTo(currentWindow, playbackPosition)
                        setMediaItem(mediaItem, false)
                        prepare()
                    }

                    val alphaAnimation = AlphaAnimation(1f, 0f)
                    alphaAnimation.duration = 300
                    alphaAnimation.fillAfter = true
                    loading_overlay.startAnimation(alphaAnimation)
                    video_go_back_holder.visibility = GONE

                    exoPlayer.setHandleAudioBecomingNoisy(true) // WHEN HEADPHONES ARE PLUGGED OUT https://github.com/google/ExoPlayer/issues/7288
                    player_view.player = exoPlayer
                    // Sets the speed
                    exoPlayer.setPlaybackParameters(PlaybackParameters(playbackSpeed!!))

                    //https://stackoverflow.com/questions/47731779/detect-pause-resume-in-exoplayer
                    exoPlayer.addListener(object : DefaultEventListener() {
                        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                            updatePIPModeActions()
                            if (playWhenReady && playbackState == Player.STATE_READY) {
                                MainActivity.requestAudioFocus()
                            }
                        }

                        override fun onPlayerError(error: ExoPlaybackException) {
                            // Lets pray this doesn't spam Toasts :)
                            when (error.type) {
                                ExoPlaybackException.TYPE_SOURCE -> {
                                    if (currentUrl != "") {
                                        Toast.makeText(
                                            activity,
                                            "Source error\n" + error.sourceException.message,
                                            LENGTH_LONG
                                        )
                                            .show()
                                    }
                                }
                                ExoPlaybackException.TYPE_REMOTE -> {
                                    Toast.makeText(activity, "Remote error", LENGTH_LONG)
                                        .show()
                                }
                                ExoPlaybackException.TYPE_RENDERER -> {
                                    Toast.makeText(
                                        activity,
                                        "Renderer error\n" + error.rendererException.message,
                                        LENGTH_LONG
                                    )
                                        .show()
                                }
                                ExoPlaybackException.TYPE_UNEXPECTED -> {
                                    Toast.makeText(
                                        activity,
                                        "Unexpected player error\n" + error.unexpectedException.message,
                                        LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    })
                }
            } catch (e: java.lang.IllegalStateException) {
                println("Warning: Illegal state exception in PlayerFragment")
            }
        }
    }

    override fun onStart() {
        super.onStart()
        hideSystemUI()
        if (data?.card != null) {
            val pro = getViewPosDur(data?.card!!.slug, data?.episodeIndex!!)
            if (pro.pos > 0 && pro.dur > 0 && (pro.pos * 100 / pro.dur) < 95) { // UNDER 95% RESUME
                playbackPosition = pro.pos
            }
        }
        thread {
            if (Util.SDK_INT > 23) {
                initPlayer()
                if (player_view != null) player_view.onResume()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // When restarting activity the rotation is ensured :)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE

        if (Util.SDK_INT <= 23) {
            initPlayer()
            if (player_view != null) player_view.onResume()
        }
    }

    override fun onPause() {
        super.onPause()
        if (Util.SDK_INT <= 23) {
            if (player_view != null) player_view.onPause()
            releasePlayer()
        }
    }

    override fun onStop() {
        super.onStop()
        if (Util.SDK_INT > 23) {
            if (player_view != null) player_view.onPause()
            releasePlayer()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        // Inflate the layout for this fragment
        val playerView = inflater.inflate(R.layout.player, container, false)
        return playerView

    }
}