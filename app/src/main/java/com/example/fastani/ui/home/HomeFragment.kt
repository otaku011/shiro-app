package com.example.fastani.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.beust.klaxon.Klaxon
import com.example.fastani.*
import kotlin.concurrent.thread

data class HomePageResponse(
    val animeData: AnimeData,
    val homeSlidesData: List<Card>,
    val recentlyAddedData: List<Card>,
    val trendingData: List<Card>
)

class HomeFragment : Fragment() {

    private lateinit var homeViewModel: HomeViewModel

    private fun getHome(token: Token): HomePageResponse? {
        val url = "https://fastani.net/api/data"
        val response = khttp.get(url, headers=token.headers, cookies=token.cookies)
        println(token)
        println(response.text)
        val parsed = Klaxon().parse<HomePageResponse>(response.text)
        println(parsed)
        return parsed
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        homeViewModel =
            ViewModelProviders.of(this).get(HomeViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_home, container, false)
        val textView: TextView = root.findViewById(R.id.text_home)
        homeViewModel.text.observe(viewLifecycleOwner, Observer {
            textView.text = "Home coming soon :)"
        })
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        thread{
            //val token = getToken()
            //getHome(token)
        }
    }
}