package com.lagradost.shiro.ui.home

import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.model.GlideUrl
import com.lagradost.shiro.*
import com.lagradost.shiro.FastAniApi.Companion.getFullUrl
import com.lagradost.shiro.FastAniApi.Companion.requestHome
import com.lagradost.shiro.MainActivity.Companion.getNextEpisode
import com.lagradost.shiro.MainActivity.Companion.loadPlayer
import com.lagradost.shiro.ui.GlideApp
import com.lagradost.shiro.ui.result.ShiroResultFragment
import kotlinx.android.synthetic.main.download_card.*
import kotlinx.android.synthetic.main.fragment_home.*
import kotlinx.android.synthetic.main.home_card.view.*
import kotlinx.android.synthetic.main.home_card.view.imageText
import kotlinx.android.synthetic.main.home_card.view.imageView
import kotlinx.android.synthetic.main.home_card_schedule.view.*
import kotlinx.android.synthetic.main.home_recently_seen.view.*
import kotlin.concurrent.thread

const val MAXIMUM_FADE = 0.3f
const val FADE_SCROLL_DISTANCE = 700f

class HomeFragment : Fragment() {

    private lateinit var homeViewModel: HomeViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        homeViewModel =
            activity?.let { ViewModelProviders.of(it).get(HomeViewModel::class.java) }!!
        thread {
            requestHome(true)
        }
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    private fun homeLoaded(data: FastAniApi.ShiroHomePage?) {
        activity?.runOnUiThread {

            trending_anime_scroll_view.removeAllViews()
            recently_updated_scroll_view.removeAllViews()
            favouriteScrollView.removeAllViews()
            recentlySeenScrollView.removeAllViews()
            scheduleScrollView.removeAllViews()

            //val cardInfo = data?.homeSlidesData?.shuffled()?.take(1)?.get(0)
            /*val glideUrl = GlideUrl("https://fastani.net/" + cardInfo?.bannerImage) { FastAniApi.currentHeaders }
            context?.let {
                GlideApp.with(it)
                    .load(glideUrl)
                    .into(main_backgroundImage)
            }*/
            val random = data?.random?.data
            if (random != null) {

                val glideUrlMain =
                    GlideUrl(getFullUrl(random.image)) { FastAniApi.currentHeaders }
                context?.let {
                    GlideApp.with(it)
                        .load(glideUrlMain)
                        .into(main_poster)
                }

                main_name.text = random.name

                main_genres.text = random.genres?.joinToString(prefix = "", postfix = "", separator = " • ")

                /*main_watch_button.setOnClickListener {
                    //MainActivity.loadPage(cardInfo!!)
                    if (cardInfo != null) {
                        val nextEpisode = getNextEpisode(cardInfo)
                        loadPlayer(nextEpisode.episodeIndex, nextEpisode.seasonIndex, cardInfo)
                    }
                }
                main_watch_button.setOnLongClickListener {
                    //MainActivity.loadPage(cardInfo!!)
                    if (cardInfo != null) {
                        val nextEpisode = getNextEpisode(cardInfo)
                        Toast.makeText(
                            activity,
                            "Season ${nextEpisode.seasonIndex + 1} Episode ${nextEpisode.episodeIndex + 1}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@setOnLongClickListener true
                }*/
                main_info_button.setOnClickListener {
                    MainActivity.loadPage(random)
                }
            }
            //"http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

            /*main_poster.setOnClickListener {
                MainActivity.loadPage(cardInfo!!)
                // MainActivity.loadPlayer(0, 0, cardInfo!!)
            }*/

            fun displayCardData(data: List<FastAniApi.AnimePageData?>?, scrollView: RecyclerView) {
                val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = context?.let {
                    CardAdapter(
                        it,
                        ArrayList<FastAniApi.AnimePageData>(),
                        scrollView,
                    )
                }

                scrollView.adapter = adapter
                (scrollView.adapter as CardAdapter).cardList = data as ArrayList<FastAniApi.AnimePageData>
                (scrollView.adapter as CardAdapter).notifyDataSetChanged()
                /*data?.forEach { cardInfo ->
                    if (cardInfo != null) {
                        val card: View = layoutInflater.inflate(R.layout.home_card, null)
                        val glideUrl =
                            GlideUrl(getFullUrl(cardInfo.image))
                        //  activity?.runOnUiThread {
                        context?.let {
                            GlideApp.with(it)
                                .load(glideUrl)
                                .into(card.imageView)
                        }

                        card.imageText.text = cardInfo.name

                        card.home_card_root.setOnLongClickListener {
                            Toast.makeText(context, cardInfo?.name, Toast.LENGTH_SHORT).show()
                            return@setOnLongClickListener true
                        }
                        card.home_card_root.setOnClickListener {
                            activity?.supportFragmentManager?.beginTransaction()
                                ?.setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                                ?.add(R.id.homeRoot, ShiroResultFragment.newInstance(cardInfo))
                                ?.commit()

                        }
                        scrollView.addView(card)
                    }
                }*/
            }

            fun displayCardData(data: List<LastEpisodeInfo?>?, scrollView: LinearLayout) {
                data?.forEach { cardInfo ->
                    if (cardInfo != null) {
                        val card: View = layoutInflater.inflate(R.layout.home_recently_seen, null)
                        val epId = cardInfo.id

                        if (cardInfo.episode.thumb != null) {
                            val glideUrl =
                                GlideUrl(cardInfo.episode.thumb) { FastAniApi.currentHeaders }
                            //  activity?.runOnUiThread {
                            context?.let {
                                GlideApp.with(it)
                                    .load(glideUrl)
                                    .into(card.imageView)
                            }
                        }
                        card.cardDescription.text =
                            "S${cardInfo.seasonIndex + 1}:E${cardInfo.episodeIndex + 1} ${cardInfo.title.english}"
                        card.infoButton.setOnClickListener {
                            if (FastAniApi.lastCards.containsKey(epId)) {
                                //MainActivity.loadPage(FastAniApi.lastCards[epId]!!)
                            } else {
                                Toast.makeText(
                                    context,
                                    "Loading...",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        card.imageView.setOnLongClickListener {
                            Toast.makeText(
                                context,
                                cardInfo.title.english +
                                        if (!cardInfo.isMovie) "\nSeason ${cardInfo.seasonIndex + 1} Episode ${cardInfo.episodeIndex + 1}" else "",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@setOnLongClickListener true
                        }

                        card.imageView.setOnClickListener {
                            when {
                                FastAniApi.lastCards.containsKey(epId) -> {
                                    loadPlayer(
                                        cardInfo.episodeIndex,
                                        cardInfo.seasonIndex,
                                        FastAniApi.lastCards[epId]!!
                                    )
                                }
                                else -> {
                                    loadPlayer(cardInfo.episode.title, cardInfo.episode.file, cardInfo.pos)
                                }
                            }
                        }

                        card.removeButton.setOnClickListener {
                            DataStore.removeKey(VIEW_LST_KEY, cardInfo.aniListId)
                            requestHome(true)
                        }

                        if (cardInfo.dur > 0 && cardInfo.pos > 0) {
                            var progress: Int = (cardInfo.pos * 100L / cardInfo.dur).toInt()
                            if (progress < 5) {
                                progress = 5
                            } else if (progress > 95) {
                                progress = 100
                            }
                            card.video_progress.progress = progress
                        } else {
                            card.video_progress.alpha = 0f
                        }
                        scrollView.addView(card)
                    }
                }
            }


            if (data != null) {
                displayCardData(data.data.trending_animes, trending_anime_scroll_view)
                displayCardData(data.data.latest_animes, recently_updated_scroll_view)
                displayCardData(data.data.ongoing_animes, ongoing_anime_scroll_view)
            }
            /*displayCardData(data?.recentlyAddedData, recentScrollView)

            // RELOAD ON NEW FAV!
            if (data?.favorites?.isNotEmpty() == true) {
                favouriteRoot.visibility = VISIBLE
                //println(data.favorites!!.map { it?.title?.english})
                displayCardData(data.favorites, favouriteScrollView)
            } else {
                favouriteRoot.visibility = GONE
            }

            if (data?.schedule?.isNotEmpty() == true) {
                scheduleRoot.visibility = VISIBLE
                //println(data.favorites!!.map { it?.title?.english})
                displayCardData(data.schedule, scheduleScrollView)
            } else {
                scheduleRoot.visibility = GONE
            }

            val transition: Transition = ChangeBounds()
            transition.duration = 100
            if (data?.recentlySeen?.isNotEmpty() == true) {
                recentlySeenRoot.visibility = VISIBLE
                displayCardData(data.recentlySeen, recentlySeenScrollView)
            } else {
                recentlySeenRoot.visibility = GONE
            }
            TransitionManager.beginDelayedTransition(main_scroll, transition)
*/
            main_load.alpha = 0f
            main_scroll.alpha = 1f

            // This somehow crashes, hope this null check helps ¯\_(ツ)_/¯
            if (main_reload_data_btt != null) {
                main_reload_data_btt.alpha = 0f
                main_reload_data_btt.isClickable = false
            }
            main_layout.setPadding(0, MainActivity.statusHeight, 0, 0)
        }
    }

    private fun onHomeErrorCatch(fullRe: Boolean) {
        // Null check because somehow this can crash
        activity?.runOnUiThread {
            if (main_reload_data_btt != null) {
                main_reload_data_btt.alpha = 1f
                main_load.alpha = 0f
                main_reload_data_btt.isClickable = true
                main_reload_data_btt.setOnClickListener {
                    main_reload_data_btt.alpha = 0f
                    main_load.alpha = 1f
                    main_reload_data_btt.isClickable = false
                    thread {
                        if (fullRe) {
                            FastAniApi.init()
                        } else {
                            FastAniApi.requestHome(false)
                        }
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        main_scroll.alpha = 0f
        FastAniApi.onHomeError += ::onHomeErrorCatch
        if (FastAniApi.hasThrownError != -1) {
            onHomeErrorCatch(FastAniApi.hasThrownError == 1)
        }
        // CAUSES CRASH ON 6.0.0
        /*main_scroll.setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
               val fade = (FADE_SCROLL_DISTANCE - scrollY) / FADE_SCROLL_DISTANCE
               // COLOR ARGB INTRODUCED IN 26!
               val gray: Int = Color.argb(fade, 0f, fade, 0f)
            //   main_backgroundImage.alpha = maxOf(0f, MAXIMUM_FADE * fade) // DONT DUE TO ALPHA FADING HINDERING FORGOUND GRADIENT
        }*/
        homeViewModel.apiData.observe(viewLifecycleOwner) {
            println("OBSERVED")
            homeLoaded(it)
        }
    }
}
