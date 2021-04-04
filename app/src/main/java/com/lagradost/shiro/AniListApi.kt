package com.lagradost.shiro

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.shiro.MainActivity.Companion.activity
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

const val ANILIST_CLIENT_ID = "4636"
const val ANILIST_ACCOUNT_ID = "0" // MIGHT WANT TO BE USED IF YOU WANT MULTIPLE ACCOUNT LOGINS

class AniListApi {
    companion object {
        val aniListStatusString = arrayOf("CURRENT", "COMPLETED", "PAUSED", "DROPPED", "PLANNING", "REPEATING")

        val mapper = JsonMapper.builder().addModule(KotlinModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()


        // Changing names of these will show up in UI
        enum class AniListStatusType(var value: Int) {
            Watching(0),
            Completed(1),
            Paused(2),
            Dropped(3),
            Planning(4),
            Rewatching(5),
            None(-1)
        }

        fun fromIntToAnimeStatus(inp: Int): AniListStatusType {//= AniListStatusType.values().first { it.value == inp }
            return when (inp) {
                -1 -> AniListStatusType.None
                0 -> AniListStatusType.Watching
                1 -> AniListStatusType.Completed
                2 -> AniListStatusType.Paused
                3 -> AniListStatusType.Dropped
                4 -> AniListStatusType.Planning
                5 -> AniListStatusType.Rewatching
                else -> AniListStatusType.None
            }
        }

        fun authenticate() {
            val request = "https://anilist.co/api/v2/oauth/authorize?client_id=$ANILIST_CLIENT_ID&response_type=token";
            MainActivity.openBrowser(request);
        }

        fun initGetUser() {
            if (DataStore.getKey<String>(ANILIST_TOKEN_KEY, ANILIST_ACCOUNT_ID, null) == null) return
            thread {
                getUser()
            }
        }

        fun authenticateLogin(data: String) {
            try {
                val sanitizer =
                    MainActivity.splitQuery(URL(data.replace("fastaniapp", "https").replace("/#", "?")))!! // FIX ERROR
                val token = sanitizer["access_token"]!!
                val expiresIn = sanitizer["expires_in"]!!
                println("DATA: " + token + "|" + expiresIn)

                val endTime = MainActivity.unixTime() + expiresIn.toLong()

                DataStore.setKey(ANILIST_UNIXTIME_KEY, ANILIST_ACCOUNT_ID, endTime)
                DataStore.setKey(ANILIST_TOKEN_KEY, ANILIST_ACCOUNT_ID, token)

                println("ANILIST LOGIN DONE")
                thread {
                    getUser()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun checkToken(): Boolean {
            if (MainActivity.unixTime() > DataStore.getKey(ANILIST_UNIXTIME_KEY, ANILIST_ACCOUNT_ID, 0L)!!) {
                activity!!.runOnUiThread {
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
                }
                return true
            } else {
                return false
            }
        }

        private fun postApi(url: String, q: String): String {
            return try {
                if (!checkToken()) {
                    // println("VARS_ " + vars)
                    khttp.post(
                        "https://graphql.anilist.co/",
                        headers = mapOf(
                            "Authorization" to "Bearer " + DataStore.getKey(
                                ANILIST_TOKEN_KEY,
                                ANILIST_ACCOUNT_ID,
                                ""
                            )!!
                        ),
                        data = mapOf("query" to q),//(if (vars == null) mapOf("query" to q) else mapOf("query" to q, "variables" to vars))
                        timeout = 5.0 // REASONABLE TIMEOUT
                    ).text.replace("\\", "")
                } else {
                    ""
                }
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
        }

        fun getDataAboutId(id: Int): AniListTitleHolder? {
            val q: String =
                """query (${'$'}id: Int = $id) { # Define which variables will be used in the query (id)
                Media (id: ${'$'}id, type: ANIME) { # Insert our variables into the query arguments (id) (type: ANIME is hard-coded in the query)
                    id
                    episodes
                    isFavourite
                    mediaListEntry {
                        progress
                        status
                        score (format: POINT_10)
                    }
                }
            }"""
            try {
                println("ID::::: " + id)
                val data = postApi("https://graphql.anilist.co", q)
                var d: GetDataRoot? = null
                try {
                    d = mapper.readValue<GetDataRoot>(data)
                } catch (e: Exception) {
                    println("Anilist json failed")
                }
                if (d == null) {
                    return null
                }

                val main = d.data.media
                if (main.mediaListEntry != null) {
                    println(main.mediaListEntry.status)
                    println(aniListStatusString)
                    println(aniListStatusString.indexOf(main.mediaListEntry.status))
                    return AniListTitleHolder(
                        id = id,
                        isFavourite = main.isFavourite,
                        progress = main.mediaListEntry.progress,
                        episodes = main.episodes,
                        score = main.mediaListEntry.score,
                        type = fromIntToAnimeStatus(aniListStatusString.indexOf(main.mediaListEntry.status))
                    )
                } else {
                    return AniListTitleHolder(
                        id = id,
                        isFavourite = main.isFavourite,
                        progress = 0,
                        episodes = main.episodes,
                        score = 0,
                        type = AniListStatusType.None
                    )
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                return null
            }

        }

        fun toggleLike(id: Int): Boolean {
            val q: String = """mutation (${'$'}animeId: Int = $id) {
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
			}"""
            val data = postApi("https://graphql.anilist.co", q)
            return data != ""
        }

        fun postDataAboutId(id: Int, type: AniListStatusType, score: Int, progress: Int): Boolean {
            val q: String =
                """mutation (${'$'}id: Int = $id, ${'$'}status: MediaListStatus = ${aniListStatusString[type.value]}, ${'$'}scoreRaw: Int = ${score * 10}, ${'$'}progress: Int = $progress) {
                SaveMediaListEntry (mediaId: ${'$'}id, status: ${'$'}status, scoreRaw: ${'$'}scoreRaw, progress: ${'$'}progress) {
                    id
                    status
                    progress
                    score
                }
                }"""

            val data = postApi("https://graphql.anilist.co", q)
            println("POST:" + data)
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
                        favourites {
                            anime {
                                nodes {
                                    id
                                }
                            }
                        }
  					}
				}"""
            try {
                val data = postApi("https://graphql.anilist.co", q)
                if (data == "") return null
                println("RESULT: " + data)
                val userData = mapper.readValue<AniListRoot>(data)
                val u = userData.data.Viewer
                val user = AniListUser(
                    u.id,
                    u.name,
                    u.avatar.large,
                )
                if (setSettings) {
                    DataStore.setKey(ANILIST_USER_KEY, ANILIST_ACCOUNT_ID, user)
                }
                /* // TODO FIX FAVS
                for(i in u.favourites.anime.nodes) {
                    println("FFAV:" + i.id)
                }*/
                return user
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                return null
            }
        }

        fun getSeason(id: Int): SeasonResponse? {
            val q: String = """
               query (${'$'}id: Int = $id) {
                   Media (id: ${'$'}id, type: ANIME) {
                       id
                       idMal
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
                data = mapOf("query" to q)
            ).text
            if (data == "") return null
            return try {
                mapper.readValue(data)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        fun getAllSeasons(id: Int): List<SeasonResponse?> {
            val seasons = mutableListOf<SeasonResponse?>()
            fun getSeasonRecursive(id: Int) {
                println(id)
                val season = getSeason(id)
                if (season != null) {
                    seasons.add(season)
                    if (season.data.Media.format?.startsWith("TV") == true) {
                        season.data.Media.relations.edges.forEach {
                            if (it.node.format != null) {
                                if (it.relationType == "SEQUEL" && it.node.format.startsWith("TV")) {
                                    getSeasonRecursive(it.node.id)
                                    return@forEach
                                }
                            }
                        }
                    }
                }
            }
            getSeasonRecursive(id)
            return seasons.toList()
        }

        fun secondsToReadable(seconds: Int, completedValue: String): String {
            var secondsLong = seconds.toLong()
            val days = TimeUnit.SECONDS
                .toDays(secondsLong)
            secondsLong -= TimeUnit.DAYS.toSeconds(days)

            val hours = TimeUnit.SECONDS
                .toHours(secondsLong)
            secondsLong -= TimeUnit.HOURS.toSeconds(hours)

            val minutes = TimeUnit.SECONDS
                .toMinutes(secondsLong)
            secondsLong -= TimeUnit.MINUTES.toSeconds(minutes)
            if (minutes < 0) {
                return completedValue
            }
            //println("$days $hours $minutes")
            return "${if (days != 0L) "$days" + "d " else ""}${if (hours != 0L) "$hours" + "h " else ""}${minutes}m"
        }
    }

    data class SeasonResponse(
        @JsonProperty("data") val data: SeasonData,
    )

    data class SeasonData(
        @JsonProperty("Media") val Media: SeasonMedia,
    )

    data class SeasonMedia(
        @JsonProperty("id") val id: Int,
        @JsonProperty("idMal") val idMal: Int?,
        @JsonProperty("format") val format: String?,
        @JsonProperty("nextAiringEpisode") val nextAiringEpisode: SeasonNextAiringEpisode?,
        @JsonProperty("relations") val relations: SeasonEdges,
    )

    data class SeasonNextAiringEpisode(
        @JsonProperty("episode") val episode: Int,
        @JsonProperty("timeUntilAiring") val timeUntilAiring: Int,
    )

    data class SeasonEdges(
        @JsonProperty("edges") val edges: List<SeasonEdge>,
    )

    data class SeasonEdge(
        @JsonProperty("id") val id: Int,
        @JsonProperty("relationType") val relationType: String,
        @JsonProperty("node") val node: SeasonNode,
    )

    data class AniListFavoritesMediaConnection(
        @JsonProperty("nodes") val nodes: List<LikeNode>,
    )

    data class AniListFavourites(
        @JsonProperty("anime") val anime: AniListFavoritesMediaConnection,
    )

    data class SeasonNode(
        @JsonProperty("id") val id: Int,
        @JsonProperty("format") val format: String?,
        @JsonProperty("nextAiringEpisode") val nextAiringEpisode: SeasonNextAiringEpisode?,
    )

    data class AniListAvatar(
        @JsonProperty("large") val large: String,
    )

    data class AniListViewer(
        @JsonProperty("id") val id: Int,
        @JsonProperty("name") val name: String,
        @JsonProperty("avatar") val avatar: AniListAvatar,
        @JsonProperty("favourites") val favourites: AniListFavourites,
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
        @JsonProperty("episodes") val episodes: Int,
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
        @JsonProperty("episodes") val episodes: Int,
        @JsonProperty("mediaListEntry") val mediaListEntry: GetDataMediaListEntry?,
    )

    data class GetDataData(
        @JsonProperty("Media") val media: GetDataMedia,
    )

    data class GetDataRoot(
        @JsonProperty("data") val data: GetDataData,
    )
}