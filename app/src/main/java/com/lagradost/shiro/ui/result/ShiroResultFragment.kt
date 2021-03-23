package com.lagradost.shiro.ui.result

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.text.Html
import android.transition.ChangeBounds
import android.transition.Transition
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.*
import androidx.core.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.mediarouter.app.MediaRouteButton
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.model.GlideUrl
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener
import com.lagradost.shiro.*
import com.lagradost.shiro.DataStore.mapper
import com.lagradost.shiro.FastAniApi.Companion.getAnimePage
import com.lagradost.shiro.FastAniApi.Companion.getFullUrlCdn
import com.lagradost.shiro.FastAniApi.Companion.requestHome
import com.lagradost.shiro.MainActivity.Companion.hideKeyboard
import com.lagradost.shiro.MainActivity.Companion.isCastApiAvailable
import com.lagradost.shiro.MainActivity.Companion.popCurrentPage
import com.lagradost.shiro.ui.GlideApp
import com.lagradost.shiro.ui.PlayerFragment
import kotlinx.android.synthetic.main.episode_result.view.*
import kotlinx.android.synthetic.main.fragment_results_new.*
import kotlin.concurrent.thread


const val DESCRIPTION_LENGTH1 = 200

class ShiroResultFragment : Fragment() {
    var data: FastAniApi.AnimePageData? = null
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

        fun newInstance(data: FastAniApi.ShiroSearchResponseShow) =
            ShiroResultFragment().apply {
                arguments = Bundle().apply {
                    //println(data)
                    putString("ShiroSearchResponseShow", mapper.writeValueAsString(data))
                }
            }

        fun newInstance(data: FastAniApi.AnimePageData) =
            ShiroResultFragment().apply {
                arguments = Bundle().apply {
                    //println(data)
                    putString("AnimePageData", mapper.writeValueAsString(data))
                }
            }

