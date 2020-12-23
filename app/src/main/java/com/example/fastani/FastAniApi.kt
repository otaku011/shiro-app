package com.example.fastani

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import khttp.structures.cookie.CookieJar


const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; rv:68.0) Gecko/20100101 Firefox/68.0"

class FastAniApi {
    data class HomePageResponse(
        val animeData: AnimeData,
        val homeSlidesData: List<Card>,
        val recentlyAddedData: List<Card>,
        val trendingData: List<Card>
    )

    data class Token(val headers: Map<String, String>, val cookies: CookieJar)
    data class Title(val romaji: String, val english: String, val native: String)
    data class EndDate(val year: Int, val month: Int, val day: Int)
    data class FullEpisode(val file: String, val title: String, val thumb: String)
    data class Episode(val file: String)
    data class CoverImage(val large: String)
    data class Seasons(val episodes: List<Episode>)
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
        val cdnData: CdnData,
    )

    data class AnimeData(val cards: List<Card>)
    data class SearchResponse(val animeData: AnimeData)

    companion object {
        private val mapper: JsonMapper = JsonMapper.builder().addModule(KotlinModule()).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()

        // No error handling so far
        private fun getToken(): Token {
            val headers = mapOf("User-Agent" to USER_AGENT)
            val fastani = khttp.get("https://fastani.net", headers = headers)

            val jsMatch = Regex("""src=\"(\/static\/js\/main.*?)\"""").find(fastani.text)
            val (destructed) = jsMatch!!.destructured
            val jsLocation = "https://fastani.net$destructed"
            val js = khttp.get(jsLocation, headers = headers)
            val tokenMatch = Regex("""method:\"GET\".*?\"(.*?)\".*?\"(.*?)\"""").find(js.text)
            val (key, token) = tokenMatch!!.destructured
            val tokenHeaders = mapOf(
                key to token,
                "User-Agent" to USER_AGENT
            )
            return Token(
                tokenHeaders,
                fastani.cookies,
            )
        }

        //search via http get request, NOT INSTANT
        fun search(query: String, page: Int = 1): SearchResponse? {
            // Tags and years can be added
            val url = "https://fastani.net/api/data?page=${page}&animes=1&search=${query}&tags=&years="
            // Security headers
            val headers = currentToken?.headers
            val response = headers?.let { khttp.get(url, headers = it, cookies = currentToken?.cookies) }
            val parsed: SearchResponse? = response?.text?.let { mapper.readValue(it) }
            println(parsed)
            return parsed
        }

        fun getHome(): HomePageResponse? {
            val url = "https://fastani.net/api/data"
            val response = currentToken?.let { khttp.get(url, headers = it.headers, cookies = currentToken!!.cookies) }
            println(currentToken)
            if (response != null) {
                println(response.text)
            }
            return response?.text?.let { mapper.readValue(it) }
        }

        var currentToken: Token? = null;
        var currentHeaders: MutableMap<String, String>? = null;

        fun init() {
            if (currentToken != null) return;

            currentToken = getToken();
            currentHeaders = FastAniApi.currentToken?.headers?.toMutableMap()
            currentHeaders?.set("Cookie", "")
            FastAniApi.currentToken?.cookies?.forEach {
                currentHeaders?.set("Cookie", it.key + "=" + it.value.substring(0, it.value.indexOf(';')) + ";")
            }
        }
    }
}