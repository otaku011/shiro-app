package com.example.fastani.ui.dashboard

import android.annotation.SuppressLint
import android.content.res.Resources
import android.os.Bundle
import android.os.Handler
import android.transition.ChangeBounds
import android.transition.Transition
import android.transition.TransitionManager
import android.view.*
import android.widget.LinearLayout
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.appcompat.widget.SearchView
import androidx.core.view.setMargins
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.transition.AutoTransition
import androidx.transition.TransitionSet
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.Headers
import com.example.fastani.R
import com.example.fastani.FastAniApi
import kotlinx.android.synthetic.main.fragment_dashboard.*
import kotlinx.android.synthetic.main.search_result.view.*
import kotlin.concurrent.thread

val Int.toPx: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()
val Int.toDp: Int get() = (this / Resources.getSystem().displayMetrics.density).toInt()

class DashboardFragment : Fragment() {

    private lateinit var dashboardViewModel: DashboardViewModel

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        progress_bar.setVisibility(View.GONE)

        main_search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                progress_bar.setVisibility(View.VISIBLE);
                cardSpace.removeAllViews()
                thread {
                    val data = FastAniApi.search(query)
                    activity?.runOnUiThread{
                        progress_bar.setVisibility(View.GONE) // GONE for remove space, INVISIBLE for just alpha = 0
                    }
                    data?.animeData?.cards?.forEach {
                        val card: View = layoutInflater.inflate(R.layout.search_result, null)
                        val glideUrl =
                            GlideUrl("https://fastani.net/" + it.coverImage.large) { FastAniApi.currentHeaders }
                        activity?.runOnUiThread {
                            context?.let {
                                Glide.with(it)
                                    .load(glideUrl)
                                    .into(card.imageView)
                            }
                            card.cardTitle.text = it.title.english
                            card.cardDescript.text = it.description
                            cardSpace.addView(card)
                        }
                    }

                }
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                println(newText);
                return true
            }
        })
        main_search.setOnQueryTextFocusChangeListener { view, b ->
            val searchParams: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
                LinearLayoutCompat.LayoutParams.MATCH_PARENT, // view width
                60.toPx // view height
            )
            val transition: Transition = ChangeBounds();
            transition.duration = 50 // DURATION OF ANIMATION IN MS

            TransitionManager.beginDelayedTransition(main_search, transition)

            val margins = if (b) 0 else 5.toPx
            searchParams.height -= margins
            searchParams.setMargins(margins)
            main_search.layoutParams = searchParams
        }
        main_search.onActionViewExpanded()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        dashboardViewModel =
            ViewModelProviders.of(this).get(DashboardViewModel::class.java)


        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }
}