        /*Creating a new Instance of the given data*/
        fun newInstance(data: BookmarkedTitle) =
            ShiroResultFragment().apply {
                arguments = Bundle().apply {
                    //println(data)
                    putString("BookmarkedTitle", mapper.writeValueAsString(data))
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
        return inflater.inflate(R.layout.fragment_results_new, container, false)
    }

    var onLoaded = Event<Boolean>()

    private fun onLoadEvent(isSucc: Boolean) {
        if (isSucc) {
            activity?.runOnUiThread {
                if (data == null) {
                    Toast.makeText(activity, "Error loading anime page!", Toast.LENGTH_LONG).show()
                    popCurrentPage()
                    return@runOnUiThread
                }
                val fadeAnimation = AlphaAnimation(1f, 0f)

                fadeAnimation.duration = 300
                fadeAnimation.fillAfter = true

                loading_overlay.startAnimation(fadeAnimation)
                loadSeason()

                val glideUrl =
                    GlideUrl(
                        data?.image?.let { getFullUrlCdn(it) }?.let { getFullUrlCdn(it) }
                    )
                context?.let {
                    GlideApp.with(it)
                        .load(glideUrl)
                        .into(title_background)
                }

                val textColor = resources.getString(R.color.textColor).substring(3)
                val textColorGrey = resources.getString(R.color.textColorGray).substring(3)
                if (data!!.status != null) {
                    // fromHtml is depreciated, but works on android 6 as opposed to the new
                    title_status.text =
                        Html.fromHtml(
                            "<font color=#${textColor}>Status:</font><font color=#${textColorGrey}> ${data!!.status}</font>"/*,
                            FROM_HTML_MODE_COMPACT*/
                        )
                } else {
                    title_status.visibility = GONE
                }
                isBookmarked = DataStore.containsKey(BOOKMARK_KEY, data!!.slug)
                toggleHeartVisual(isBookmarked)
                title_episodes.text =
                    Html.fromHtml(
                        "<font color=#${textColor}>Episodes:</font><font color=#${textColorGrey}> ${data!!.episodeCount}</font>"/*,
                        FROM_HTML_MODE_COMPACT*/
                    )

                if (data!!.year != null) {
                    title_year.text =
                        Html.fromHtml(
                            "<font color=#${textColor}>Year:</font><font color=#${textColorGrey}> ${data!!.year}</font>"/*,
                            FROM_HTML_MODE_COMPACT*/
                        )
                } else {
                    title_year.visibility = GONE
                }

                if (data!!.genres != null) {
                    title_genres.text =
                        Html.fromHtml(
                            "<font color=#${textColor}>Status:</font><font color=#${textColorGrey}> ${
                                data!!.genres?.joinToString(
                                    ", "
                                )
                            }</font>"/*,
                            FROM_HTML_MODE_COMPACT*/
                        )
                } else {
                    title_genres.visibility = GONE
                }

                if (data!!.schedule != null && data?.status != "finished") {
                    title_day_of_week.text =
                        Html.fromHtml(
                            "<font color=#${textColor}>Schedule:</font><font color=#${textColorGrey}> ${
                                data!!.schedule
                            }</font>"/*,
                            FROM_HTML_MODE_COMPACT*/
                        )
                } else {
                    title_day_of_week.visibility = GONE
                }

                title_name.text = data!!.name
                val descript = data!!.synopsis
                    .replace("<br>", "")
                    .replace("<i>", "")
                    .replace("</i>", "")
                    .replace("\n", " ")

                title_descript.text = if (descript.length > 200) Html.fromHtml(
                    descript.substring(0, minOf(descript.length, DESCRIPTION_LENGTH1 - 3)) + "..." +
                            "<font color=#${textColorGrey}><i> Read more...</i></font>"/*,
                            FROM_HTML_MODE_COMPACT*/
                ) else descript
                title_descript.setOnClickListener {
                    val transition: Transition = ChangeBounds()
                    transition.duration = 100
                    if (title_descript.text.length <= 200 + 13) {
                        title_descript.text = descript
                    } else {
                        title_descript.text =
                            Html.fromHtml(
                                descript.substring(0, minOf(descript.length, DESCRIPTION_LENGTH1 - 3)) + "..." +
                                        "<font color=#${textColorGrey}><i> Read more...</i></font>"/*,
                            FROM_HTML_MODE_COMPACT*/
                            )
                    }
                    TransitionManager.beginDelayedTransition(description_holder, transition)
                }
                /*var ratTxt = (data!!.averageScore / 10f).toString().replace(',', '.') // JUST IN CASE DUE TO LANG SETTINGS
                if (!ratTxt.contains('.')) ratTxt += ".0"
                title_rating.text = "Rated: $ratTxt"
                */
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        arguments?.getString("ShiroSearchResponseShow")?.let {
            thread {
                data = getAnimePage(mapper.readValue(it, FastAniApi.ShiroSearchResponseShow::class.java))?.data
                onLoaded.invoke(true)
            }
        }
        // Kinda hacky solution, but works
        arguments?.getString("AnimePageData")?.let {
            thread {
                val pageData = mapper.readValue(it, FastAniApi.AnimePageData::class.java)
                println("DATA $pageData")
                data = getAnimePage(
                    pageData.slug
                )?.data
                onLoaded.invoke(true)
            }
        }

        /*Calling the getAnimePage function to get the page*/
        arguments?.getString("BookmarkedTitle")?.let {
            thread {
                data = getAnimePage(mapper.readValue(it, BookmarkedTitle::class.java))?.data
                onLoaded.invoke(true)
            }
        }
        //isMovie = data!!.episodes == 1 && data!!.status == "FINISHED"

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

    private fun toggleHeart(_isBookmarked: Boolean) {
        this.isBookmarked = _isBookmarked
        toggleHeartVisual(_isBookmarked)
        /*Saving the new bookmark in the database*/
        if (_isBookmarked) {
            DataStore.setKey<BookmarkedTitle>(
                BOOKMARK_KEY,
                data!!.slug,
                BookmarkedTitle(
                    data!!.name,
                    data!!.image,
                    data!!.slug
                )
            )
        } else {
            DataStore.removeKey(BOOKMARK_KEY, data!!.slug)
        }
        thread {
            requestHome(true)
        }
    }


    private fun loadSeason() {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(MainActivity.activity)
        val save = settingsManager.getBoolean("save_history", true)

        if (data?.episodes?.isNotEmpty() == true) {
            val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = context?.let {
                EpisodeAdapter(
                    it,
                    data!!,
                    title_season_cards,
                    save,
                )
            }
            title_season_cards.adapter = adapter
            (title_season_cards.adapter as EpisodeAdapter).episodes =
                data!!.episodes
            (title_season_cards.adapter as EpisodeAdapter).notifyDataSetChanged()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        PlayerFragment.onLeftPlayer -= ::onLeftVideoPlayer
        DownloadManager.downloadStartEvent -= ::onDownloadStarted
        isInResults = false
    }

    fun onLeftVideoPlayer(event: Boolean) {
        loadSeason()
    }

    fun onDownloadStarted(anilistId: String) {
        /*if (anilistId == data!!.anilistId) {
            activity?.runOnUiThread {
                loadSeason(currentSeasonIndex)
            }
        }*/
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isInResults = true

        hideKeyboard()
        //title_duration.text = data!!.duration.toString() + "min"
        if (isCastApiAvailable()) {
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
        }
        //isViewState = false
        PlayerFragment.onLeftPlayer += ::onLeftVideoPlayer
        DownloadManager.downloadStartEvent += ::onDownloadStarted


        results_root.setPadding(0, MainActivity.statusHeight, 0, 0)
        //media_route_button_holder.setPadding(0, MainActivity.statusHeight, 0, 0)
        //media_route_button.layoutParams = LinearLayout.LayoutParams(20.toPx, 20.toPx + MainActivity.statusHeight)  //setPadding(0, MainActivity.statusHeight, 0, 0)
        title_go_back.setOnClickListener {
            MainActivity.popCurrentPage()
        }

        bookmark_holder.setOnClickListener {
            toggleHeart(!isBookmarked)
        }

        /*
        title_viewstate.setOnClickListener {
            ToggleViewState(!isViewState)
        }*/

        view.setOnTouchListener { _, _ -> return@setOnTouchListener true } // VERY IMPORTANT https://stackoverflow.com/questions/28818926/prevent-clicking-on-a-button-in-an-activity-while-showing-a-fragment

        title_holder.setPadding(
            title_holder.paddingLeft,
            MainActivity.statusHeight, //+ title_background.minimumHeight - 44.toPx,
            title_holder.paddingRight,
            0,
        )


        /*if (data!!.trailer != null) {
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
        }
        title_trailer_btt.alpha = 0f
*/
        // SEASON SELECTOR
        onLoaded += ::onLoadEvent


    }
}
