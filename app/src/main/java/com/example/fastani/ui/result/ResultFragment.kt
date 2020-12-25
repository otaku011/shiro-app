package com.example.fastani.ui.result

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.example.fastani.FastAniApi
import com.example.fastani.MainActivity
import com.example.fastani.R
import com.example.fastani.toPx
import com.example.fastani.ui.GlideApp
import kotlinx.android.synthetic.main.episode_result.view.*
import kotlinx.android.synthetic.main.fragment_dashboard.*
import kotlinx.android.synthetic.main.fragment_home.*
import kotlinx.android.synthetic.main.fragment_results.*
import kotlinx.android.synthetic.main.home_card.view.*
import kotlinx.android.synthetic.main.home_card.view.imageView
import kotlinx.android.synthetic.main.search_result.view.*

const val DESCRIPT_LENGTH = 200

class ResultFragment(data: FastAniApi.Card) : Fragment() {
    var data: FastAniApi.Card = data
    val isMovie: Boolean = data.episodes == 1 && data.status == "FINISHED"
    private lateinit var resultViewModel: ResultViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        resultViewModel =
            activity?.let { ViewModelProviders.of(it).get(ResultViewModel::class.java) }!!
        return inflater.inflate(R.layout.fragment_results, container, false)
    }

    fun loadSeason(index: Int) {
        var epNum = 0
        data.cdnData.seasons[index].episodes.forEach {

            val card: View = layoutInflater.inflate(R.layout.episode_result, null)
            if (it.thumb != null) {
                val glideUrl = GlideUrl(it.thumb)
                context?.let {
                    Glide.with(it)
                        .load(glideUrl)
                        .into(card.imageView)
                }
            }

            val epIndex = epNum
            card.imageView.setOnClickListener {
                MainActivity.loadPlayer(epIndex, index, data)
            }

            epNum++
            var title = it.title
            if (title == null || title?.replace(" ", "") == "") {
                title = "Episode " + epNum
            }
            if(!isMovie) {
                title = epNum.toString() + ". " + title
            }
            card.cardTitle.text = title
            title_season_cards.addView(card)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.setOnTouchListener { _, _ -> return@setOnTouchListener true } // VERY IMPORTANT https://stackoverflow.com/questions/28818926/prevent-clicking-on-a-button-in-an-activity-while-showing-a-fragment

        title_holder.setPadding(
            title_holder.paddingLeft,
            MainActivity.statusHeight + title_background.minimumHeight - 44.toPx,
            title_holder.paddingRight,
            0,
        )
        val glideUrl =
            GlideUrl("https://fastani.net/" + data.bannerImage) { FastAniApi.currentHeaders }

        if (data.trailer != null) {
            title_background.setOnLongClickListener {
                Toast.makeText(context, data.title.english + " - Trailer", Toast.LENGTH_SHORT).show()
                return@setOnLongClickListener true
            }

            title_background.setOnClickListener() {
                //TODO headers for trailers
                MainActivity.loadPlayer(data.title.english + " - Trailer", "https://fastani.net/" + data.trailer!!)
            }
        } else {
            title_trailer_btt.alpha = 0f
        }

        var seasonsTxt: MutableList<String> = mutableListOf<String>()
        for (i in 1..data.cdnData.seasons.size) {
            seasonsTxt.add("Season " + i)
        }
        loadSeason(0)
        /*
        val arrayAdapter = ArrayAdapter(context, R.layout.s,    arrayOf("Season 1", "season 2") )
        title_seasons.adapter = arrayAdapter*/

        context?.let {
            GlideApp.with(it)
                .load(glideUrl)
                .into(title_background)
        }

        title_name.text = data.title.english
        var descript = data.description
        if (descript.length > DESCRIPT_LENGTH) {
            descript = descript.substring(0, DESCRIPT_LENGTH)
                .replace("<br>", "")
                .replace("<i>", "")
                .replace("</i>", "")
                .replace("\n", " ") + "..."
        }
        title_descript.text = descript
        title_duration.text = data.duration.toString() + "min"
        var ratTxt = (data.averageScore / 10f).toString().replace(',', '.') // JUST IN CASE DUE TO LANG SETTINGS
        if (!ratTxt.contains('.')) ratTxt += ".0"
        title_rating.text = "Rated: " + ratTxt
        title_genres.text = data.genres.joinToString(prefix = "", postfix = "", separator = "  ") //  •

    }
}
