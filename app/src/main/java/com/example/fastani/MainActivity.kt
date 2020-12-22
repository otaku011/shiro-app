package com.example.fastani

import android.os.Bundle
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import com.beust.klaxon.lookup
import khttp.responses.Response
import khttp.structures.cookie.CookieJar
import org.json.JSONObject
import kotlin.concurrent.thread

data class Token(val headers: Map<String, String>, val cookies: CookieJar)

class MainActivity : AppCompatActivity() {

    // No error handling so far
    private fun getToken(): Token {
        val headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; rv:68.0) Gecko/20100101 Firefox/68.0")
        val fastani = khttp.get("https://fastani.net", headers = headers)
        val cookies = fastani.cookies
        val jsMatch = Regex("""src=\"(\/static\/js\/main.*?)\"""").find(fastani.text)
        val (destructed) = jsMatch!!.destructured
        val jsLocation = "https://fastani.net$destructed"
        val js = khttp.get(jsLocation, headers = headers)
        val tokenMatch = Regex("""method:\"GET\".*?\"(.*?)\".*?\"(.*?)\"""").find(js.text)
        val (key, token) = tokenMatch!!.destructured
        return Token(
            mapOf(
                key to token,
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; rv:68.0) Gecko/20100101 Firefox/68.0"
            ), cookies
        )
    }

    private fun search(query: String, token: Token, page: Int = 1): JsonObject {
        // Tags and years can be added
        val url = "https://fastani.net/api/data?page=${page}&animes=1&search=${query}&tags=&years="
        // Security headers
        val headers = token.headers
        val response = khttp.get(url, headers = headers, cookies = token.cookies)
        val parser: Parser = Parser.default()
        val stringBuilder: StringBuilder = StringBuilder(response.text)
        val json: JsonObject = parser.parse(stringBuilder) as JsonObject
        val cardArray = json.obj("animeData")?.array<JsonObject>("cards")
        // Gets a JsonArray of streaming links for example
        // .get(0) is first search result
        println(cardArray?.get(0)?.lookup<String?>("cdnData.seasons.episodes.file"))
        return json
    }

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
        // UNCOMMENT FOR WORKING SEARCH
        thread {
            val token = getToken()
            search("never", token)
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