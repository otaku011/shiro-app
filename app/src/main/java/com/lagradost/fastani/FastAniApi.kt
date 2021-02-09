package com.lagradost.fastani

import android.annotation.SuppressLint
import android.provider.Settings
import android.util.Base64.*
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.fastani.MainActivity.Companion.activity
import com.lagradost.fastani.MainActivity.Companion.md5
import khttp.structures.cookie.CookieJar
import java.lang.Exception
import java.net.URLEncoder
import kotlin.concurrent.thread

class FastAniApi {
    data class HomePageResponse(
        @JsonProperty("animeData") val animeData: AnimeData,
        @JsonProperty("homeSlidesData") val homeSlidesData: List<Card>,
        @JsonProperty("recentlyAddedData") val recentlyAddedData: List<Card>,
        @JsonProperty("trendingData") val trendingData: List<Card>,
        @JsonProperty("favorites") var favorites: List<BookmarkedTitle?>?,
        @JsonProperty("recentlySeen") var recentlySeen: List<LastEpisodeInfo?>?,
        @JsonProperty("schedule") var schedule: List<ScheduleItem?>?,
    )

    data class Token(
        @JsonProperty("headers") val headers: Map<String, String>,
        @JsonProperty("cookies") val cookies: CookieJar
    )

    data class Title(
        @JsonProperty("romaji") val romaji: String,
        @JsonProperty("english") val english: String,
        @JsonProperty("native") val native: String
    )

    data class ScheduleTitle(
        @JsonProperty("romaji") val romaji: String?,
        @JsonProperty("english") val english: String?,
        @JsonProperty("native") val native: String?
    )

    data class EndDate(
        @JsonProperty("year") val year: Int,
        @JsonProperty("month") val month: Int,
        @JsonProperty("day") val day: Int
    )

    data class FullEpisode(
        @JsonProperty("file") val file: String,
        @JsonProperty("title") val title: String?,
        @JsonProperty("thumb") val thumb: String?
    )

    data class Episode(@JsonProperty("file") val file: String)
    data class CoverImage(@JsonProperty("large") val large: String)
    data class Seasons(@JsonProperty("episodes") val episodes: List<FullEpisode>)
    data class CdnData(@JsonProperty("seasons") val seasons: List<Seasons>)
    data class Card(
        @JsonProperty("title") val title: Title,
        @JsonProperty("endDate") val endDate: EndDate,
        @JsonProperty("episodes") val episodes: Int,
        @JsonProperty("duration") val duration: Int,
        @JsonProperty("trailer") val trailer: String?,
        @JsonProperty("averageScore") val averageScore: Int,
        @JsonProperty("isAdult") val isAdult: Boolean,
        @JsonProperty("status") val status: String,
        @JsonProperty("coverImage") val coverImage: CoverImage,
        @JsonProperty("bannerImage") val bannerImage: String,
        @JsonProperty("anilistId") val anilistId: String,
        @JsonProperty("id") val id: String,
        @JsonProperty("description") val description: String,
        @JsonProperty("cdnData") val cdnData: CdnData,
        @JsonProperty("genres") val genres: List<String>,
    )

    data class ScheduleItem(
        @JsonProperty("episode") val episode: Int,
        @JsonProperty("timeUntilAiring") val timeUntilAiring: String,
        @JsonProperty("media") val media: ScheduleMediaItem
    )


    data class ScheduleMediaItem(
        @JsonProperty("averageScore") val averageScore: Int?,
        @JsonProperty("id") val id: Int,
        @JsonProperty("coverImage") val coverImage: CoverImage,
        @JsonProperty("title") val title: ScheduleTitle,
    )

    data class AnimeData(@JsonProperty("cards") val cards: List<Card>)
    data class SearchResponse(@JsonProperty("animeData") val animeData: AnimeData)
    data class EpisodeResponse(@JsonProperty("anime") val anime: Card, @JsonProperty("native") val nextEpisode: Int)

