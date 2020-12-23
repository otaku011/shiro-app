package com.example.fastani.ui.dashboard

import android.annotation.SuppressLint
import android.content.res.Resources
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
import com.example.fastani.R
import com.example.fastani.FastAniApi
import com.example.fastani.ui.GridAdapter
import kotlinx.android.synthetic.main.fragment_dashboard.*
import kotlin.concurrent.thread

val Int.toPx: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()
val Int.toDp: Int get() = (this / Resources.getSystem().displayMetrics.density).toInt()

class DashboardFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    private lateinit var dashboardViewModel: DashboardViewModel

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        progress_bar.visibility = View.GONE

        main_search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                progress_bar.visibility = View.VISIBLE;
                thread {
                    val data = FastAniApi.search(query)
                    activity?.runOnUiThread{
                        progress_bar.visibility = View.GONE // GONE for remove space, INVISIBLE for just alpha = 0
                    }
                    val adapter = context?.let {
                        GridAdapter(
                            it,
                            data?.animeData?.cards!! as ArrayList<FastAniApi.Card>
                        )
                    }
                    cardSpace.adapter = adapter
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
