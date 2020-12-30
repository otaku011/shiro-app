package com.lagradost.fastani.ui.search

import android.annotation.SuppressLint
import android.os.Bundle
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
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.fastani.R
import com.lagradost.fastani.FastAniApi
import com.lagradost.fastani.MainActivity
import com.lagradost.fastani.toPx
import com.lagradost.fastani.ui.ResAdapter
import kotlinx.android.synthetic.main.fragment_search.*
import kotlin.concurrent.thread

class SearchFragment : Fragment() {

    private lateinit var searchViewModel: SearchViewModel

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val topParams: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
            LinearLayoutCompat.LayoutParams.MATCH_PARENT, // view width
            MainActivity.statusHeight // view height
        )
        top_padding.layoutParams = topParams

        progress_bar.visibility = View.GONE
        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = context?.let {
            ResAdapter(
                it,
                ArrayList<FastAniApi.Card>(),
                cardSpace,
            )
        }
        cardSpace.adapter = adapter

        main_search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                progress_bar.visibility = View.VISIBLE
                (cardSpace.adapter as ResAdapter).cardList.clear()

                thread {
                    val data = FastAniApi.search(query)
                    activity?.runOnUiThread {
                        progress_bar.visibility = View.GONE // GONE for remove space, INVISIBLE for just alpha = 0
                        (cardSpace.adapter as ResAdapter).cardList =
                            data?.animeData?.cards!! as ArrayList<FastAniApi.Card>
                        (cardSpace.adapter as ResAdapter).notifyDataSetChanged()
                    }
                }
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                println(newText)
                return true
            }
        })
        main_search.setOnQueryTextFocusChangeListener { view, b ->
            val searchParams: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
                LinearLayoutCompat.LayoutParams.MATCH_PARENT, // view width
                60.toPx // view height
            )
            val transition: Transition = ChangeBounds()
            transition.duration = 100 // DURATION OF ANIMATION IN MS

            TransitionManager.beginDelayedTransition(main_search, transition)

            val margins = if (b) 0 else 6.toPx
            searchParams.height -= margins * 2 // TO KEEP
            searchParams.setMargins(margins)
            main_search.layoutParams = searchParams
        }
        main_search.onActionViewExpanded()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        searchViewModel =
            ViewModelProviders.of(this).get(SearchViewModel::class.java)
        activity?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onStop() {
        super.onStop()
        activity?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )
    }
}
