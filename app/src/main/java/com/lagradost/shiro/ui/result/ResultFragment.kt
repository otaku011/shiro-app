package com.lagradost.shiro.ui.result

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.Transition
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat.getColor
import androidx.core.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.mediarouter.app.MediaRouteButton
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.cast.CastPlayer
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener
import com.google.android.gms.common.images.WebImage
import com.lagradost.shiro.*
import com.lagradost.shiro.AniListApi.Companion.AniListStatusType
import com.lagradost.shiro.AniListApi.Companion.fromIntToAnimeStatus
import com.lagradost.shiro.AniListApi.Companion.getAllSeasons
import com.lagradost.shiro.AniListApi.Companion.postDataAboutId
import com.lagradost.shiro.AniListApi.Companion.secondsToReadable
import com.lagradost.shiro.DataStore.mapper
import com.lagradost.shiro.FastAniApi.Companion.requestHome
import com.lagradost.shiro.MALApi.Companion.malStatusAsString
import com.lagradost.shiro.MALApi.Companion.setScoreRequest
import com.lagradost.shiro.MainActivity.Companion.getColorFromAttr
import com.lagradost.shiro.MainActivity.Companion.hideKeyboard
import com.lagradost.shiro.MainActivity.Companion.openBrowser
import com.lagradost.shiro.MainActivity.Companion.getNextEpisode
import com.lagradost.shiro.MainActivity.Companion.isCastApiAvailable
import com.lagradost.shiro.MainActivity.Companion.loadPlayer
import com.lagradost.shiro.ui.GlideApp
import com.lagradost.shiro.ui.PlayerFragment
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.episode_result.view.*
import kotlinx.android.synthetic.main.fragment_home.*
import kotlinx.android.synthetic.main.fragment_results.*
import kotlinx.android.synthetic.main.fragment_search.*
import kotlinx.android.synthetic.main.home_card.view.*
import kotlinx.android.synthetic.main.home_card.view.imageView
import kotlinx.android.synthetic.main.number_picker_dialog.*
import kotlinx.android.synthetic.main.player_custom_layout.*
import kotlinx.android.synthetic.main.search_result.view.*
import java.io.File
import kotlin.concurrent.thread


const val DESCRIPTION_LENGTH = 200

class ResultFragment : Fragment() {
    var data: FastAniApi.Card? = null
    private lateinit var resultViewModel: ResultViewModel
    private var isMovie: Boolean = false
    var isBookmarked = false
    private var seasons: List<AniListApi.SeasonResponse?>? = null

    companion object {
        var isInResults: Boolean = false
        var isViewState: Boolean = true

        fun fixEpTitle(
            _title: String?,
            epNum: Int,
            seNum: Int,
            isMovie: Boolean,
            formatBefore: Boolean = false,
        ): String {
            var title = _title
            if (title == null || title.replace(" ", "") == "") {
                title = "Episode $epNum"
            }
            if (!isMovie) {
                if (formatBefore) {
                    title = "S$seNum:E$epNum $title" //•
                } else {
                    title = "$epNum. $title"
                }
            }
            return title
        }

        fun newInstance(data: FastAniApi.Card) =
            ResultFragment().apply {
                arguments = Bundle().apply {
                    //println(data)
                    putString("data", mapper.writeValueAsString(data))
                }
            }
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        resultViewModel =
            activity?.let { ViewModelProviders.of(it).get(ResultViewModel::class.java) }!!
        return inflater.inflate(R.layout.fragment_results, container, false)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        arguments?.getString("data")?.let {
            data = mapper.readValue(it, FastAniApi.Card::class.java)
        }
        isMovie = data!!.episodes == 1 && data!!.status == "FINISHED"
        isBookmarked = DataStore.containsKey(BOOKMARK_KEY, data!!.anilistId)
    }
    /*
    private fun ToggleViewState(_isViewState: Boolean) {
        isViewState = _isViewState
        if (isViewState) {
            title_viewstate.setImageResource(R.drawable.filled_viewstate)
        } else {
            title_viewstate.setImageResource(R.drawable.outlined_viewstate)
        }
    }*/

    private fun toggleHeartVisual(_isBookmarked: Boolean) {
        if (_isBookmarked) {
            title_bookmark.setImageResource(R.drawable.filled_heart)
        } else {
            title_bookmark.setImageResource(R.drawable.outlined_heart)
        }
    }

