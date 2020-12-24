package com.example.fastani.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.example.fastani.FastAniApi
import com.example.fastani.FastAniApi.Card
import com.example.fastani.R
import kotlinx.android.synthetic.main.search_result.view.*

class GridAdapter : BaseAdapter {
    var cardList = ArrayList<Card>()
    var context: Context? = null

    constructor(context: Context, foodsList: ArrayList<Card>) : super() {
        this.context = context
        this.cardList = foodsList
    }

    override fun getCount(): Int {
        return cardList.size
    }

    override fun getItem(position: Int): Any {
        return cardList[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val card = this.cardList[position]

        val inflator = context!!.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val cardView = inflator.inflate(R.layout.search_result, null)

        val glideUrl =
            GlideUrl("https://fastani.net/" + card.coverImage.large) { FastAniApi.currentHeaders }

        context?.let {
            Glide.with(it)
                .load(glideUrl)
                .into(cardView.imageView)
        }
        return cardView
    }
}