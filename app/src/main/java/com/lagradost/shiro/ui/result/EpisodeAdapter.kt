package com.lagradost.shiro.ui.result

import android.content.Context
import android.content.DialogInterface
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.*
import androidx.recyclerview.widget.RecyclerView
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.cast.CastPlayer
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.common.images.WebImage
import com.lagradost.shiro.*
import com.lagradost.shiro.FastAniApi.Companion.getFullUrlCdn
import com.lagradost.shiro.FastAniApi.Companion.getVideoLink
import com.lagradost.shiro.MainActivity.Companion.activity
import com.lagradost.shiro.MainActivity.Companion.getColorFromAttr
import com.lagradost.shiro.MainActivity.Companion.isCastApiAvailable
import com.lagradost.shiro.ui.AutofitRecyclerView
import kotlinx.android.synthetic.main.download_card.view.*
import kotlinx.android.synthetic.main.episode_result_compact.view.*
import kotlinx.android.synthetic.main.episode_result_compact.view.cardBg
import kotlinx.android.synthetic.main.episode_result_compact.view.cardTitle
import kotlinx.android.synthetic.main.home_card.view.*
import java.io.File
import java.lang.Math.abs
import kotlin.concurrent.thread


class EpisodeAdapter(
    val context: Context,
    val data: FastAniApi.AnimePageData,
    val resView: AutofitRecyclerView,
    val save: Boolean
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var episodes = data.episodes
    var prevFocus: Int? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return CardViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.episode_result_compact, parent, false),
            context,
            resView,
            save
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        holder.itemView.setOnFocusChangeListener { focusedView, hasFocus ->
            if (prevFocus != null) {
                if (kotlin.math.abs(position - prevFocus!!) > 3 * 2) {
                    this.resView.layoutManager?.scrollToPosition(0)
                }

            }
            prevFocus = position
            //updateFocusPositions(holder, hasFocus, position)
        }

        when (holder) {
            is CardViewHolder -> {
                holder.bind(data, position)
            }
        }
    }

    override fun getItemCount(): Int {
        return episodes!!.size
    }

    class CardViewHolder
    constructor(itemView: View, _context: Context, resView: RecyclerView, val save: Boolean) :
        RecyclerView.ViewHolder(itemView) {

        val context = _context
        val card: LinearLayout = itemView.episode_result_root
        fun bind(data: FastAniApi.AnimePageData?, position: Int) {
            val key = data!!._id + position//MainActivity.getViewKey(data!!.url, index, epIndex)

            /*DownloadManager.downloadEpisode(
                        DownloadManager.DownloadInfo(
                            index,
                            epIndex,
                            data!!.title,
                            isMovie,
                            data!!.anilistId,
                            data!!.id,
                            data!!.cdnData.seasons[index].episodes[epIndex],
                            data!!.coverImage.large
                        )
                    )*/

            card.cdi.visibility = View.GONE
            val param = card.cardTitle.layoutParams as ViewGroup.MarginLayoutParams
            param.updateMarginsRelative(
                card.cardTitle.marginLeft,
                card.cardTitle.marginTop,
                10.toPx,
                card.cardTitle.marginBottom
            )
            card.cardTitle.layoutParams = param

            itemView.cardBg.setOnClickListener {
                //Toast.makeText(activity, "Loading link (Don't press shit!)", Toast.LENGTH_LONG).show()
                if (save) {
                    DataStore.setKey<Long>(VIEWSTATE_KEY, key, System.currentTimeMillis())
                }

                if (isCastApiAvailable()) {
                    val castContext = CastContext.getSharedInstance(activity!!.applicationContext)
                    println("SSTATE: " + castContext.castState + "<<")
                    if (castContext.castState == CastState.CONNECTED) {
                        castEpisode(data, position)
                    } else {
                        thread {
                            MainActivity.loadPlayer(position, 0L, data)
                        }
                    }
                } else {
                    thread {
                        MainActivity.loadPlayer(position, 0L, data)
                    }
                }
            }

            /*card.cardBg.setOnLongClickListener {
                if (isViewState) {
                    if (DataStore.containsKey(VIEWSTATE_KEY, key)) {
                        DataStore.removeKey(VIEWSTATE_KEY, key)
                    } else {
                        DataStore.setKey<Long>(VIEWSTATE_KEY, key, System.currentTimeMillis())
                    }
                }
                return@setOnLongClickListener true
            }*/

            val title = "Episode ${position + 1}"

            card.cardTitle.text = title
            if (DataStore.containsKey(VIEWSTATE_KEY, key)) {
                activity?.let {
                    card.cardBg.setCardBackgroundColor(
                        it.getColorFromAttr(
                            R.attr.colorPrimaryMegaDark
                        )
                    )
                }
            }

            val pro = MainActivity.getViewPosDur(data._id, 0, position)
            //println("DURPOS:" + epNum + "||" + pro.pos + "|" + pro.dur)
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

            if (MainActivity.isDonor) {
                val internalId = (data._id + "E${position}").hashCode()
                val child = DataStore.getKey<DownloadManager.DownloadFileMetadata>(
                    DOWNLOAD_CHILD_KEY,
                    internalId.toString()
                )
                // ================ DOWNLOAD STUFF ================
                if (child != null) {
                    println("CHILD NOT NULL:" + position)
                    val file = File(child.videoPath)
                    if (file.exists()) {
                        val megaBytesTotal = DownloadManager.convertBytesToAny(child.maxFileSize, 0, 2.0).toInt()
                        val localBytesTotal =
                            maxOf(DownloadManager.convertBytesToAny(file.length(), 0, 2.0).toInt(), 1)

                        fun updateIcon(megabytes: Int) {
                            if (!file.exists()) {
                                card.cdi.visibility = View.VISIBLE
                                card.progressBar.visibility = View.GONE
                                card.cardPauseIcon.visibility = View.GONE
                                card.cardRemoveIcon.visibility = View.GONE
                            } else {
                                card.cdi.visibility = View.GONE
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
                        }

                        println("FILE EXISTS:" + position)
                        fun deleteFile() {
                            if (file.exists()) {
                                file.delete()
                            }
                            activity?.runOnUiThread {
                                DataStore.removeKey(DOWNLOAD_CHILD_KEY, key)
                                Toast.makeText(
                                    context,
                                    "${child.videoTitle} S${child.seasonIndex + 1}:E${child.episodeIndex + 1} deleted",
                                    Toast.LENGTH_LONG
                                ).show()
                                updateIcon(0)
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

                        card.cardTitle.text = title

                        //card.cardTitleExtra.text = "$localBytesTotal / $megaBytesTotal MB"


                        fun getDownload(): DownloadManager.DownloadInfo {
                            return DownloadManager.DownloadInfo(
                                child.seasonIndex,
                                child.episodeIndex,
                                FastAniApi.Title(data.name, data.name, data.name),
                                false,
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
                                                DownloadManager.DownloadStatusType.IsStopped
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
                                                DownloadManager.DownloadStatusType.IsStopped
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
                                    //card.cardTitleExtra.text = "${megaBytes} / $megaBytesTotal MB"
                                    card.progressBar.progress = maxOf(
                                        minOf(megaBytes * 100 / megaBytesTotal, 100),
                                        0
                                    )
                                    updateIcon(megaBytes)
                                }
                            }
                        }
                        // END DOWNLOAD
                    }
                }
            }

        }

        fun castEpisode(data: FastAniApi.AnimePageData, episodeIndex: Int) {
            val castContext = CastContext.getSharedInstance(activity!!.applicationContext)
            castContext.castOptions
            val key = data._id + episodeIndex//MainActivity.getViewKey(data!!.anilistId, seasonIndex, episodeIndex)
            thread {
                val videoLink = data.episodes?.get(episodeIndex)?.videos?.getOrNull(0)?.video_id.let { it1 ->
                    getVideoLink(
                        it1!!
                    )
                }
                println("LINK $videoLink")
                if (videoLink != null) {
                    activity!!.runOnUiThread {

                        val movieMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE)
                        movieMetadata.putString(
                            MediaMetadata.KEY_TITLE,
                            "Episode ${episodeIndex + 1}"
                        )
                        movieMetadata.putString(MediaMetadata.KEY_ALBUM_ARTIST, data.name)
                        movieMetadata.addImage(WebImage(Uri.parse(getFullUrlCdn(data.image))))

                        val mediaInfo = MediaInfo.Builder(videoLink)
                            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                            .setContentType(MimeTypes.VIDEO_UNKNOWN)
                            .setMetadata(movieMetadata).build()

                        val mediaItems = arrayOf(MediaQueueItem.Builder(mediaInfo).build())
                        val castPlayer = CastPlayer(castContext)

                        castPlayer.loadItems(
                            mediaItems,
                            0,
                            DataStore.getKey<Long>(VIEW_POS_KEY, key, 0L)!!,
                            Player.REPEAT_MODE_OFF
                        )
                    }


                    /*castPlayer.setSessionAvailabilityListener(object : SessionAvailabilityListener {
                        override fun onCastSessionAvailable() {

                        }

                        override fun onCastSessionUnavailable() {}
                    })*/
                }
            }
        }
    }

}

