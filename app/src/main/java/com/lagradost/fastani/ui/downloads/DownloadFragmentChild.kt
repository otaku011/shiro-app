package com.lagradost.fastani.ui.downloads

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import com.google.android.gms.cast.framework.CastContext
import com.lagradost.fastani.*
import com.lagradost.fastani.MainActivity.Companion.getColorFromAttr
import com.lagradost.fastani.ui.PlayerData
import com.lagradost.fastani.ui.result.ResultFragment
import com.lagradost.fastani.ui.result.ResultFragment.Companion.fixEpTitle
import com.lagradost.fastani.ui.result.ResultFragment.Companion.isInResults
import kotlinx.android.synthetic.main.episode_result_downloaded.*
import kotlinx.android.synthetic.main.episode_result_downloaded.view.*
import kotlinx.android.synthetic.main.fragment_download_child.*
import kotlinx.android.synthetic.main.fragment_results.*
import kotlinx.android.synthetic.main.home_card.view.*
import kotlinx.android.synthetic.main.home_card.view.imageView
import kotlinx.android.synthetic.main.player_custom_layout.*
import java.io.File

import com.lagradost.fastani.MainActivity
import com.lagradost.fastani.ui.PlayerFragment


class DownloadFragmentChild() : Fragment() {
    var anilistId: String? = null
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isInResults = true
        arguments?.getString("anilist_id")?.let {
            anilistId = it
        }
        val topParams: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
            LinearLayoutCompat.LayoutParams.MATCH_PARENT, // view width
            MainActivity.statusHeight // view height
        )
        top_padding_download_child.layoutParams = topParams
        PlayerFragment.onLeftPlayer += ::onPlayerLeft
        download_go_back.setOnClickListener {
            MainActivity.popCurrentPage()
        }
        println("ANILIST: " + anilistId)
        loadData()
    }

    override fun onDestroy() {
        super.onDestroy()
        PlayerFragment.onLeftPlayer -= ::onPlayerLeft
        isInResults = false
    }

    fun onPlayerLeft(it: Boolean) {
        loadData()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun loadData() {
        downloadRootChild.removeAllViews()
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(MainActivity.activity)
        val save = settingsManager.getBoolean("save_history", true)

        // When fastani is down it doesn't report any seasons and this is needed.
        val episodeKeys = DownloadFragment.childMetadataKeys[anilistId]
        val parent = DataStore.getKey<DownloadManager.DownloadParentFileMetadata>(DOWNLOAD_PARENT_KEY, anilistId!!)
        download_header_text.text = parent?.title?.english

        for (k in episodeKeys!!) {
            val child = DataStore.getKey<DownloadManager.DownloadFileMetadata>(k)

            if (child != null) {
                val file = File(child.videoPath)
                if (!file.exists()) {
                    continue
                }

                val card: View = layoutInflater.inflate(R.layout.episode_result_downloaded, null)
                if (child.thumbPath != null) {
                    card.imageView.setImageURI(Uri.parse(child.thumbPath))
                }

                val key = MainActivity.getViewKey(anilistId!!, child.seasonIndex, child.episodeIndex)

                card.imageView.setOnClickListener {
                    val castContext = CastContext.getSharedInstance(activity!!.applicationContext)
                    println("SSTATE: " + castContext.castState + "<<")
                    if (save) {
                        DataStore.setKey<Long>(VIEWSTATE_KEY, key, System.currentTimeMillis())
                    }
                    MainActivity.loadPlayer(
                        PlayerData(
                            child.videoTitle,
                            child.videoPath,
                            child.episodeIndex,
                            child.seasonIndex,
                            null,
                            null,
                            anilistId
                        )
                    )
                    //MainActivity.loadPlayer(epIndex, index, data)
                }
                val title = fixEpTitle(
                    child.videoTitle, child.episodeIndex + 1, child.seasonIndex + 1,
                    parent?.isMovie == true, true
                )

                // ================ DOWNLOAD STUFF ================
                fun deleteFile() {
                    if (file.exists()) {
                        file.delete()
                    }
                    activity?.runOnUiThread {
                        card.visibility = GONE
                        DataStore.removeKey(k)
                        Toast.makeText(
                            context,
                            "${child.videoTitle} S${child.seasonIndex + 1}:E${child.episodeIndex + 1} deleted",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                card.cardRemoveIcon.setOnClickListener {
                    val alertDialog: AlertDialog? = activity?.let {
                        val builder = AlertDialog.Builder(it)
                        builder.apply {
                            setPositiveButton("Delete",
                                DialogInterface.OnClickListener { dialog, id ->
                                    deleteFile()
                                })
                            setNegativeButton("Cancel",
                                DialogInterface.OnClickListener { dialog, id ->
                                    // User cancelled the dialog
                                })
                        }
                        // Set other dialog properties
                        builder.setTitle("Delete ${child.videoTitle} - S${child.seasonIndex + 1}:E${child.episodeIndex + 1}")

                        // Create the AlertDialog
                        builder.create()
                    }
                    alertDialog?.show()
                }

                card.setOnLongClickListener {
                    if (ResultFragment.isViewState) {
                        if (DataStore.containsKey(VIEWSTATE_KEY, key)) {
                            DataStore.removeKey(VIEWSTATE_KEY, key)
                        } else {
                            DataStore.setKey<Long>(VIEWSTATE_KEY, key, System.currentTimeMillis())
                        }
                        loadData()
                    }
                    return@setOnLongClickListener true
                }

                card.cardTitle.text = title
                val megaBytesTotal = DownloadManager.convertBytesToAny(child.maxFileSize, 0, 2.0).toInt()
                val localBytesTotal = maxOf(DownloadManager.convertBytesToAny(file.length(), 0, 2.0).toInt(), 1)
                card.cardTitleExtra.text = "$localBytesTotal / $megaBytesTotal MB"

                fun updateIcon(megabytes: Int) {
                    if (megabytes + 3 >= megaBytesTotal) {
                        card.progressBar.visibility = View.GONE
                        card.cardPauseIcon.visibility = View.GONE
                        card.cardRemoveIcon.visibility = View.VISIBLE
                    } else {
                        card.progressBar.visibility = View.VISIBLE
                        card.cardRemoveIcon.visibility = View.GONE
                        card.cardPauseIcon.visibility = View.VISIBLE
                    }
                }

                fun getDownload(): DownloadManager.DownloadInfo {
                    return DownloadManager.DownloadInfo(
                        child.seasonIndex,
                        child.episodeIndex,
                        parent!!.title,
                        parent.isMovie,
                        child.anilistId,
                        child.fastAniId,
                        FastAniApi.FullEpisode(child.downloadFileUrl, child.videoTitle, child.thumbPath),
                        null
                    )
                }

                fun getStatus(): Boolean { // IF CAN RESUME
                    return if (DownloadManager.downloadStatus.containsKey(child.internalId)) {
                        DownloadManager.downloadStatus[child.internalId] == DownloadManager.DownloadStatusType.IsPaused
                    } else {
                        true
                    }
                }

                fun setStatus() {
                    activity?.runOnUiThread {
                        if (getStatus()) {
                            card.cardPauseIcon.setImageResource(R.drawable.netflix_play)
                        } else {
                            card.cardPauseIcon.setImageResource(R.drawable.exo_icon_stop)
                        }
                    }
                }

                setStatus()
                updateIcon(localBytesTotal)

                card.cardPauseIcon.setOnClickListener { v ->
                    val popup = PopupMenu(context, v)
                    if (getStatus()) {
                        popup.setOnMenuItemClickListener {
                            when (it.itemId) {
                                R.id.res_resumedload -> {
                                    DownloadManager.downloadEpisode(getDownload(), true)
                                }
                                R.id.res_stopdload -> {
                                    DownloadManager.invokeDownloadAction(
                                        child.internalId,
                                        DownloadManager.DownloadStatusType.IsStoped
                                    )
                                    deleteFile()
                                }
                            }
                            return@setOnMenuItemClickListener true
                        }
                        popup.inflate(R.menu.resume_menu)
                    } else {
                        popup.setOnMenuItemClickListener {
                            when (it.itemId) {
                                R.id.stop_pauseload -> {
                                    DownloadManager.invokeDownloadAction(
                                        child.internalId,
                                        DownloadManager.DownloadStatusType.IsPaused
                                    )
                                }
                                R.id.stop_stopdload -> {
                                    DownloadManager.invokeDownloadAction(
                                        child.internalId,
                                        DownloadManager.DownloadStatusType.IsStoped
                                    )
                                    deleteFile()
                                }
                            }
                            return@setOnMenuItemClickListener true
                        }
                        popup.inflate(R.menu.stop_menu)
                    }
                    popup.show()
                }

                card.progressBar.progress = maxOf(minOf(localBytesTotal * 100 / megaBytesTotal, 100), 0)

                DownloadManager.downloadPauseEvent += {
                    if (it == child.internalId) {
                        setStatus()
                    }
                }

                DownloadManager.downloadDeleteEvent += {
                    if (it == child.internalId) {
                        deleteFile()
                    }
                }

                DownloadManager.downloadEvent += {
                    activity?.runOnUiThread {
                        if (it.id == child.internalId) {
                            val megaBytes = DownloadManager.convertBytesToAny(it.bytes, 0, 2.0).toInt()
                            card.cardTitleExtra.text = "${megaBytes} / $megaBytesTotal MB"
                            card.progressBar.progress = maxOf(minOf(megaBytes * 100 / megaBytesTotal, 100), 0)
                            updateIcon(megaBytes)
                        }
                    }
                }

                // ================ REGULAR ================
                if (DataStore.containsKey(VIEWSTATE_KEY, key)) {
                    card.cardBg.setCardBackgroundColor(
                        requireContext().getColorFromAttr(
                            R.attr.colorPrimaryMegaDark
                        )
                    )
                }

                val pro = MainActivity.getViewPosDur(anilistId!!, child.seasonIndex, child.episodeIndex)
                if (pro.dur > 0 && pro.pos > 0) {
                    var progress: Int = (pro.pos * 100L / pro.dur).toInt()
                    if (progress < 5) {
                        progress = 5
                    } else if (progress > 95) {
                        progress = 100
                    }
                    card.video_progress.progress = progress
                } else {
                    card.video_progress.alpha = 0f
                }
                downloadRootChild.addView(card)
                downloadRootChild.invalidate()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_download_child, container, false)
    }


    companion object {
        fun newInstance(_anilistId: String) =
            DownloadFragmentChild().apply {
                arguments = Bundle().apply {
                    putString("anilist_id", _anilistId)
                }
            }
    }
}