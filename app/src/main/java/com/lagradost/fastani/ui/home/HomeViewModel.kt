package com.lagradost.fastani.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.fastani.FastAniApi
import com.lagradost.fastani.FastAniApi.Companion.getHome
import com.lagradost.fastani.FastAniApi.Companion.requestHome
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    val apiData = MutableLiveData<FastAniApi.HomePageResponse>().apply {
        FastAniApi.onHomeFetched += ::homeLoaded
    }

    private fun homeLoaded(data: FastAniApi.HomePageResponse?) {
        apiData.postValue(data!!)
    }
}