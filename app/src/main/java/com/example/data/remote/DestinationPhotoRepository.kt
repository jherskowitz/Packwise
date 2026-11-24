package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

class DestinationPhotoRepository {

  private val client = OkHttpClient.Builder()
    .connectTimeout(6, TimeUnit.SECONDS)
    .readTimeout(6, TimeUnit.SECONDS)
    .build()

  suspend fun fetchDestinationPhotoUrl(
    destination: String,
    country: String = "",
  ): String? = withContext(Dispatchers.IO) {
    val cleanDest = destination.trim()
    if (cleanDest.isBlank()) return@withContext null
    val cityOnly = cleanDest.substringBefore(",").trim()

    val candidates = buildList {
      add(cleanDest)
      if (cityOnly.isNotBlank() && !cityOnly.equals(cleanDest, ignoreCase = true) && country.isNotBlank()) {
        add("$cityOnly, $country")
      }
      if (country.isNotBlank()) add("$cleanDest, $country")
      if (cityOnly.isNotBlank() && !cityOnly.equals(cleanDest, ignoreCase = true)) {
        add(cityOnly)
      }
    }.distinct()

    for (title in candidates) {
      val direct = fetchSummaryThumbnail(title)
      if (!direct.isNullOrBlank()) return@withContext direct
    }

    val searchQuery = if (country.isNotBlank()) "$cleanDest $country" else cleanDest
    fetchSearchThumbnail(searchQuery)
  }

  private fun fetchSummaryThumbnail(title: String): String? {
    val encoded = URLEncoder.encode(title.replace(' ', '_'), "UTF-8")
      .replace("+", "%20")
    val url = "https://en.wikipedia.org/api/rest_v1/page/summary/$encoded"
    val request = Request.Builder()
      .url(url)
      .header("User-Agent", USER_AGENT)
      .header("Accept", "application/json")
      .build()

    return runCatching {
      client.newCall(request).execute().use { resp ->
        if (!resp.isSuccessful) return@use null
        val body = resp.body?.string().orEmpty()
        if (body.isBlank()) return@use null
        val json = JSONObject(body)
        if (json.optString("type").equals("disambiguation", ignoreCase = true)) {
          return@use null
        }
        val thumb = json.optJSONObject("thumbnail")?.optString("source").orEmpty()
        val original = json.optJSONObject("originalimage")?.optString("source").orEmpty()
        pickBestRasterUrl(listOf(thumb, original))
      }
    }.getOrNull()
  }

  private fun fetchSearchThumbnail(query: String): String? {
    val url = "https://en.wikipedia.org/w/api.php".toHttpUrl().newBuilder()
      .addQueryParameter("action", "query")
      .addQueryParameter("generator", "search")
      .addQueryParameter("gsrsearch", query)
      .addQueryParameter("gsrlimit", "5")
      .addQueryParameter("prop", "pageimages")
      .addQueryParameter("piprop", "thumbnail")
      .addQueryParameter("pithumbsize", "960")
      .addQueryParameter("format", "json")
      .build()

    val request = Request.Builder()
      .url(url)
      .header("User-Agent", USER_AGENT)
      .build()

    return runCatching {
      client.newCall(request).execute().use { resp ->
        if (!resp.isSuccessful) return@use null
        val body = resp.body?.string().orEmpty()
        if (body.isBlank()) return@use null
        val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages")
          ?: return@use null
        val candidates = buildList {
          val keys = pages.keys()
          while (keys.hasNext()) {
            val page = pages.optJSONObject(keys.next()) ?: continue
            val index = page.optInt("index", 99)
            val src = page.optJSONObject("thumbnail")?.optString("source").orEmpty()
            if (isUsableRasterPhoto(src)) {
              add(index to src)
            }
          }
        }.sortedBy { it.first }
        candidates.firstOrNull()?.second
      }
    }.getOrNull()
  }

  private fun pickBestRasterUrl(urls: List<String>): String? {
    val usable = urls.filter { isUsableRasterPhoto(it) }
    // Prefer higher-resolution Wikimedia thumbnail (~800-1000px) when original is giant or vice versa.
    return usable.firstOrNull { it.contains("thumb") } ?: usable.firstOrNull()
  }

  private fun isUsableRasterPhoto(url: String): Boolean {
    if (url.isBlank() || url.length > 950) return false
    val lower = url.lowercase(Locale.ROOT)
    if (!lower.startsWith("https://")) return false
    if (lower.endsWith(".svg") || lower.contains(".svg/")) return false
    if (lower.contains("flag_of_") || lower.contains("locator_map") || lower.contains("map_of_")) return false
    return true
  }

  private companion object {
    const val USER_AGENT = "PackWiseAndroid/1.4 (Travel Packing App)"
  }
}
