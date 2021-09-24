package io.github.hotlava03.baclava.dashboard.api.controllers

import com.google.gson.Gson
import io.github.hotlava03.baclava.config.ConfigHandler
import io.github.hotlava03.baclava.dashboard.api.entities.CurrentAuthInformation
import io.github.hotlava03.baclava.dashboard.api.entities.OAuthResponseData
import io.github.hotlava03.baclava.dashboard.api.entities.User
import io.github.hotlava03.baclava.dashboard.auth.AuthHandler
import io.github.hotlava03.baclava.dashboard.functions.baseUri
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.auth.*
import io.ktor.client.features.auth.providers.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam

@Controller
class AuthController {
    private val redirect = baseUri("auth/callback")

    // Initialize HTTP client and install timeout.
    private val client = HttpClient(CIO) {
        install(Auth) {
            basic {
                credentials {
                    BasicAuthCredentials(
                        username = ConfigHandler.config.clientId,
                        password = ConfigHandler.config.clientSecret
                    )
                }
            }
        }

        install(JsonFeature) {
            acceptContentTypes = listOf(ContentType.Application.Json, ContentType.Application.FormUrlEncoded)
        }
    }

    /**
     * Check if the given token is valid. If it
     * is, the username will be given.
     */
    @GetMapping("/auth")
    fun index(@RequestHeader(required = true) authorization: String): ResponseEntity<User?> {
        val user = AuthHandler[authorization] ?: return ResponseEntity.badRequest().body(null)
        return ResponseEntity.ok(user)
    }

    /**
     * This is always called by Discord as the callback
     * URL when redirected from the OAuth2 authentication
     * process.
     */
    @GetMapping("/auth/callback")
    fun callback(@RequestParam code: String): ResponseEntity<OAuthResponseData> {
        println(ContentType.Application.FormUrlEncoded.toString())
        return runBlocking {
            // Create a request to Discord.
            var res: HttpResponse = client.submitFormWithBinaryData(
                url = "https://discord.com/api/v8/oauth2/token",
                formData = formData {
                    append("code", code)
                    append("grant_type", "authorization_code")
                    append("redirect_uri", redirect)
                },
            )
            var str = res.receive<String>()
            val gson = Gson()
            val entity = gson.fromJson(str, OAuthResponseData::class.java)

            res = client.request {
                url("https://discord.com/api/v8/oauth2/@me")
                header("Authorization", "Bearer ${entity.access_token}")
                method = HttpMethod.Get
            }

            str = res.receive()
            println(str)
            val currentInfo = gson.fromJson(str, CurrentAuthInformation::class.java)
            if (currentInfo.user == null) {
                entity.access_token = ""
                return@runBlocking ResponseEntity.badRequest().body(entity)
            }

            AuthHandler[entity.access_token] = currentInfo.user
            return@runBlocking ResponseEntity.ok(entity)
        }
    }
}
