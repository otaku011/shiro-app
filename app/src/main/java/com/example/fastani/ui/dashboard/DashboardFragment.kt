package com.example.fastani.ui.dashboard

import android.annotation.SuppressLint
import android.content.res.Resources
import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.example.fastani.R
import kotlinx.android.synthetic.main.fragment_dashboard.*

val Int.toPx: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()
val Int.toDp: Int get() = (this / Resources.getSystem().displayMetrics.density).toInt()

class DashboardFragment : Fragment() {

    private lateinit var dashboardViewModel: DashboardViewModel

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        main_search.onActionViewExpanded()
        val roomParams: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
            LinearLayoutCompat.LayoutParams.WRAP_CONTENT, // view width
            LinearLayoutCompat.LayoutParams.WRAP_CONTENT // view height
        )
        roomParams.gravity = Gravity.CENTER_VERTICAL or Gravity.END
        roomParams.height = 120.toPx
        main_search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                print(newText);
                return true
            }
        })
        main_search.layoutParams = roomParams;
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        dashboardViewModel =
                ViewModelProviders.of(this).get(DashboardViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_dashboard, container, false)

        return root
    }
}
