package com.example.fastani.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fastani.FastAniApi
import com.example.fastani.FastAniApi.Companion.getHome
import com.example.fastani.FastAniApi.Companion.requestHome
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    val apiData = MutableLiveData<FastAniApi.HomePageResponse>().apply {
        FastAniApi.onHomeFetched += ::homeLoaded
    }

    private fun homeLoaded(data: FastAniApi.HomePageResponse?) {
        apiData.postValue(data!!)
    }
}