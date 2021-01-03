package com.lagradost.fastani

import android.annotation.SuppressLint
import android.provider.Settings
import android.util.Base64.*
import android.widget.Toast
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.fastani.MainActivity.Companion.activity
import khttp.structures.cookie.CookieJar
import java.lang.Exception
import java.net.URLEncoder
import java.util.*
import kotlin.concurrent.thread

class FastAniApi {
    data class HomePageResponse(
        val animeData: AnimeData,
        val homeSlidesData: List<Card>,
        val recentlyAddedData: List<Card>,
        val trendingData: List<Card>,
        var favorites: List<BookmarkedTitle?>?,
        var recentlySeen: List<LastEpisodeInfo?>?,
    )

    data class Token(val headers: Map<String, String>, val cookies: CookieJar)
    data class Title(val romaji: String, val english: String, val native: String)
    data class EndDate(val year: Int, val month: Int, val day: Int)
    data class FullEpisode(val file: String, val title: String?, val thumb: String?)
    data class Episode(val file: String)
    data class CoverImage(val large: String)
    data class Seasons(val episodes: List<FullEpisode>)
    data class CdnData(val seasons: List<Seasons>)
    data class Card(
        val title: Title,
        val endDate: EndDate,
        val episodes: Int,
        val duration: Int,
        val trailer: String?,
        val averageScore: Int,
        val isAdult: Boolean,
        val status: String,
        val coverImage: CoverImage,
        val bannerImage: String,
        val anilistId: String,
        val id: String,
        val description: String,
        val cdnData: CdnData,
        val genres: List<String>,
    )

    data class AnimeData(val cards: List<Card>)
    data class SearchResponse(val animeData: AnimeData)
    data class EpisodeResponse(val anime: Card, val nextEpisode: Int)

    data class Update(val shouldUpdate: Boolean, val updateURL: String?, val updateVersion: String?)

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; rv:68.0) Gecko/20100101 Firefox/68.0"
        private val mapper: JsonMapper = JsonMapper.builder().addModule(KotlinModule())
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
                val currentVersion = activity?.packageManager?.getPackageInfo(activity?.packageName, 0)
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
        fun getDonorStatus(): Boolean {
            try {
                val url = "https://cdn1.fastani.net/donors.json"
                val response = khttp.get(url).text
                val users = mapper.readValue<List<String>>(response)
                users.forEach lit@{
                    println(it)
                    try {
                        val responseId = decode(it, DEFAULT).toString(charset("UTF-8"))
                        val androidId: String =
                            Settings.Secure.getString(activity?.contentResolver, Settings.Secure.ANDROID_ID)
                        if (androidId == responseId || it == "all") {
                            return true
                        }
                    } catch (e: Exception) {
                        return@lit
                    }
                }
                return false
            } catch (e: Exception) {
                return false
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

        fun getLastWatch(): List<LastEpisodeInfo?> {
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

        fun getHome(canBeCached: Boolean): HomePageResponse? {
            var res: HomePageResponse? = null
            if (canBeCached && cachedHome != null) {
                res = cachedHome
            } else {
                val url = "https://fastani.net/api/data"
                val response =
                    currentToken?.let { khttp.get(url, headers = it.headers, cookies = currentToken!!.cookies) }
                res = response?.text?.let { mapper.readValue(it) }
            }

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