package com.lagradost.fastani

import android.util.Base64
import java.net.URL
import java.security.SecureRandom
import kotlin.concurrent.thread

const val MAL_CLIENT_ID: String = "8c25dbc2c2ea8adb0e1901c7e923aa78"
const val MAL_ACCOUNT_ID = "0" // MIGHT WANT TO BE USED IF YOU WANT MULTIPLE ACCOUNT LOGINS

class MALApi {
    companion object {
        var requestId = 0
        var codeVerifier = ""

        fun authenticate() {
            // It is recommended to use a URL-safe string as code_verifier.
            // See section 4 of RFC 7636 for more details.

            val secureRandom = SecureRandom()
            val codeVerifierBytes = ByteArray(96) // base64 has 6bit per char; (8/6)*96 = 128
            secureRandom.nextBytes(codeVerifierBytes)
            codeVerifier =
                Base64.encodeToString(codeVerifierBytes, Base64.DEFAULT).trimEnd('=').replace("+", "-")
                    .replace("/", "_").replace("\n","")
            val codeChallenge = codeVerifier
            println("codeVerifier" + codeVerifier)
            val request =
                "https://myanimelist.net/v1/oauth2/authorize?response_type=code&client_id=$MAL_CLIENT_ID&code_challenge=$codeChallenge&state=RequestID$requestId";
            MainActivity.openBrowser(request);
        }

        fun authenticateLogin(data: String) {
            try {
                val sanitizer =
                    MainActivity.splitQuery(URL(data.replace("fastaniapp", "https").replace("/#", "?")))!! // FIX ERROR
                val state = sanitizer["state"]!!
                if (state == "RequestID" + requestId) {
                    val currentCode = sanitizer["code"]!!
                    thread {
                        var res = ""
                        try {
                            println("cc::::: " + codeVerifier)
                            res = khttp.post(
                                "https://myanimelist.net/v1/oauth2/token",
                                data = mapOf("client_id" to MAL_CLIENT_ID,
                                    "code" to currentCode,
                                    "code_verifier" to codeVerifier,
                                    "grant_type" to "authorization_code")).text
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        if(res != "") {
                            println("GOT MAL MASTER TOKEN:::: " + res)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

    }
}