package com.lagradost.fastani.ui.home

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.view.*
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.AbsListView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import com.bumptech.glide.load.model.GlideUrl
import com.lagradost.fastani.*
import com.lagradost.fastani.DataStore.getSharedPrefs
import com.lagradost.fastani.FastAniApi.Companion.getCardById
import com.lagradost.fastani.FastAniApi.Companion.requestHome
import com.lagradost.fastani.ui.GlideApp
import com.lagradost.fastani.ui.PlayerData
import com.lagradost.fastani.ui.PlayerFragment
import kotlinx.android.synthetic.main.fragment_home.*
import kotlinx.android.synthetic.main.home_card.view.*
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
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    private fun homeLoaded(data: FastAniApi.HomePageResponse?) {
        activity?.runOnUiThread {

            trendingScrollView.removeAllViews()
            recentScrollView.removeAllViews()
            favouriteScrollView.removeAllViews()

            val cardInfo = data?.homeSlidesData?.get(0)
            val glideUrl = GlideUrl("https://fastani.net/" + cardInfo?.bannerImage) { FastAniApi.currentHeaders }
            context?.let {
                GlideApp.with(it)
                    .load(glideUrl)
                    .into(main_backgroundImage)
            }

            val glideUrlMain =
                GlideUrl("https://fastani.net/" + cardInfo?.coverImage?.large) { FastAniApi.currentHeaders }
            context?.let {
                GlideApp.with(it)
                    .load(glideUrlMain)
                    .into(main_poster)
            }

            main_name.text = cardInfo?.title?.english

            main_genres.text = cardInfo?.genres?.joinToString(prefix = "", postfix = "", separator = " • ")

            main_watch_button.setOnClickListener {
                MainActivity.loadPage(cardInfo!!)
            }

            //"http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
            main_poster.setOnClickListener {
                MainActivity.loadPage(cardInfo!!)
                // MainActivity.loadPlayer(0, 0, cardInfo!!)
            }
            fun displayCardData(data: List<BookmarkedTitle?>?, scrollView: LinearLayout) {
                data?.forEach { cardInfo ->
                    val card: View = layoutInflater.inflate(R.layout.home_card, null)
                    val glideUrl =
                        GlideUrl("https://fastani.net/" + cardInfo?.coverImage?.large) { FastAniApi.currentHeaders }
                    //  activity?.runOnUiThread {
                    context?.let {
                        GlideApp.with(it)
                            .load(glideUrl)
                            .into(card.imageView)
                    }
                    card.imageView.setOnLongClickListener {
                        Toast.makeText(context, cardInfo?.title?.english, Toast.LENGTH_SHORT).show()
                        return@setOnLongClickListener true
                    }
                    card.imageView.setOnClickListener {
                        val _id = cardInfo?.anilistId
                        if (FastAniApi.fullBookmarks.containsKey(_id)) {
                            if (cardInfo != null) {
                                MainActivity.loadPage(FastAniApi.fullBookmarks[_id]!!)
                            }
                        } else {
                            Toast.makeText(context, "Loading " + cardInfo?.title?.english + "... ", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                    scrollView.addView(card)
                }
            }

            fun displayCardData(data: List<FastAniApi.Card?>?, scrollView: LinearLayout) {
                data?.forEach { cardInfo ->
                    val card: View = layoutInflater.inflate(R.layout.home_card, null)
                    val glideUrl =
                        GlideUrl("https://fastani.net/" + cardInfo?.coverImage?.large) { FastAniApi.currentHeaders }
                    //  activity?.runOnUiThread {
                    context?.let {
                        GlideApp.with(it)
                            .load(glideUrl)
                            .into(card.imageView)
                    }
                    card.imageView.setOnLongClickListener {
                        Toast.makeText(context, cardInfo?.title?.english, Toast.LENGTH_SHORT).show()
                        return@setOnLongClickListener true
                    }
                    card.imageView.setOnClickListener {
                        if (cardInfo != null) {
                            MainActivity.loadPage(cardInfo)
                        }
                    }
                    scrollView.addView(card)
                }
            }
            displayCardData(data?.trendingData, trendingScrollView)
            displayCardData(data?.recentlyAddedData, recentScrollView)

            // RELOAD ON NEW FAV!
            if (data?.favorites?.isNotEmpty() == true) {
                favouriteRoot.visibility = VISIBLE
                //println(data.favorites!!.map { it?.title?.english})
                displayCardData(data.favorites, favouriteScrollView)
            } else {
                favouriteRoot.visibility = GONE
            }
            main_load.alpha = 0f
            main_scroll.alpha = 1f
            main_layout.setPadding(0, MainActivity.statusHeight, 0, 0)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        main_scroll.alpha = 0f

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
