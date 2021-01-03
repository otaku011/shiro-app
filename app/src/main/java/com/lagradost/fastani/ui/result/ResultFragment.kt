package com.lagradost.fastani.ui.result

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat.getColor
import androidx.core.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.mediarouter.app.MediaRouteButton
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.google.android.gms.cast.framework.CastButtonFactory
import com.lagradost.fastani.*
import com.lagradost.fastani.FastAniApi.Companion.requestHome
import com.lagradost.fastani.MainActivity.Companion.getColorFromAttr
import com.lagradost.fastani.ui.GlideApp
import com.lagradost.fastani.ui.PlayerFragment
import kotlinx.android.synthetic.main.episode_result.view.*
import kotlinx.android.synthetic.main.fragment_search.*
import kotlinx.android.synthetic.main.fragment_home.*
import kotlinx.android.synthetic.main.fragment_results.*
import kotlinx.android.synthetic.main.home_card.view.*
import kotlinx.android.synthetic.main.home_card.view.imageView
import kotlinx.android.synthetic.main.player_custom_layout.*
import kotlinx.android.synthetic.main.search_result.view.*
import kotlin.concurrent.thread
import com.google.android.gms.cast.framework.CastState

import com.google.android.gms.cast.framework.CastStateListener
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.MediaQueueItem

import com.google.android.exoplayer2.util.MimeTypes

import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata

import com.google.android.gms.common.images.WebImage
import com.google.android.exoplayer2.Player

import com.google.android.exoplayer2.ext.cast.CastPlayer
import com.lagradost.fastani.DataStore.mapper
import com.lagradost.fastani.MainActivity.Companion.hideKeyboard
import kotlinx.android.synthetic.main.activity_main.*


const val DESCRIPTION_LENGTH = 200
const val has_download_perms = true

class ResultFragment : Fragment() {
    var data: FastAniApi.Card? = null
    private lateinit var resultViewModel: ResultViewModel
    private var isMovie: Boolean = false
    var isBookmarked = false

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
        println(data)
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

    private fun ToggleHeartVisual(_isBookmarked: Boolean) {
        if (_isBookmarked) {
            title_bookmark.setImageResource(R.drawable.filled_heart)
        } else {
            title_bookmark.setImageResource(R.drawable.outlined_heart)
        }
    }

    private fun ToggleHeart(_isBookmarked: Boolean) {
        this.isBookmarked = _isBookmarked
        ToggleHeartVisual(_isBookmarked)
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
    }

    fun castEpsiode(seasonIndex: Int, episodeIndex: Int) {
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

    @SuppressLint("ClickableViewAccessibility")
    private fun loadSeason(index: Int) {
        currentSeasonIndex = index
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

                if (MainActivity.isDonor) {
                    card.cardDownloadIcon.setOnClickListener {
                        if (data != null) {
                            DownloadManager.downloadEpisode(DownloadManager.DownloadInfo(
                                index,
                                epIndex,
                                data!!.title,
                                isMovie,
                                data!!.anilistId,
                                data!!.id,
                                data!!.cdnData.seasons[index].episodes[epIndex],
                                data!!.coverImage.large
                            ))
                        }
                    }
                } else {
                    card.cardDownloadIcon.visibility = View.GONE
                    val param = card.cardTitle.layoutParams as ViewGroup.MarginLayoutParams
                    param.updateMarginsRelative(
                        card.cardTitle.marginLeft,
                        card.cardTitle.marginTop,
                        10.toPx,
                        card.cardTitle.marginBottom
                    )
                    card.cardTitle.layoutParams = param
                }

                card.imageView.setOnClickListener {
                    val castContext = CastContext.getSharedInstance(activity!!.applicationContext)
                    println("SSTATE: " + castContext.castState + "<<")
                    if (save) {
                        DataStore.setKey<Long>(VIEWSTATE_KEY, key, System.currentTimeMillis())
                    }
                    if (castContext.castState == CastState.CONNECTED) {
                        castEpsiode(index, epIndex)
                        loadSeason(index)
                    } else {
                        MainActivity.loadPlayer(epIndex, index, data!!)
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
                            R.attr.colorPrimaryDark
                        )
                    )
                }

                val pro = MainActivity.getViewPosDur(data!!.anilistId, index, epIndex)
                println("DURPOS:" + epNum + "||" + pro.pos + "|" + pro.dur)
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

                title_season_cards.addView(card)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        PlayerFragment.onLeftPlayer -= ::OnLeftVideoPlayer
        isInResults = false
    }

    fun OnLeftVideoPlayer(event: Boolean) {
        loadSeason(currentSeasonIndex)
    }

    var currentSeasonIndex: Int = 0

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        hideKeyboard()
        if (!FastAniApi.lastCards.containsKey(data!!.id)) {
            FastAniApi.lastCards[data!!.id] = data!!
        }

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
        isInResults = true
        //isViewState = false
        PlayerFragment.onLeftPlayer += ::OnLeftVideoPlayer
        ToggleHeartVisual(isBookmarked)

        title_go_back_holder.setPadding(0, MainActivity.statusHeight, 0, 0)
        media_route_button_holder.setPadding(0, MainActivity.statusHeight, 0, 0)
        //media_route_button.layoutParams = LinearLayout.LayoutParams(20.toPx, 20.toPx + MainActivity.statusHeight)  //setPadding(0, MainActivity.statusHeight, 0, 0)
        title_go_back.setOnClickListener {
            MainActivity.popCurrentPage()
        }

        bookmark_holder.setOnClickListener {
            ToggleHeart(!isBookmarked)
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
        var descript = data!!.description
        if (descript.length > DESCRIPTION_LENGTH) {
            descript = descript.substring(0, DESCRIPTION_LENGTH)
                .replace("<br>", "")
                .replace("<i>", "")
                .replace("</i>", "")
                .replace("\n", " ") + "..."
        }
        title_descript.text = descript
        title_duration.text = data!!.duration.toString() + "min"
        var ratTxt = (data!!.averageScore / 10f).toString().replace(',', '.') // JUST IN CASE DUE TO LANG SETTINGS
        if (!ratTxt.contains('.')) ratTxt += ".0"
        title_rating.text = "Rated: $ratTxt"
        title_genres.text = data!!.genres.joinToString(prefix = "", postfix = "", separator = "  ") //  •
    }
}
