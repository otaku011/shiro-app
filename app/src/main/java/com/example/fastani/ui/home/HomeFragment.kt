package com.example.fastani.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import com.bumptech.glide.load.model.GlideUrl
import com.example.fastani.*
import com.example.fastani.FastAniApi.Companion.requestHome
import com.example.fastani.ui.GlideApp
import kotlinx.android.synthetic.main.fragment_home.*
import kotlinx.android.synthetic.main.home_card.view.*
import kotlin.concurrent.thread


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
        }

        data?.trendingData?.forEach { cardInfo ->
            val card: View = layoutInflater.inflate(R.layout.home_card, null)
            val glideUrl =
                GlideUrl("https://fastani.net/" + cardInfo.coverImage.large) { FastAniApi.currentHeaders }
            activity?.runOnUiThread {
                context?.let {
                    GlideApp.with(it)
                        .load(glideUrl)
                        .into(card.imageView)
                }
                card.imageView.setOnLongClickListener {
                    Toast.makeText(context, cardInfo.title.english, Toast.LENGTH_SHORT).show()
                    return@setOnLongClickListener true
                }
                trendingScrollView.addView(card)
            }
        }
        data?.recentlyAddedData?.forEach { cardInfo ->
            val card: View = layoutInflater.inflate(R.layout.home_card, null)
            val glideUrl =
                GlideUrl("https://fastani.net/" + cardInfo.coverImage.large) { FastAniApi.currentHeaders }
            activity?.runOnUiThread {
                context?.let {
                    GlideApp.with(it)
                        .load(glideUrl)
                        .into(card.imageView)
                }
                card.imageView.setOnLongClickListener {
                    Toast.makeText(context, cardInfo.title.english, Toast.LENGTH_SHORT).show()
                    return@setOnLongClickListener true
                }
                recentScrollView.addView(card)
            }
        }
    }

    override fun onDestroy() {
        FastAniApi.onHomeFetched -= ::homeLoaded;
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        homeViewModel.apiData.observe(viewLifecycleOwner) {
            homeLoaded(it)
        }
        /*FastAniApi.onHomeFetched += ::homeLoaded;
        thread {
            // NOTE THAT THIS WILL RESULT IN NOTHING ON FIRST LOAD BECAUSE TOKEN IS NOT LAODED
            requestHome(true)
        }*/
    }
}