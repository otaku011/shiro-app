package com.lagradost.shiro.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.shiro.FastAniApi
import com.lagradost.shiro.FastAniApi.Companion.getHome
import com.lagradost.shiro.FastAniApi.Companion.requestHome
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    val apiData = MutableLiveData<FastAniApi.HomePageResponse>().apply {
        FastAniApi.onHomeFetched += ::homeLoaded
    }

    private fun homeLoaded(data: FastAniApi.HomePageResponse?) {
        apiData.postValue(data!!)
    }
}