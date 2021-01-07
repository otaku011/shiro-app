package com.lagradost.fastani

import android.content.DialogInterface
import android.net.UrlQuerySanitizer
import androidx.appcompat.app.AlertDialog
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.fastani.MainActivity.Companion.activity

const val CLIENT_ID = "4636"
const val ACCOUNT_ID = "0" // MIGHT WANT TO BE USED IF YOU WANT MULTIPLE ACCOUNT LOGINS

class AniListApi {
    companion object {
        val aniListStatusString = arrayOf("CURRENT", "COMPLETED", "PAUSED", "DROPPED", "PLANNING", "REPEATING")

        private val mapper: JsonMapper = JsonMapper.builder().addModule(KotlinModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()

        fun fromInt(value: Int) = AniListStatusType.values().first { it.value == value }

        fun authenticate() {
            val request = "https://anilist.co/api/v2/oauth/authorize?client_id=$CLIENT_ID&response_type=token";
            MainActivity.openBrowser(request);
        }

        fun authenticateLogin(data: String) {
            try {
                val sanitizer = UrlQuerySanitizer(data)
                val token = sanitizer.getValue("access_token")
                val expiresIn = sanitizer.getValue("expires_in")
                val unixTime = System.currentTimeMillis() / 1000L
                val endTime = unixTime + expiresIn.toLong()

                DataStore.setKey(ANILIST_UNIXTIME_KEY, ACCOUNT_ID, endTime)
                DataStore.setKey(ANILIST_TOKEN_KEY, ACCOUNT_ID, token)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun checkToken(): Boolean {
            val unixTime = System.currentTimeMillis() / 1000L
            if (unixTime > DataStore.getKey(ANILIST_UNIXTIME_KEY, ACCOUNT_ID, 0L)!!) {
                val alertDialog: AlertDialog? = activity?.let {
                    val builder = AlertDialog.Builder(it)
                    builder.apply {
                        setPositiveButton("Login",
                            DialogInterface.OnClickListener { dialog, id ->
                                authenticate()
                            })
                        setNegativeButton("Cancel",
                            DialogInterface.OnClickListener { dialog, id ->
                                // User cancelled the dialog
                            })
                    }
                    // Set other dialog properties
                    builder.setTitle("AniList token has expired")

                    // Create the AlertDialog
                    builder.create()
                }
                alertDialog?.show()
                return true
            } else {
                return false
            }
        }

        private fun postApi(url: String, args: String): String {
            return try {
                if (!checkToken()) {
                    val response = khttp.post(url + args,
                        headers = mapOf("Authorization" to "Bearer " + DataStore.getKey(ANILIST_TOKEN_KEY,
                            ACCOUNT_ID,
                            "")!!))

                    response.text
                } else {
                    ""
                }
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
        }

        fun getDataAboutId(id: Int): AniListTitleHolder {
            val q: String =
                """query (${'$'}id: Int) { # Define which variables will be used in the query (id)
                Media (id: ${'$'}id, type: ANIME) { # Insert our variables into the query arguments (id) (type: ANIME is hard-coded in the query)
                    #id
                    isFavourite
                    mediaListEntry {
                        progress
                        status
                        score (format: POINT_10)
                    }
                }
                }&variables={ "id":"$id" }"""
            val data = postApi("https://graphql.anilist.co", "&query=$q")
            val d = mapper.readValue<GetDataRoot>(data)
            val main = d.data.media

            return AniListTitleHolder(
                id = id,
                isFavourite = main.isFavourite,
                progress = main.mediaListEntry.progress,
                score = main.mediaListEntry.score,
                type = fromInt(aniListStatusString.indexOf(main.mediaListEntry.status))
            )
        }

        fun toggleLike(id: Int): Boolean {
            val q: String = """mutation (${'$'}animeId: Int) {
				ToggleFavourite (animeId: ${'$'}animeId) {
					anime {
						nodes {
							id
							title {
								romaji
							}
						}
					}
				}
				}&variables={
					"animeId": $id
				}";"""
            val data = postApi("https://graphql.anilist.co", "&query=$q")
            return data != ""
        }

        fun postDataAboutId(id: Int, type: AniListStatusType, score: Int, progress: Int): Boolean {
            val q: String =
                """mutation (${'$'}id: Int, ${'$'}status: MediaListStatus, ${'$'}scoreRaw: Int, ${'$'}progress: Int) {
                SaveMediaListEntry (mediaId: ${'$'}id, status: ${'$'}status, scoreRaw: ${'$'}scoreRaw, progress: ${'$'}progress) {
                    id
                    status
                    progress
                    score
                }
                }&variables={
                    "id": $id,
                    "status":"${aniListStatusString[type.value]}",
                    "scoreRaw":${score * 10},
                    "progress":$progress
                }";"""
            val data = postApi("https://graphql.anilist.co", "&query=$q")
            return data != ""
        }

        fun getUser(setSettings: Boolean = true): AniListUser? {
            val q: String = """
				{
  					Viewer {
    					id
    					name
						avatar {
							large
						}
  					}
				}";"""
            val data = postApi("https://graphql.anilist.co", "&query=$q")
            if (data == "") return null
            val userData = mapper.readValue<AniListRoot>(data)
            val u = userData.data.Viewer
            val user = AniListUser(
                u.id,
                u.name,
                u.avatar.large,
            )
            if (setSettings) {
                DataStore.setKey(ANILIST_USER_KEY, ACCOUNT_ID, user)
            }
            return user
        }
    }

    enum class AniListStatusType(val value: Int) {
        Watching(0),
        Completed(1),
        OnHold(2),
        Dropped(3),
        PlanToWatch(4),
        ReWatching(5),
        None(-1)
    }


    data class AniListAvatar(
        val large: String,
    )

    data class AniListViewer(
        val id: Int,
        val name: String,
        val avatar: AniListAvatar,
    )

    data class AniListData(
        val Viewer: AniListViewer,
    )

    data class AniListRoot(
        val data: AniListData,
    )

    data class AniListUser(
        val id: Int,
        val name: String,
        val picture: String,
    )


    data class LikeNode(
        val id: Int,
        //public int idMal;
    )

    data class LikePageInfo(
        val total: Int,
        val currentPage: Int,
        val lastPage: Int,
        val perPage: Int,
        val hasNextPage: Boolean,
    )

    data class LikeAnime(
        val nodes: List<LikeNode>,
        val pageInfo: LikePageInfo,
    )

    data class LikeFavourites(
        val anime: LikeAnime,
    )

    data class LikeViewer(
        val favourites: LikeFavourites,
    )

    data class LikeData(
        val Viewer: LikeViewer,
    )

    data class LikeRoot(
        val data: LikeData,
    )

    data class AniListTitleHolder(
        val isFavourite: Boolean,
        val id: Int,
        val progress: Int,
        val score: Int,
        val type: AniListStatusType,
    )

    data class GetDataMediaListEntry(
        val progress: Int,
        val status: String,
        val score: Int,
    )

    data class GetDataMedia(
        val isFavourite: Boolean,
        val mediaListEntry: GetDataMediaListEntry,
    )

    data class GetDataData(
        val media: GetDataMedia,
    )

    data class GetDataRoot(
        val data: GetDataData,
    )
}