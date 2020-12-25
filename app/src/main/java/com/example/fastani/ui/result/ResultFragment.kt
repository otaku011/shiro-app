package com.example.fastani.ui.result

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import com.example.fastani.FastAniApi
import com.example.fastani.R
import kotlinx.android.synthetic.main.fragment_results.*

const val DESCRIPT_LENGTH = 100

class ResultFragment(data: FastAniApi.Card) : Fragment() {
    var data: FastAniApi.Card = data
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        title_name.text = data.title.english
        var descript = data.description
        if (descript.length > DESCRIPT_LENGTH) {
            descript = descript.substring(0, DESCRIPT_LENGTH) + "..."
        }
        title_descript.text = descript
        title_duration.text = data.duration.toString() + "min"
        title_rating.text = "Rated: " + (data.averageScore/10)
        title_genres.text = data.genres.joinToString(prefix = "", postfix = "", separator = " • ")

    }
}