    /* private fun toggleHeart(_isBookmarked: Boolean) {
         this.isBookmarked = _isBookmarked
         toggleHeartVisual(_isBookmarked)
         if (_isBookmarked) {
             DataStore.setKey<BookmarkedTitle>(
                 BOOKMARK_KEY,
                 data!!.anilistId,
                 BookmarkedTitle(data!!.id, data!!.anilistId, data!!.description, data!!.title, data!!.coverImage)
             )
         } else {
             DataStore.removeKey(BOOKMARK_KEY, data!!.anilistId)
         }
         thread {
             requestHome(true)
         }
     }*/

    private fun castEpsiode(seasonIndex: Int, episodeIndex: Int) {
        if (isCastApiAvailable()) {
            val castContext = CastContext.getSharedInstance(activity!!.applicationContext)
            castContext.castOptions
            val ep = data!!.cdnData.seasons[seasonIndex].episodes[episodeIndex]
            val poster = ep.thumb
            val url = ep.file
            val key = MainActivity.getViewKey(data!!.anilistId, seasonIndex, episodeIndex)

            val movieMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE)
            movieMetadata.putString(
                MediaMetadata.KEY_TITLE,
                fixEpTitle(ep.title, episodeIndex + 1, seasonIndex + 1, isMovie, true)
            )
            movieMetadata.putString(MediaMetadata.KEY_ALBUM_ARTIST, data!!.title.english)
            if (poster != null) {
                movieMetadata.addImage(WebImage(Uri.parse(poster)))
            }
            val mediaInfo = MediaInfo.Builder(url)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(MimeTypes.VIDEO_UNKNOWN)
                .setMetadata(movieMetadata).build()

            val mediaItems = arrayOf(MediaQueueItem.Builder(mediaInfo).build())
            val castPlayer = CastPlayer(castContext)

            castPlayer.loadItems(
                mediaItems,
                0,
                DataStore.getKey<Long>(VIEW_POS_KEY, key, 0L)!!,
                Player.REPEAT_MODE_OFF
            )


            /*castPlayer.setSessionAvailabilityListener(object : SessionAvailabilityListener {
                override fun onCastSessionAvailable() {

                }

                override fun onCastSessionUnavailable() {}
            })*/
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun loadSeason(index: Int) {
        currentSeasonIndex = index
        seasonChange()
        title_season_cards.removeAllViews()
        var epNum = 0
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(MainActivity.activity)
        val save = settingsManager.getBoolean("save_history", true)

        // When fastani is down it doesn't report any seasons and this is needed.
        if (data!!.cdnData.seasons.isNotEmpty()) {
            data!!.cdnData.seasons[index].episodes.forEach { fullEpisode ->
                val epIndex = epNum
                epNum++

                val card: View = layoutInflater.inflate(R.layout.episode_result, null)
                if (fullEpisode.thumb != null) {
                    // Can be "N/A"
                    if (fullEpisode.thumb.startsWith("http")) {
                        val glideUrl = GlideUrl(fullEpisode.thumb)
                        context?.let {
                            Glide.with(it)
                                .load(glideUrl)
                                .into(card.imageView)
                        }
                    }
                }

                val key = MainActivity.getViewKey(data!!.anilistId, index, epIndex)

                /*if (MainActivity.isDonor) {
                    card.cdi.setOnClickListener {
                        if (data != null) {
                            DownloadManager.downloadEpisode(
                                DownloadManager.DownloadInfo(
                                    index,
                                    epIndex,
                                    data!!.title,
                                    isMovie,
                                    data!!.anilistId,
                                    data!!.id,
                                    data!!.cdnData.seasons[index].episodes[epIndex],
                                    data!!.coverImage.large
                                )
                            )
                        }
                    }
                } else {
                    card.cdi.visibility = View.GONE
                    val param = card.cardTitle.layoutParams as ViewGroup.MarginLayoutParams
                    param.updateMarginsRelative(
                        card.cardTitle.marginLeft,
                        card.cardTitle.marginTop,
                        10.toPx,
                        card.cardTitle.marginBottom
                    )
                    card.cardTitle.layoutParams = param
                }

                card.episode_result_root.setOnClickListener {

                    if (save) {
                        DataStore.setKey<Long>(VIEWSTATE_KEY, key, System.currentTimeMillis())
                    }

                    if (MainActivity.isCastApiAvailable()) {
                        val castContext = CastContext.getSharedInstance(activity!!.applicationContext)
                        println("SSTATE: " + castContext.castState + "<<")
                        if (castContext.castState == CastState.CONNECTED) {
                            castEpsiode(index, epIndex)
                            loadSeason(index)
                            return@setOnClickListener
                        } else {
                            //MainActivity.loadPlayer(epIndex, index, data!!)
                        }
                    } else {
                        //MainActivity.loadPlayer(epIndex, index, data!!)
                    }
                }

                card.setOnLongClickListener {
                    if (isViewState) {
                        if (DataStore.containsKey(VIEWSTATE_KEY, key)) {
                            DataStore.removeKey(VIEWSTATE_KEY, key)
                        } else {
                            DataStore.setKey<Long>(VIEWSTATE_KEY, key, System.currentTimeMillis())
                        }
                        loadSeason(index)
                    }
                    return@setOnLongClickListener true
                }

                val title = fixEpTitle(fullEpisode.title, epNum, index + 1, isMovie)

                card.cardTitle.text = title
                if (DataStore.containsKey(VIEWSTATE_KEY, key)) {
                    card.cardBg.setCardBackgroundColor(
                        requireContext().getColorFromAttr(
                            R.attr.colorPrimaryMegaDark
                        )
                    )
                }

                val pro = MainActivity.getViewPosDur(data!!.anilistId, index, epIndex)
                //println("DURPOS:" + epNum + "||" + pro.pos + "|" + pro.dur)
                if (pro.dur > 0 && pro.pos > 0) {
                    var progress: Int = (pro.pos * 100L / pro.dur).toInt()
                    if (progress < 5) {
                        progress = 5
                    } else if (progress > 95) {
                        progress = 100
                    }
                    card.video_progress.progress = progress
                } else {
                    card.video_progress.alpha = 0f
                }

                if (MainActivity.isDonor) {
                    val internalId = (data!!.anilistId + "S${index}E${epIndex}").hashCode()
                    val child = DataStore.getKey<DownloadManager.DownloadFileMetadata>(
                        DOWNLOAD_CHILD_KEY,
                        internalId.toString()
                    )
                    // ================ DOWNLOAD STUFF ================
                    if (child != null) {
                        println("CHILD NOT NULL:" + epIndex)
                        val file = File(child.videoPath)
                        if (file.exists()) {
                            val megaBytesTotal = DownloadManager.convertBytesToAny(child.maxFileSize, 0, 2.0).toInt()
                            val localBytesTotal =
                                maxOf(DownloadManager.convertBytesToAny(file.length(), 0, 2.0).toInt(), 1)

                            fun updateIcon(megabytes: Int) {
                                if (!file.exists()) {
                                    card.cdi.visibility = View.VISIBLE
                                    card.progressBar.visibility = View.GONE
                                    card.cardPauseIcon.visibility = View.GONE
                                    card.cardRemoveIcon.visibility = View.GONE
                                } else {
                                    card.cdi.visibility = View.GONE
                                    if (megabytes + 3 >= megaBytesTotal) {
                                        card.progressBar.visibility = View.GONE
                                        card.cardPauseIcon.visibility = View.GONE
                                        card.cardRemoveIcon.visibility = View.VISIBLE
                                    } else {
                                        card.progressBar.visibility = View.VISIBLE
                                        card.cardRemoveIcon.visibility = View.GONE
                                        card.cardPauseIcon.visibility = View.VISIBLE
                                    }
                                }
                            }

                            println("FILE EXISTS:" + epIndex)
                            fun deleteFile() {
                                if (file.exists()) {
                                    file.delete()
                                }
                                activity?.runOnUiThread {
                                    DataStore.removeKey(DOWNLOAD_CHILD_KEY, key)
                                    Toast.makeText(
                                        context,
                                        "${child.videoTitle} S${child.seasonIndex + 1}:E${child.episodeIndex + 1} deleted",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    updateIcon(0)
                                }
                            }

                            card.cardRemoveIcon.setOnClickListener {
                                val alertDialog: AlertDialog? = activity?.let {
                                    val builder = AlertDialog.Builder(it)
                                    builder.apply {
                                        setPositiveButton("Delete",
                                            DialogInterface.OnClickListener { dialog, id ->
                                                deleteFile()
                                            })
                                        setNegativeButton("Cancel",
                                            DialogInterface.OnClickListener { dialog, id ->
                                                // User cancelled the dialog
                                            })
                                    }
                                    // Set other dialog properties
                                    builder.setTitle("Delete ${child.videoTitle} - S${child.seasonIndex + 1}:E${child.episodeIndex + 1}")

                                    // Create the AlertDialog
                                    builder.create()
                                }
                                alertDialog?.show()
                            }

                            card.cardTitle.text = title

                            //card.cardTitleExtra.text = "$localBytesTotal / $megaBytesTotal MB"


                            fun getDownload(): DownloadManager.DownloadInfo {
                                return DownloadManager.DownloadInfo(
                                    child.seasonIndex,
                                    child.episodeIndex,
                                    data!!.title,
                                    isMovie,
                                    child.anilistId,
                                    child.fastAniId,
                                    FastAniApi.FullEpisode(child.downloadFileUrl, child.videoTitle, child.thumbPath),
                                    null
                                )
                            }

                            fun getStatus(): Boolean { // IF CAN RESUME
                                return if (DownloadManager.downloadStatus.containsKey(child.internalId)) {
                                    DownloadManager.downloadStatus[child.internalId] == DownloadManager.DownloadStatusType.IsPaused
                                } else {
                                    true
                                }
                            }

                            fun setStatus() {
                                activity?.runOnUiThread {
                                    if (getStatus()) {
                                        card.cardPauseIcon.setImageResource(R.drawable.netflix_play)
                                    } else {
                                        card.cardPauseIcon.setImageResource(R.drawable.exo_icon_stop)
                                    }
                                }
                            }

                            setStatus()
                            updateIcon(localBytesTotal)

                            card.cardPauseIcon.setOnClickListener { v ->
                                val popup = PopupMenu(context, v)
                                if (getStatus()) {
                                    popup.setOnMenuItemClickListener {
                                        when (it.itemId) {
                                            R.id.res_resumedload -> {
                                                DownloadManager.downloadEpisode(getDownload(), true)
                                            }
                                            R.id.res_stopdload -> {
                                                DownloadManager.invokeDownloadAction(
                                                    child.internalId,
                                                    DownloadManager.DownloadStatusType.IsStopped
                                                )
                                                deleteFile()
                                            }
                                        }
                                        return@setOnMenuItemClickListener true
                                    }
                                    popup.inflate(R.menu.resume_menu)
                                } else {
                                    popup.setOnMenuItemClickListener {
                                        when (it.itemId) {
                                            R.id.stop_pauseload -> {
                                                DownloadManager.invokeDownloadAction(
                                                    child.internalId,
                                                    DownloadManager.DownloadStatusType.IsPaused
                                                )
                                            }
                                            R.id.stop_stopdload -> {
                                                DownloadManager.invokeDownloadAction(
                                                    child.internalId,
                                                    DownloadManager.DownloadStatusType.IsStopped
                                                )
                                                deleteFile()
                                            }
                                        }
                                        return@setOnMenuItemClickListener true
                                    }
                                    popup.inflate(R.menu.stop_menu)
                                }
                                popup.show()
                            }

                            card.progressBar.progress = maxOf(minOf(localBytesTotal * 100 / megaBytesTotal, 100), 0)

                            DownloadManager.downloadPauseEvent += {
                                if (it == child.internalId) {
                                    setStatus()
                                }
                            }

                            DownloadManager.downloadDeleteEvent += {
                                if (it == child.internalId) {
                                    deleteFile()
                                }
                            }

                            DownloadManager.downloadEvent += {
                                activity?.runOnUiThread {
                                    if (it.id == child.internalId) {
                                        val megaBytes = DownloadManager.convertBytesToAny(it.bytes, 0, 2.0).toInt()
                                        //card.cardTitleExtra.text = "${megaBytes} / $megaBytesTotal MB"
                                        card.progressBar.progress = maxOf(
                                            minOf(megaBytes * 100 / megaBytesTotal, 100),
                                            0
                                        )
                                        updateIcon(megaBytes)
                                    }
                                }
                            }
                            // END DOWNLOAD
                        }
                    }
                }
                title_season_cards.addView(card)*/
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        PlayerFragment.onLeftPlayer -= ::onLeftVideoPlayer
        DownloadManager.downloadStartEvent -= ::onDownloadStarted
        isInResults = false
    }

    fun onLeftVideoPlayer(event: Boolean) {
        loadSeason(currentSeasonIndex)
    }

    fun onDownloadStarted(anilistId: String) {
        if (anilistId == data!!.anilistId) {
            activity?.runOnUiThread {
                loadSeason(currentSeasonIndex)
            }
        }
    }

    var currentSeasonIndex: Int = 0
    var currentAniListId: Int = 0
    var currentMalId: Int? = null

    private fun loadGetDataAboutId() {
        val hasAniList = DataStore.getKey<String>(
            ANILIST_TOKEN_KEY,
            ANILIST_ACCOUNT_ID,
            null
        ) != null
        val hasMAL = DataStore.getKey<String>(MAL_TOKEN_KEY, MAL_ACCOUNT_ID, null) != null
        println("HAS MAL $hasMAL HASANI $hasAniList")

        val malHolder = if (hasMAL) currentMalId?.let { MALApi.getDataAboutId(it) } else null
        val holder = if (hasAniList && !hasMAL) AniListApi.getDataAboutId(currentAniListId) else null
        //setAllMalData()
        //MALApi.allTitles.get(currentMalId)

        if (holder != null || malHolder != null) {
            class CardAniListInfo {
                // Sets to watching if anything is done
                fun typeGetter(): AniListStatusType {
                    return if (malHolder != null) {
                        var type = fromIntToAnimeStatus(malStatusAsString.indexOf(malHolder.my_list_status?.status))
                        type =
                            if (type.value == MALApi.Companion.MalStatusType.None.value) AniListStatusType.Watching else type
                        type
                    } else {
                        val type =
                            if (holder!!.type == AniListStatusType.None) AniListStatusType.Watching else holder.type
                        fromIntToAnimeStatus(type.value)
                    }
                }

                var type = typeGetter()
                    set(value) {
                        //field = value
                        println("Changed type")
                        field = fromIntToAnimeStatus(this.typeValue)
                        requireActivity().runOnUiThread {
                            status_text.text = field.name
                        }
                    }

                // This is helper class to type.value because setter on type.value isn't working.
                var typeValue = type.value
                    set(value) {
                        field = value
                        // Invoke setter
                        // println("Invoked setter")
                        this::type.setter.call(type)
                    }
                var progress =
                    if (malHolder?.my_list_status != null) malHolder.my_list_status.num_episodes_watched else holder!!.progress
                    set(value) {
                        field = maxOf(0, minOf(value, episodes))
                        requireActivity().runOnUiThread {
                            aniList_progressbar.progress = field * 100 / episodes
                            anilist_progress_txt.text = "${field}/${episodes}"
                            status_text.text = type.name
                        }
                        requireActivity().runOnUiThread {
                            if (progress == episodes && typeValue != AniListStatusType.Completed.value) {
                                Toast.makeText(
                                    requireContext(),
                                    "All episodes seen, marking as Completed",
                                    Toast.LENGTH_LONG
                                ).show()
                                typeValue = AniListStatusType.Completed.value
                            }
                            if (progress != episodes && typeValue == AniListStatusType.Completed.value) {
                                Toast.makeText(
                                    requireContext(),
                                    "Marking as Watching",
                                    Toast.LENGTH_LONG
                                ).show()
                                typeValue = AniListStatusType.Watching.value
                            }
                        }
                        /*if (field == holder.episodes) {
                            this.type.value = AniListStatusType.Completed.value
                        } else if (field != holder.episodes && this.type.value == AniListStatusType.Completed.value) {
                            this.type.value = AniListStatusType.Watching.value
                        }*/
                    }
                var score = if (malHolder?.my_list_status != null) malHolder.my_list_status.score else holder!!.score
                    set(value) {
                        field = value
                        requireActivity().runOnUiThread {
                            rating_text.text = if (value == 0) "Rate" else value.toString()
                            status_text.text = type.name
                        }
                    }
                var episodes = if (malHolder != null) malHolder.num_episodes else holder!!.episodes

                fun syncData() {
                    thread {

                        val anilistPost =
                            if (hasAniList) postDataAboutId(currentAniListId, type, score, progress) else true
                        val malPost = if (hasMAL)
                            currentMalId?.let {
                                setScoreRequest(
                                    it,
                                    MALApi.fromIntToAnimeStatus(type.value),
                                    score,
                                    progress
                                )
                            } else true
                        if (!anilistPost || malPost?.not() == true) {
                            requireActivity().runOnUiThread {
                                Toast.makeText(
                                    requireContext(),
                                    "Error updating episode progress",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            }

            val info = CardAniListInfo()
            info.episodes = maxOf(
                info.episodes,
                data!!.cdnData.seasons[currentSeasonIndex].episodes.size
            ) // TO REMOVE DIVIDE BY 0 ERROR
            activity!!.runOnUiThread {
                val transition: Transition = ChangeBounds()
                transition.duration = 100 // DURATION OF ANIMATION IN MS

                anilist_holder.visibility = VISIBLE
                aniList_progressbar.progress = info.progress * 100 / info.episodes
                anilist_progress_txt.text = "${info.progress}/${info.episodes}"
                anilist_btt_holder.visibility = VISIBLE
                status_text.text = if (info.type.value == AniListStatusType.None.value) "Status" else info.type.name
                rating_text.text = if (info.score == 0) "Rate" else info.score.toString()
                TransitionManager.beginDelayedTransition(title_holder, transition)

                edit_episodes_btt.setOnClickListener {
                    val dialog = Dialog(requireContext())
                    //dialog.window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                    dialog.setTitle("Select episodes seen")
                    dialog.setContentView(R.layout.number_picker_dialog)

                    dialog.number_picker_episode_text.setText(info.progress.toString())

                    dialog.number_picker_episode_up.setOnClickListener {
                        val number = if (dialog.number_picker_episode_text.text.toString().toIntOrNull() == null
                        ) 1 else minOf(dialog.number_picker_episode_text.text.toString().toInt() + 1, info.episodes)
                        dialog.number_picker_episode_text.setText(number.toString())
                    }
                    dialog.number_picker_episode_down.setOnClickListener {
                        val number = if (dialog.number_picker_episode_text.text.toString().toIntOrNull() == null
                        ) 0 else maxOf(dialog.number_picker_episode_text.text.toString().toInt() - 1, 0)
                        dialog.number_picker_episode_text.setText(number.toString())
                    }
                    dialog.episode_progress_btt.setOnClickListener {
                        thread {
                            val progress =
                                if (dialog.number_picker_episode_text.text.toString().toIntOrNull() == null
                                ) 0 else minOf(
                                    dialog.number_picker_episode_text.text.toString().toInt(),
                                    info.episodes
                                )
                            // Applying progress after is needed
                            info.progress = progress
                            info.syncData()
                            dialog.dismiss()
                        }
                    }
                    dialog.show()
                }


                rating_btt.setOnClickListener {
                    val dialog = Dialog(requireContext())
                    // Lmao this needs something better
                    val ids = listOf(
                        R.id.rating_text_no_rating,
                        R.id.rating_text_1,
                        R.id.rating_text_2,
                        R.id.rating_text_3,
                        R.id.rating_text_4,
                        R.id.rating_text_5,
                        R.id.rating_text_6,
                        R.id.rating_text_7,
                        R.id.rating_text_8,
                        R.id.rating_text_9,
                        R.id.rating_text_10,
                    )

                    //dialog.window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                    dialog.setTitle("Rate")
                    dialog.setContentView(R.layout.rating_pick_dialog)

                    for (i in 0..10) {
                        val button = dialog.findViewById<Button>(ids[i])
                        if (i == info.score) {
                            button.typeface = Typeface.DEFAULT_BOLD
                        }
                        button.setOnClickListener {
                            info.score = i
                            info.syncData()
                            dialog.dismiss()
                        }
                    }
                    dialog.show()
                }

                status_btt.setOnClickListener {
                    val dialog = Dialog(requireContext())
                    val ids = listOf(
                        R.id.status_currently_watching,
                        R.id.status_completed,
                        R.id.status_paused,
                        R.id.status_dropped,
                        R.id.status_plan_to_watch,
                        R.id.status_rewatching,
                    )
                    //dialog.window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                    dialog.setTitle("Pick status")
                    dialog.setContentView(R.layout.status_picker_dialog)
                    for (i in 0..5) {
                        val button = dialog.findViewById<Button>(ids[i])
                        if (i == info.typeValue) {
                            button.typeface = Typeface.DEFAULT_BOLD
                        }
                        button.setOnClickListener {
                            info.typeValue = i

                            if (i == AniListStatusType.Completed.value) {
                                info.progress = info.episodes
                            }

                            info.syncData()
                            dialog.dismiss()
                        }
                    }
                    dialog.show()
                }

                title_anilist.setOnClickListener {
                    openBrowser("https://anilist.co/anime/${currentAniListId}")
                }
            }
        }
    }

    private fun seasonChange() {
        if (seasons != null) {
            thread {
                try {
                    val currentData = seasons!![currentSeasonIndex]!!.data.Media
                    currentAniListId = currentData.id
                    currentMalId = currentData.idMal
                    println("GET DATA ABOUT: " + currentAniListId)
                    println("ANI" + DataStore.getKey<String>(MAL_TOKEN_KEY, MAL_ACCOUNT_ID, null))
                    if (DataStore.getKey<String>(
                            ANILIST_TOKEN_KEY,
                            ANILIST_ACCOUNT_ID,
                            null
                        ) != null || DataStore.getKey<String>(MAL_TOKEN_KEY, MAL_ACCOUNT_ID, null) != null
                    ) {
                        loadGetDataAboutId()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        currentAniListId = data!!.anilistId.toInt()

        hideKeyboard()
        /*if (!FastAniApi.lastCards.containsKey(data!!.id)) {
            FastAniApi.lastCards[data!!.id] = data!!
        }*/
        title_duration.text = data!!.duration.toString() + "min"

        thread {
            seasons = getAllSeasons(data!!.anilistId.toInt())
            if (seasons != null && seasons?.size != 0) {
                activity?.runOnUiThread {
                    if (seasons!!.last()?.data?.Media?.nextAiringEpisode?.timeUntilAiring != null) {

                        title_duration.text =
                            data!!.duration.toString() + "min | Next episode airing in " + secondsToReadable(
                                seasons!!.last()?.data?.Media?.nextAiringEpisode?.timeUntilAiring!!,
                                "Now"
                            )
                    } else {
                        title_duration.text =
                            data!!.duration.toString() + "min | Completed"
                    }
                }
            }
            seasonChange()
        }


        if (MainActivity.isCastApiAvailable()) {
            val mMediaRouteButton = view.findViewById<MediaRouteButton>(R.id.media_route_button)

            CastButtonFactory.setUpMediaRouteButton(activity, mMediaRouteButton);
            val castContext = CastContext.getSharedInstance(activity!!.applicationContext)

            if (castContext.castState != CastState.NO_DEVICES_AVAILABLE) media_route_button.visibility = View.VISIBLE
            castContext.addCastStateListener(CastStateListener { state ->
                if (media_route_button != null) {
                    if (state == CastState.NO_DEVICES_AVAILABLE) media_route_button.visibility = View.GONE else {
                        if (media_route_button.visibility == View.GONE) media_route_button.visibility = View.VISIBLE
                    }
                }
            })
            fab_play_button.setOnClickListener {
                if (data != null) {
                    /*val nextEpisode = getNextEpisode(data!!)
                    if (castContext.castState == CastState.CONNECTED) {
                        castEpsiode(nextEpisode.seasonIndex, nextEpisode.episodeIndex)
                        loadSeason(nextEpisode.seasonIndex)
                    } else {
                       // loadPlayer(nextEpisode.episodeIndex, nextEpisode.seasonIndex, data!!)
                    }*/
                }
            }
        } else {
            fab_play_button.setOnClickListener {
                if (data != null) {
                    //val nextEpisode = getNextEpisode(data!!)
                    //loadPlayer(nextEpisode.episodeIndex, nextEpisode.seasonIndex, data!!)
                }
            }
        }
        isInResults = true
        //isViewState = false
        PlayerFragment.onLeftPlayer += ::onLeftVideoPlayer
        DownloadManager.downloadStartEvent += ::onDownloadStarted
        toggleHeartVisual(isBookmarked)

        title_go_back_holder.setPadding(0, MainActivity.statusHeight, 0, 0)
        media_route_button_holder.setPadding(0, MainActivity.statusHeight, 0, 0)
        //media_route_button.layoutParams = LinearLayout.LayoutParams(20.toPx, 20.toPx + MainActivity.statusHeight)  //setPadding(0, MainActivity.statusHeight, 0, 0)
        title_go_back_holder.setOnClickListener {
            MainActivity.popCurrentPage()
        }

        bookmark_holder.setOnClickListener {
            //toggleHeart(!isBookmarked)
        }
        if (data != null) {
            // val nextEpisode = getNextEpisode(data!!)
            /*if (nextEpisode.isFound) {
                fab_play_button.visibility = VISIBLE
            }*/
        }
        fab_play_button.setOnLongClickListener {
            //MainActivity.loadPage(cardInfo!!)
            if (data != null) {
                /*val nextEpisode = getNextEpisode(data!!)
                Toast.makeText(
                    activity,
                    "Season ${nextEpisode.seasonIndex + 1} Episode ${nextEpisode.episodeIndex + 1}",
                    Toast.LENGTH_LONG
                ).show()*/
            }
            return@setOnLongClickListener true
        }
        /*
        title_viewstate.setOnClickListener {
            ToggleViewState(!isViewState)
        }*/

        view.setOnTouchListener { _, _ -> return@setOnTouchListener true } // VERY IMPORTANT https://stackoverflow.com/questions/28818926/prevent-clicking-on-a-button-in-an-activity-while-showing-a-fragment

        title_holder.setPadding(
            title_holder.paddingLeft,
            MainActivity.statusHeight + title_background.minimumHeight - 44.toPx,
            title_holder.paddingRight,
            0,
        )

        val glideUrl =
            GlideUrl("https://fastani.net/" + data!!.bannerImage) { FastAniApi.currentHeaders }

        if (data!!.trailer != null) {
            title_background.setOnLongClickListener {
                Toast.makeText(context, data!!.title.english + " - Trailer", Toast.LENGTH_SHORT).show()
                return@setOnLongClickListener true
            }

            title_background.setOnClickListener() {
                MainActivity.loadPlayer(
                    data!!.title.english + " - Trailer",
                    "https://fastani.net/" + data!!.trailer!!,
                    null
                )
            }
        } else {
            title_trailer_btt.alpha = 0f
        }

        // SEASON SELECTOR
        val seasonsTxt = data!!.cdnData.seasons.mapIndexed { i: Int, _: FastAniApi.Seasons -> "Season ${i + 1}" }
        val arrayAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, seasonsTxt)
        spinner.adapter = arrayAdapter
        class SpinnerClickListener : AdapterView.OnItemClickListener, AdapterView.OnItemSelectedListener {
            override fun onItemClick(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {}
            override fun onNothingSelected(p0: AdapterView<*>?) {}
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                loadSeason(p2)
            }
        }
        if (data!!.cdnData.seasons.size <= 1) {
            spinner.background = null
            spinner.isEnabled = false
            spinnerRoot.backgroundTintList = ColorStateList.valueOf(getColor(requireContext(), R.color.background));
        }
        spinner.onItemSelectedListener = SpinnerClickListener()
        // loadSeason(0)

        context?.let {
            GlideApp.with(it)
                .load(glideUrl)
                .into(title_background)
        }

        title_name.text = data!!.title.english
        val descript = data!!.description
            .replace("<br>", "")
            .replace("<i>", "")
            .replace("</i>", "")
            .replace("\n", " ")

        title_descript.text = descript.substring(0, minOf(descript.length, DESCRIPTION_LENGTH1 - 3)) + "..."
        title_descript.setOnClickListener {
            val transition: Transition = ChangeBounds()
            transition.duration = 100
            if (title_descript.text.length == 200) {
                title_descript.text = descript
            } else {
                title_descript.text = descript.substring(0, minOf(descript.length, DESCRIPTION_LENGTH1 - 3)) + "..."
            }
            TransitionManager.beginDelayedTransition(title_holder, transition)
        }

        var ratTxt = (data!!.averageScore / 10f).toString().replace(',', '.') // JUST IN CASE DUE TO LANG SETTINGS
        if (!ratTxt.contains('.')) ratTxt += ".0"
        title_rating.text = "Rated: $ratTxt"
        title_genres.text = data!!.genres.joinToString(prefix = "", postfix = "", separator = "  ") //  •

    }
}
