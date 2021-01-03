package com.lagradost.fastani.ui.downloads

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.widget.LinearLayoutCompat
import com.lagradost.fastani.*
import com.lagradost.fastani.MainActivity.Companion.isDonor
import kotlinx.android.synthetic.main.download_card.view.*
import kotlinx.android.synthetic.main.fragment_download.*
import java.io.File
import java.lang.Exception

class DownloadFragment : Fragment() {
    data class EpisodesDownloaded(
        val count: Int,
        val countDownloading: Int,
        val countBytes: Long,
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val topParams: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
            LinearLayoutCompat.LayoutParams.MATCH_PARENT, // view width
            MainActivity.statusHeight // view height
        )
        top_padding_download.layoutParams = topParams

        println("TTLLLL::: ")
        val inflator = requireContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        childMetadataKeys.clear()

        val epData = hashMapOf<String, EpisodesDownloaded>()
        try {
            val childKeys = DataStore.getKeys(DOWNLOAD_CHILD_KEY)

            downloadCenterText.text = if (isDonor) "Download something to make it show up here" else "Donate to download shows"
            downloadCenterRoot.visibility = if (childKeys.isEmpty()) VISIBLE else GONE

            for (k in childKeys) {
                val child = DataStore.getKey<DownloadManager.DownloadFileMetadata>(k)
                if (child != null) {
                    if (!File(child.videoPath).exists()) { // FILE DOESN'T EXIT
                        val thumbFile = File(child.thumbPath)
                        if (thumbFile.exists()) {
                            thumbFile.delete()
                        }
                        DataStore.removeKey(k)
                    } else {
                        if (childMetadataKeys.containsKey(child.anilistId)) {
                            childMetadataKeys[child.anilistId]?.add(k)
                        } else {
                            childMetadataKeys[child.anilistId] = mutableListOf<String>(k)
                        }

                        val id = child.anilistId
                        println("EPINDEX: " + child.episodeIndex)
                        val isDownloading =
                            DownloadManager.downloadStatus.containsKey(child.internalId) &&
                                    DownloadManager.downloadStatus[child.internalId] == DownloadManager.DownloadStatusType.IsDownloading

                        if (!epData.containsKey(id)) {
                            epData[id] = EpisodesDownloaded(1, if (isDownloading) 1 else 0, child.maxFileSize)
                        } else {
                            val current = epData[id]!!
                            epData[id] = EpisodesDownloaded(
                                current.count + 1,
                                current.countDownloading + (if (isDownloading) 1 else 0),
                                current.countBytes + child.maxFileSize
                            )
                        }
                    }
                }
            }

            val keys = DataStore.getKeys(DOWNLOAD_PARENT_KEY)
            for (k in keys) {
                val parent = DataStore.getKey<DownloadManager.DownloadParentFileMetadata>(k)
                if (parent != null) {
                    println("KEY::: " + k)
                    if (epData.containsKey(parent.anilistId)) {
                        val cardView = inflator.inflate(R.layout.download_card, null)

                        cardView.cardTitle.text = parent.title.english
                        cardView.imageView.setImageURI(Uri.parse(parent.coverImagePath))

                        val childData = epData[parent.anilistId]!!
                        val megaBytes = DownloadManager.convertBytesToAny(childData.countBytes, 0, 2.0).toInt()
                        cardView.cardInfo.text =
                            if (parent.isMovie) "$megaBytes MB" else
                                "${childData.count} Episode${(if (childData.count == 1) "" else "s")} | $megaBytes MB"

                        cardView.cardBg.setOnClickListener {
                            activity?.supportFragmentManager?.beginTransaction()
                                ?.setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                                ?.add(
                                    R.id.homeRoot, DownloadFragmentChild.newInstance(
                                        parent.anilistId
                                    )
                                )
                                ?.commit()
                            /*MainActivity.activity?.supportFragmentManager?.beginTransaction()
                                ?.setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                                ?.replace(
                                    R.id.homeRoot, DownloadFragmentChild(
                                        parent.anilistId
                                    )
                                )
                                ?.commit()*/
                        }

                        downloadRoot.addView(cardView)
                    } else {
                        val coverFile = File(parent.coverImagePath)
                        if (coverFile.exists()) {
                            coverFile.delete()
                        }
                        DataStore.removeKey(k)
                    }
                }
            }
        } catch (e: Exception) {
            println("ERROR LOADING DLOADS:::")
            e.printStackTrace()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = requireActivity().filesDir.toString() + "/Download/"
        File(path).walk().forEach {
            println("PATH: " + it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_download, container, false)
    }

    companion object {
        val childMetadataKeys = hashMapOf<String, MutableList<String>>()
    }
}