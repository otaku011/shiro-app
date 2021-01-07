package com.lagradost.fastani

import android.content.DialogInterface
import android.net.UrlQuerySanitizer
import androidx.appcompat.app.AlertDialog
import com.fasterxml.jackson.annotation.JsonProperty
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

        val mapper = JsonMapper.builder().addModule(KotlinModule())
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
                    val response = khttp.post(
                        url + args,
                        headers = mapOf(
                            "Authorization" to "Bearer " + DataStore.getKey(
                                ANILIST_TOKEN_KEY,
                                ACCOUNT_ID,
                                ""
                            )!!
                        )
                    )

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

        fun getSeason(id: Int): SeasonResponse? {
            val q: String = """
               query (${'$'}id: Int) {
                   Media (id: ${'$'}id, type: ANIME) {
                       relations {
                            edges {
                                 id
                                 relationType(version: 2)
                                 node {
                                      id
                                      format
                                      nextAiringEpisode {
                                           timeUntilAiring
                                           episode
                                      }
                                 }
                            }
                       }
                       nextAiringEpisode {
                            timeUntilAiring
                            episode
                       }
                       format
                   }
               }
        """

            val data = khttp.post(
                "https://graphql.anilist.co",
                data = mapOf("query" to q, "variables" to mapOf("id" to "$id"))
            ).text
            println(data)
            if (data == "") return null
            return mapper.readValue(data)
        }

        /*fun getAllSeasons(id: Int): List<SeasonData?> {
            val seasons = mutableListOf<SeasonData?>()
            fun getSeasonRecursive(id: Int) {
                val season = getSeason(id)
                if (season != null) {
                    seasons.add(season)
                    if (season.data.Media.format == "TV") {
                        season.data.Media.relations.forEach {
                            if (it.relationType == "SEQUEL" && it.node.format == "TV") {
                                return getSeasonRecursive(it.node.id)
                            }
                        }
                    }
                }
            }
            getSeasonRecursive(id)
            return seasons.toList()
        }*/
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

    data class SeasonResponse(
        @JsonProperty("data") val data: SeasonData
    )

    data class SeasonData(
        @JsonProperty("Media") val Media: SeasonMedia
    )

    data class SeasonMedia(
        @JsonProperty("format") val format: String?,
        @JsonProperty("nextAiringEpisode") val nextAiringEpisode: SeasonNextAiringEpisode?,
        @JsonProperty("relations") val relations: List<SeasonEdges>,
    )

    data class SeasonNextAiringEpisode(
        @JsonProperty("episode") val episode: Int,
        @JsonProperty("timeUntilAiring") val timeUntilAiring: Int
    )

    data class SeasonEdges(
        @JsonProperty("id") val id: Int,
        @JsonProperty("relationType") val relationType: String,
        //@JsonProperty("node") val node: SeasonNode
    )

    data class SeasonNode(
        @JsonProperty("id") val id: Int,
        @JsonProperty("format") val format: String,
        @JsonProperty("nextAiringEpisode") val nextAiringEpisode: SeasonNextAiringEpisode?
    )

    data class AniListAvatar(
        @JsonProperty("large") val large: String,
    )

    data class AniListViewer(
        @JsonProperty("id") val id: Int,
        @JsonProperty("name") val name: String,
        @JsonProperty("avatar") val avatar: AniListAvatar,
    )

    data class AniListData(
        @JsonProperty("Viewer") val Viewer: AniListViewer,
    )

    data class AniListRoot(
        @JsonProperty("data") val data: AniListData,
    )

    data class AniListUser(
        @JsonProperty("id") val id: Int,
        @JsonProperty("name") val name: String,
        @JsonProperty("picture") val picture: String,
    )


    data class LikeNode(
        @JsonProperty("id") val id: Int,
        //@JsonProperty("idMal") public int idMal;
    )

    data class LikePageInfo(
        @JsonProperty("total") val total: Int,
        @JsonProperty("currentPage") val currentPage: Int,
        @JsonProperty("lastPage") val lastPage: Int,
        @JsonProperty("perPage") val perPage: Int,
        @JsonProperty("hasNextPage") val hasNextPage: Boolean,
    )

    data class LikeAnime(
        @JsonProperty("nodes") val nodes: List<LikeNode>,
        @JsonProperty("pageInfo") val pageInfo: LikePageInfo,
    )

    data class LikeFavourites(
        @JsonProperty("anime") val anime: LikeAnime,
    )

    data class LikeViewer(
        @JsonProperty("favourites") val favourites: LikeFavourites,
    )

    data class LikeData(
        @JsonProperty("Viewer") val Viewer: LikeViewer,
    )

    data class LikeRoot(
        @JsonProperty("data") val data: LikeData,
    )

    data class AniListTitleHolder(
        @JsonProperty("isFavourite") val isFavourite: Boolean,
        @JsonProperty("id") val id: Int,
        @JsonProperty("progress") val progress: Int,
        @JsonProperty("score") val score: Int,
        @JsonProperty("type") val type: AniListStatusType,
    )

    data class GetDataMediaListEntry(
        @JsonProperty("progress") val progress: Int,
        @JsonProperty("status") val status: String,
        @JsonProperty("score") val score: Int,
    )

    data class GetDataMedia(
        @JsonProperty("isFavourite") val isFavourite: Boolean,
        @JsonProperty("mediaListEntry") val mediaListEntry: GetDataMediaListEntry,
    )

    data class GetDataData(
        @JsonProperty("media") val media: GetDataMedia,
    )

    data class GetDataRoot(
        @JsonProperty("data") val data: GetDataData,
    )
}