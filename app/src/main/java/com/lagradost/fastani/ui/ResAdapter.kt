package com.lagradost.fastani.ui

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
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.lagradost.fastani.*
import com.lagradost.fastani.FastAniApi.Card
import com.lagradost.fastani.FastAniApi.Companion.requestHome
import com.lagradost.fastani.MainActivity.Companion.activity
import com.lagradost.fastani.ui.result.ResultFragment
import kotlinx.android.synthetic.main.search_result.view.*
import kotlinx.android.synthetic.main.search_result.view.imageText
import kotlinx.android.synthetic.main.search_result.view.imageView
import kotlinx.android.synthetic.main.search_result_compact.view.*
import kotlin.concurrent.thread
import kotlin.math.roundToInt

val settingsManager = PreferenceManager.getDefaultSharedPreferences(activity)

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
        val compactView = settingsManager.getBoolean("compact_search_enabled", true)
        val layout = if (compactView) R.layout.search_result_compact else R.layout.search_result
        return CardViewHolder(
            LayoutInflater.from(parent.context).inflate(layout, parent, false),
            context!!,
            resView!!
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
        private val compactView = settingsManager.getBoolean("compact_search_enabled", true)
        val context = _context
        val cardView = itemView.imageView
        val coverHeight: Int = if (compactView) 80.toPx else (resView.itemWidth / 0.68).roundToInt()
        fun bind(card: Card) {
            if (compactView) {
                // COPIED -----------------------------------------
                var isBookmarked = DataStore.containsKey(BOOKMARK_KEY, card.anilistId)
                fun toggleHeartVisual(_isBookmarked: Boolean) {
                    if (_isBookmarked) {
                        itemView.title_bookmark.setImageResource(R.drawable.filled_heart)
                    } else {
                        itemView.title_bookmark.setImageResource(R.drawable.outlined_heart)
                    }
                }

                fun toggleHeart(_isBookmarked: Boolean) {
                    isBookmarked = _isBookmarked
                    toggleHeartVisual(_isBookmarked)
                    if (_isBookmarked) {
                        DataStore.setKey<BookmarkedTitle>(
                            BOOKMARK_KEY,
                            card.anilistId,
                            BookmarkedTitle(
                                card.id,
                                card.anilistId,
                                card.description,
                                card.title,
                                card.coverImage
                            )
                        )
                    } else {
                        DataStore.removeKey(BOOKMARK_KEY, card.anilistId)
                    }
                    thread {
                        requestHome(true)
                    }
                }
                toggleHeartVisual(isBookmarked)
                itemView.bookmark_holder.setOnClickListener {
                    toggleHeart(!isBookmarked)
                }
                // ------------------------------------------------
                itemView.backgroundCard.setOnClickListener {
                    activity?.supportFragmentManager?.beginTransaction()
                        ?.setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                        ?.add(R.id.homeRoot, ResultFragment.newInstance(card))
                        ?.commit()
                }
            }


            itemView.apply {
                layoutParams = LinearLayout.LayoutParams(
                    MATCH_PARENT,
                    coverHeight
                )
            }
            itemView.imageText.text = card.title.english
            cardView.setOnLongClickListener {
                Toast.makeText(context, card.title.english, Toast.LENGTH_SHORT).show()
                return@setOnLongClickListener true
            }

            cardView.setOnClickListener {
                activity?.supportFragmentManager?.beginTransaction()
                    ?.setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                    ?.add(R.id.homeRoot, ResultFragment.newInstance(card))
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