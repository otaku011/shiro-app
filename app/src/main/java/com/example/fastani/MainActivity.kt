package com.example.fastani

import android.os.Bundle
import android.webkit.CookieManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import com.beust.klaxon.Parser
import com.beust.klaxon.lookup
import khttp.responses.Response
import khttp.structures.cookie.Cookie
import khttp.structures.cookie.CookieJar
import org.json.JSONObject
import java.net.HttpCookie
import kotlin.concurrent.thread

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

const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; rv:68.0) Gecko/20100101 Firefox/68.0"

// No error handling so far
fun getToken(): Token {
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

fun search(query: String, token: Token, page: Int = 1): SearchResponse? {
    // Tags and years can be added
    val url = "https://fastani.net/api/data?page=${page}&animes=1&search=${query}&tags=&years="
    // Security headers
    val headers = token.headers
    val response = khttp.get(url, headers = headers, cookies = token.cookies)
    val parsed = Klaxon().parse<SearchResponse>(response.text)
    println(parsed)
    return parsed
}

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Setting the theme
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val autoDarkMode = settingsManager.getBoolean("auto_dark_mode", true)
        val darkMode = settingsManager.getBoolean("dark_mode", false)

        if (autoDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        } else {
            if (darkMode) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val navView: BottomNavigationView = findViewById(R.id.nav_view)
        thread {
            //val token = getToken()
            //search("never", token)
        }


        val navController = findNavController(R.id.nav_host_fragment)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_search, R.id.navigation_settings
            )
        )
        //setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }
}