package com.lagradost.shiro.ui.home

import android.content.res.Configuration
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.shiro.ShiroApi
import com.lagradost.shiro.MainActivity.Companion.popCurrentPage
import com.lagradost.shiro.R
import kotlinx.android.synthetic.main.fragment_expanded_home.*

private const val CARD_LIST = "card_list"
private const val TITLE = "title"
private const val spanCountLandscape = 6
private const val spanCountPortrait = 3

class ExpandedHomeFragment : Fragment() {
    private var cardList: List<ShiroApi.AnimePageData?>? = null
    private var title: String? = null
    val mapper = JsonMapper.builder().addModule(KotlinModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            val cards = it.getString(CARD_LIST)
            cardList = cards?.let { it1 -> mapper.readValue(it1) }
            title = it.getString(TITLE)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_expanded_home, container, false)
    }

    companion object {
        var isInExpandedView: Boolean = false
        fun newInstance(cardList: String, title: String) =
            ExpandedHomeFragment().apply {
                arguments = Bundle().apply {
                    putString(CARD_LIST, cardList)
                    putString(TITLE, title)
                }
            }
    }

    override fun onDestroy() {
        isInExpandedView = false
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isInExpandedView = true
        title_go_back_holder.setOnClickListener {
            popCurrentPage()
        }
        val orientation = resources.configuration.orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            expanded_card_list_view.spanCount = spanCountLandscape
        } else {
            expanded_card_list_view.spanCount = spanCountPortrait
        }
        title_text.text = title
        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = context?.let {
            CardAdapter(
                it,
                ArrayList<ShiroApi.AnimePageData?>(),
                expanded_card_list_view,
            )
        }
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(activity)
        val hideDubbed = settingsManager.getBoolean("hide_dubbed", false)
        val filteredData = if (hideDubbed) cardList?.filter { it?.name?.endsWith("Dubbed") == false } else cardList
        expanded_card_list_view.adapter = adapter
        (expanded_card_list_view.adapter as CardAdapter).cardList = filteredData as ArrayList<ShiroApi.AnimePageData?>
        (expanded_card_list_view.adapter as CardAdapter).notifyDataSetChanged()

    }
}