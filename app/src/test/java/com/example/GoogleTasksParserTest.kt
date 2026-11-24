package com.example

import com.example.data.remote.GoogleWorkspaceRepository
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GoogleTasksParserTest {

  @Test
  fun parseTasksFromText_parsesBulletsCheckboxesAndNotes() {
    val raw = """
      - [ ] Passport - Check expiry date
      - [x] Rain jacket
      * Sunscreen | SPF 50
      1. Hiking boots
      • Power bank
      Passport
    """.trimIndent()

    val items = GoogleWorkspaceRepository.parseTasksFromText(raw)
    assertEquals(5, items.size)

    assertEquals("Passport", items[0].title)
    assertEquals("Check expiry date", items[0].notes)
    assertFalse(items[0].isCompleted)

    assertEquals("Rain jacket", items[1].title)
    assertTrue(items[1].isCompleted)

    assertEquals("Sunscreen", items[2].title)
    assertEquals("SPF 50", items[2].notes)

    assertEquals("Hiking boots", items[3].title)
    assertEquals("Power bank", items[4].title)
  }

  @Test
  fun parseTasksFromText_parsesGoogleTakeoutJson() {
    val json = """
      {
        "kind": "tasks#taskLists",
        "items": [
          {
            "kind": "tasks#taskList",
            "title": "Packing List",
            "items": [
              {
                "id": "t1",
                "title": "Travel adapter",
                "notes": "Type C + G",
                "status": "needsAction"
              },
              {
                "id": "t2",
                "title": "Swimwear",
                "status": "completed"
              }
            ]
          }
        ]
      }
    """.trimIndent()

    val items = GoogleWorkspaceRepository.parseTasksFromText(json)
    assertEquals(2, items.size)
    assertEquals("Travel adapter", items[0].title)
    assertEquals("Type C + G", items[0].notes)
    assertFalse(items[0].isCompleted)
    assertEquals("Swimwear", items[1].title)
    assertTrue(items[1].isCompleted)
  }

  @Test
  fun fetchTasksFromList_requestsCompletedAndHiddenAndPaginatesAllTasks() = runTest {
    val capturedUrls = mutableListOf<String>()
    val client = OkHttpClient.Builder()
      .addInterceptor(
        Interceptor { chain ->
          val req = chain.request()
          val url = req.url
          capturedUrls.add(url.toString())

          val pageToken = url.queryParameter("pageToken")
          val bodyJson = if (pageToken == null) {
            """
              {
                "nextPageToken": "page_2",
                "items": [
                  {"id": "1", "title": "Passport", "status": "needsAction"},
                  {"id": "2", "title": "Chargers", "status": "completed", "hidden": true},
                  {"id": "3", "title": "Old Trashed Item", "status": "completed", "deleted": true}
                ]
              }
            """.trimIndent()
          } else {
            """
              {
                "items": [
                  {"id": "4", "title": "Sunglasses", "notes": "Polarized", "status": "completed", "hidden": true}
                ]
              }
            """.trimIndent()
          }

          Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(bodyJson.toResponseBody("application/json".toMediaType()))
            .build()
        }
      )
      .build()

    val repo = GoogleWorkspaceRepository(
      tasksBaseUrl = "https://tasks.test/",
      calendarBaseUrl = "https://calendar.test/",
      client = client,
    )

    val result = repo.fetchTasksFromList("fake-token", "list-123")
    assertTrue(result.isSuccess)
    val tasks = result.getOrThrow()

    assertEquals(2, capturedUrls.size)
    assertTrue(capturedUrls[0].contains("showCompleted=true"))
    assertTrue(capturedUrls[0].contains("showHidden=true"))
    assertTrue(capturedUrls[1].contains("pageToken=page_2"))

    assertEquals(listOf("Passport", "Chargers", "Sunglasses"), tasks.map { it.title })
    assertFalse(tasks[0].isCompleted)
    assertTrue(tasks[1].isCompleted)
    assertTrue(tasks[2].isCompleted)
    assertEquals("Polarized", tasks[2].notes)
  }
}
