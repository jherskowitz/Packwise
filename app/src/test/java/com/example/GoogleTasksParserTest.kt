package com.example

import com.example.data.remote.GoogleWorkspaceRepository
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
}