    data class Update(
        @JsonProperty("shouldUpdate") val shouldUpdate: Boolean,
        @JsonProperty("updateURL") val updateURL: String?,
        @JsonProperty("updateVersion") val updateVersion: String?
    )

    data class Donor(@JsonProperty("id") val id: String)

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; rv:68.0) Gecko/20100101 Firefox/68.0"
        private val mapper = JsonMapper.builder().addModule(KotlinModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()

        // NULL IF ERROR
        private fun getToken(): Token? {
            try {
                val headers = mapOf("User-Agent" to USER_AGENT)
                val fastani = khttp.get("https://fastani.net", headers = headers)

                val jsMatch = Regex("""src="(/static/js/main.*?)"""").find(fastani.text)
                val (destructed) = jsMatch!!.destructured
                val jsLocation = "https://fastani.net$destructed"
                val js = khttp.get(jsLocation, headers = headers)
                val tokenMatch = Regex("""method:"GET".*?"(.*?)".*?"(.*?)"""").find(js.text)
                val (key, token) = tokenMatch!!.destructured
                val tokenHeaders = mapOf(
                    key to token,
                    "User-Agent" to USER_AGENT
                )
                return Token(
                    tokenHeaders,
                    fastani.cookies,
                )
            } catch (e: Exception) {
                println(e)
                return null
            }
        }

        fun getAppUpdate(): Update {
            try {
                val url = "https://cdn1.fastani.net/apk/"
                val response = khttp.get(url)
                val versionRegex = Regex("""href="(.*?((\d)\.(\d)\.(\d)).*\.apk)"""")
                val found =
                    versionRegex.findAll(response.text).sortedWith(compareBy {
                        it.groupValues[2]
                    }).toList().lastOrNull()
                val currentVersion = activity?.packageName?.let { activity?.packageManager?.getPackageInfo(it, 0) }
                //println(found.groupValues)
                //println(currentVersion?.versionName)

                val shouldUpdate =
                    if (found != null) currentVersion?.versionName?.compareTo(found.groupValues[2])!! < 0 else false
                return Update(shouldUpdate, url + found?.groupValues?.get(1), found?.groupValues?.get(2))

            } catch (e: Exception) {
                println(e)
                return Update(false, "", "")
            }
        }

        @SuppressLint("HardwareIds")
        // Developers please do not share an apk with donor mode enabled for all as fastani relies on donors to keep the site alive and ad-free.
        fun getDonorStatus(): String {
            val url = "https://raw.githubusercontent.com/Blatzar/donors/master/donors.json"
            try {
                // Change cache with this
                // , headers = mapOf("Cache-Control" to "max-age=60")
                val response = khttp.get(url).text
                val users = mapper.readValue<List<Donor>>(response)
                users.forEach {
                    try {
                        val androidId: String =
                            Settings.Secure.getString(activity?.contentResolver, Settings.Secure.ANDROID_ID)
                        if (androidId.md5() == it.id || it.id == "all") {
                            return androidId.md5()
                        }
                    } catch (e: Exception) {
                        return@forEach
                    }
                }
                return ""
            } catch (e: Exception) {
                return ""
            }
        }

        //search via http get request, NOT INSTANT
        // ONLY PAGE 1
        fun search(query: String, page: Int = 1): SearchResponse? {
            // Tags and years can be added
            val url = "https://fastani.net/api/data?page=${page}&animes=1&search=${
                URLEncoder.encode(
                    query,
                    "UTF-8"
                )
            }&tags=&years="
            // Security headers
            val headers = currentToken?.headers
            val response = headers?.let { khttp.get(url, headers = it, cookies = currentToken?.cookies) }
            return response?.text?.let { mapper.readValue(it) }
        }

        val lastCards = hashMapOf<String, Card>()
        fun getCardById(id: String, canBeCached: Boolean = true): EpisodeResponse? {
            if (canBeCached && lastCards.containsKey(id)) {
                return EpisodeResponse(lastCards[id]!!, 0)
            }
            val url =
                "https://fastani.net/api/data/anime/$id" //?season=$season&episode=$episode" // SPECIFYING EPISODE AND SEASON WILL ONLY GIVE 1 EPISODE
            val response = currentToken?.headers?.let { khttp.get(url, headers = it, cookies = currentToken?.cookies) }
            val resp: EpisodeResponse? = response?.text?.let { mapper.readValue(it) }
            if (resp != null) {
                lastCards[id] = resp.anime
            }
            return resp
        }

        var cachedHome: HomePageResponse? = null

        private fun getFav(): List<BookmarkedTitle?> {
            val keys = DataStore.getKeys(BOOKMARK_KEY)
            thread {
                keys.pmap {
                    DataStore.getKey<BookmarkedTitle>(it)?.id?.let { it1 -> getCardById(it1)?.anime }
                }
            }

            return keys.map {
                DataStore.getKey<BookmarkedTitle>(it)
            }
        }

        private fun getLastWatch(): List<LastEpisodeInfo?> {
            val keys = DataStore.getKeys(VIEW_LST_KEY)
            thread {
                keys.pmap {
                    DataStore.getKey<LastEpisodeInfo>(it)?.id?.let { it1 -> getCardById(it1)?.anime }
                }
            }
            return (DataStore.getKeys(VIEW_LST_KEY).map {
                DataStore.getKey<LastEpisodeInfo>(it)
            }).sortedBy { if (it == null) 0 else -(it.seenAt) }
        }

        private fun getFullFav() {

            /*
            fullBookmarks.clear()
            for (b in books) {
                if (b != null) {
                    fullBookmarks[b.anilistId] = b
                }
            }*/
        }

        fun requestHome(canBeCached: Boolean = true): HomePageResponse? {
            println("LOAD HOME $currentToken")
            if (currentToken == null) return null
            return getHome(canBeCached)
        }

        private fun getSchedule(): List<ScheduleItem>? {
            val url = "https://fastani.net/api/schedule"
            val res = currentHeaders?.let {
                khttp.get(url, headers = it.toMap())
            }
            return if (res != null) {
                mapper.readValue(res.text)
            } else null
        }

        fun getHome(canBeCached: Boolean): HomePageResponse? {
            var res: HomePageResponse? = null
            if (canBeCached && cachedHome != null) {
                res = cachedHome
            } else {
                val url = "https://fastani.net/api/data"
                val response =
                    currentToken?.let { khttp.get(url, headers = it.headers, cookies = currentToken!!.cookies) }
                res = response?.text?.let { mapper.readValue(it) }
                res?.schedule = getSchedule()
            }
            // Anything below here shouldn't do network requests (network on main thread)
            // (card.removeButton.setOnClickListener {requestHome(true)})

            if (res == null) {
                hasThrownError = 0
                onHomeError.invoke(false)
                return null
            }
            res.favorites = getFav()
            res.recentlySeen = getLastWatch()

            cachedHome = res
            onHomeFetched.invoke(res)
            return res
        }

        var currentToken: Token? = null
        var currentHeaders: MutableMap<String, String>? = null
        var onHomeFetched = Event<HomePageResponse?>()
        var onHomeError = Event<Boolean>() // TRUE IF FULL RELOAD OF TOKEN, FALSE IF JUST HOME
        var hasThrownError = -1

        fun init() {
            if (currentToken != null) return

            currentToken = getToken()
            if (currentToken != null) {
                currentHeaders = currentToken?.headers?.toMutableMap()
                currentHeaders?.set("Cookie", "")
                currentToken?.cookies?.forEach {
                    currentHeaders?.set("Cookie", it.key + "=" + it.value.substring(0, it.value.indexOf(';')) + ";")
                }
                requestHome()
            } else {
                println("TOKEN ERROR")
                hasThrownError = 1
                onHomeError.invoke(true)
            }
        }
    }
}