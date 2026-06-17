package com.rssfeeder.server

import com.rssfeeder.debug.DebugLogger
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Base64

object RelayManager {

    private const val RELAY_OWNER = "dcon4"
    private const val RELAY_REPO = "RSS-Feeder"
    private const val RELAY_BRANCH = "gh-pages"
    private const val API_BASE = "https://api.github.com"
    private const val PAGES_BASE = "https://dcon4.github.io"

    fun feedToken(feedId: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("rss_feeder_relay_$feedId".toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    fun getRelayUrl(feedId: Long): String {
        return "$PAGES_BASE/$RELAY_REPO/feeds/${feedToken(feedId)}.xml"
    }

    fun pushFeed(pat: String, feedId: Long, rssXml: String): String? {
        val token = feedToken(feedId)
        return try {
            val path = "feeds/$token.xml"
            val existingSha = getExistingSha(pat, path)

            val url = URL("$API_BASE/repos/$RELAY_OWNER/$RELAY_REPO/contents/$path")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "PUT"
            conn.setRequestProperty("Authorization", "Bearer $pat")
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            val encoded = Base64.getEncoder().encodeToString(rssXml.toByteArray(Charsets.UTF_8))
            val body = buildString {
                append("{\"message\":\"Update feed $feedId\",\"branch\":\"$RELAY_BRANCH\",\"content\":\"$encoded\"")
                if (existingSha != null) {
                    append(",\"sha\":\"$existingSha\"")
                }
                append("}")
            }

            OutputStreamWriter(conn.outputStream).use { it.write(body) }

            val code = conn.responseCode
            val responseBody = if (code in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText() ?: "No body"
            }
            conn.disconnect()

            if (code in 200..299) {
                DebugLogger.log("RelayManager", "Feed $feedId pushed to relay")
                null
            } else {
                val err = "Push failed: HTTP $code $responseBody"
                DebugLogger.log("RelayManager", err)
                err
            }
        } catch (e: Exception) {
            val err = "Push error: ${e.message}"
            DebugLogger.log("RelayManager", err)
            err
        }
    }

    private fun getExistingSha(pat: String, path: String): String? {
        return try {
            val url = URL("$API_BASE/repos/$RELAY_OWNER/$RELAY_REPO/contents/$path?ref=$RELAY_BRANCH")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $pat")
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val code = conn.responseCode
            if (code == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val sha = body.substringAfter("\"sha\":\"").substringBefore("\"")
                if (sha.isNotEmpty()) sha else null
            } else {
                conn.disconnect()
                null
            }
        } catch (e: Exception) {
            DebugLogger.verbose("RelayManager", "No existing file for $path: ${e.message}")
            null
        }
    }
}
