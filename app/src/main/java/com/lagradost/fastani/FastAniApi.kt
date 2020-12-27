package com.lagradost.fastani

import com.lagradost.fastani.ui.Stopwatch
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import khttp.structures.cookie.CookieJar
import java.lang.Exception
import kotlin.concurrent.thread

class FastAniApi {
    data class HomePageResponse(
        val animeData: AnimeData,
        val homeSlidesData: List<Card>,
        val recentlyAddedData: List<Card>,
        val trendingData: List<Card>,
        var favorites: List<BookmarkedTitle?>?,
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
                return null
            }
        }

        //search via http get request, NOT INSTANT
        // ONLY PAGE 1
        fun search(query: String, page: Int = 1): SearchResponse? {
            // Tags and years can be added
            val url = "https://fastani.net/api/data?page=${page}&animes=1&search=${query}&tags=&years="
            // Security headers
            val headers = currentToken?.headers
            val response = headers?.let { khttp.get(url, headers = it, cookies = currentToken?.cookies) }
            return response?.text?.let { mapper.readValue(it) }
        }

        fun getCardById(id: String): EpisodeResponse? {
            val url = "https://fastani.net/api/data/anime/$id" //?season=$season&episode=$episode" // SPECIFYING EPISODE AND SEASON WILL ONLY GIVE 1 EPISODE
            val response = currentToken?.headers?.let { khttp.get(url, headers = it, cookies = currentToken?.cookies) }
            return response?.text?.let { mapper.readValue(it) }
        }

        var cachedHome: HomePageResponse? = null

        private fun getFav(): List<BookmarkedTitle?> {
            thread {
                getFullFav()
            }
            val keys = DataStore.getKeys(BOOKMARK_KEY)

            return keys.map {
                DataStore.getKey<BookmarkedTitle>(it)
            }
        }

        val fullBookmarks = hashMapOf<String, Card>()

        private fun getFullFav() {
            val keys = DataStore.getKeys(BOOKMARK_KEY)

            val books = keys.pmap {
                DataStore.getKey<BookmarkedTitle>(it)?.id?.let { it1 -> getCardById(it1)?.anime }
            }
            fullBookmarks.clear()
            for (b in books) {
                if (b != null) {
                    fullBookmarks[b.anilistId] = b
                }
            }
        }

        fun requestHome(canBeCached: Boolean = true, forceUpdateFav: Boolean = false): HomePageResponse? {
            if (currentToken == null) return null

            if (cachedHome != null && canBeCached) {
                onHomeFetched.invoke(cachedHome)
                if (forceUpdateFav) {
                    cachedHome?.favorites = getFav()
                }
                return cachedHome
            }
            return getHome()
        }

        fun getHome(): HomePageResponse? {
            val url = "https://fastani.net/api/data"
            val response = currentToken?.let { khttp.get(url, headers = it.headers, cookies = currentToken!!.cookies) }
            val res: HomePageResponse? = response?.text?.let { mapper.readValue(it) }

            res?.favorites = getFav()

            cachedHome = res
            onHomeFetched.invoke(res)
            return res
        }

        var currentToken: Token? = null
        var currentHeaders: MutableMap<String, String>? = null
        var onHomeFetched = Event<HomePageResponse?>()

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
            }
        }
    }
}