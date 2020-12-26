package com.example.fastani.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.navigation.Navigation
import androidx.navigation.Navigation.findNavController
import androidx.navigation.findNavController
import androidx.navigation.findNavController
import androidx.navigation.ui.NavigationUI
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.example.fastani.FastAniApi
import com.example.fastani.FastAniApi.Card
import com.example.fastani.MainActivity
import com.example.fastani.MainActivity.Companion.activity
import com.example.fastani.R
import com.example.fastani.ui.result.ResultFragment
import kotlinx.android.synthetic.main.search_result.view.*
import kotlin.math.roundToInt

class ResAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder> {
    var cardList = ArrayList<Card>()
    var context: Context? = null
    var resView: AutofitRecyclerView? = null

    constructor(context: Context, foodsList: ArrayList<Card>, resView: AutofitRecyclerView) : super() {
        this.context = context
        this.cardList = foodsList
        this.resView = resView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return CardViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.search_result, parent, false), context!!, resView!!
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {

            is CardViewHolder -> {
                holder.bind(cardList[position])
            }

        }
    }

    override fun getItemCount(): Int {
        return cardList.size
    }

    class CardViewHolder
    constructor(itemView: View, _context: Context, resView: AutofitRecyclerView) : RecyclerView.ViewHolder(itemView) {
        val context = _context
        val cardView = itemView.imageView
        val coverHeight: Int = (resView.itemWidth / 0.68).roundToInt()
        fun bind(card: Card) {
            itemView.apply {
                layoutParams = LinearLayout.LayoutParams(
                    MATCH_PARENT,
                    coverHeight
                )
            }
            cardView.setOnLongClickListener {
                Toast.makeText(context, card.title.english, Toast.LENGTH_SHORT).show()
                return@setOnLongClickListener true
            }

            cardView.setOnClickListener {
                activity?.supportFragmentManager?.beginTransaction()
                    ?.add(R.id.homeRoot, ResultFragment(card))
                    ?.commit()
                 /*MainActivity.loadPage(card)*/
            }

            val glideUrl =
                GlideUrl("https://fastani.net/" + card.coverImage.large) { FastAniApi.currentHeaders }
            context.let {
                Glide.with(it)
                    .load(glideUrl)
                    .into(cardView)
            }
        }

    }
}