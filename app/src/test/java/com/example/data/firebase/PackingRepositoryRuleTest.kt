package com.example.data.firebase

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.DraftPackingItem
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PackingRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun createTripAndItems_authenticatedOwner_succeedsAndObservesRealtime() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = PackingRepository(firestore, auth)

    val drafts = listOf(
      DraftPackingItem(
        name = "Passport",
        category = "Documents",
        quantity = 1L,
        isAlwaysBring = true,
        contextReason = "Always Bring",
        sourceType = "always_bring",
      ),
      DraftPackingItem(
        name = "Rain Shell",
        category = "Outerwear",
        quantity = 1L,
        isAlwaysBring = false,
        contextReason = "60% rain forecast",
        sourceType = "weather",
      ),
    )

    val tripId = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.createTripWithItems(
        destination = "Reykjavik",
        country = "Iceland",
        latitude = 64.1466,
        longitude = -21.9426,
        startDateEpochDay = 20770L,
        endDateEpochDay = 20774L,
        durationDays = 5L,
        activities = listOf("Hiking", "Hot Springs"),
        calendarEventsSummary = listOf("Golden Circle Tour"),
        weatherSummary = "2C-7C, 60% rain",
        weatherTempMinC = 2.0,
        weatherTempMaxC = 7.0,
        weatherRainProbMax = 60L,
        weatherAlertActive = true,
        weatherAlertMessage = "Pack waterproof jacket",
        weatherNotificationsEnabled = true,
        draftItems = drafts,
      ).getOrThrow()
    }

    assertTrue(tripId.isNotBlank())

    val observedTrips = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeTrips(uid).first { trips -> trips.any { it.id == tripId } }
    }
    assertTrue(observedTrips.any { it.id == tripId && it.destination == "Reykjavik" })

    val items = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeTripItems(uid, tripId).first { list -> list.size >= 2 }
    }
    assertEquals(2, items.size)

    // Toggle packed, then reset trip after journey
    val firstItem = items.first()
    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.toggleItemPacked(tripId, firstItem.id, true).getOrThrow()
    }
    val packedItems = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getTripItemsOnce(tripId) }
    assertTrue(packedItems.first { it.id == firstItem.id }.isPacked)

    val trip = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getTripOnce(tripId) }
    assertNotNull(trip)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.completeAndResetTrip(trip!!, packedItems, reopenImmediately = false).getOrThrow()
    }

    val resetTrip = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getTripOnce(tripId) }
    assertEquals("completed", resetTrip?.status)
    assertEquals(1L, resetTrip?.resetCount)
    val resetItems = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getTripItemsOnce(tripId) }
    assertTrue(resetItems.all { !it.isPacked })
  }

  @Test
  fun alwaysBring_authenticatedOwner_canAddAndObserve() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = PackingRepository(firestore, auth)

    val itemId = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.addAlwaysBringItem("Universal Adapter", "Tech", 1L, "For EU/JP", "custom").getOrThrow()
    }
    assertTrue(itemId.isNotBlank())

    val list = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeAlwaysBringItems(uid).first { items -> items.any { it.id == itemId } }
    }
    assertFalse(list.isEmpty())
  }

  @Test
  fun observeTrips_crossUserAccess_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = PackingRepository(firestore, auth)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.addAlwaysBringItem("Laptop", "Tech", 1L).getOrThrow()
    }

    signInTestUser(BOB_EMAIL)
    val bobRepo = PackingRepository(firestore, auth)

    var caught: Throwable? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        bobRepo.observeTrips(aliceUid).first()
      }
    } catch (t: Throwable) {
      caught = t
    }
    val firestoreException = caught?.unwrapFirestoreException()
    assertNotNull("Expected FirebaseFirestoreException, got $caught", firestoreException)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException!!.code)
  }

  @Test
  fun observeAlwaysBring_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    auth.signOut()
    val repo = PackingRepository(firestore, auth)

    var caught: Throwable? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeAlwaysBringItems(aliceUid).first()
      }
    } catch (t: Throwable) {
      caught = t
    }
    val firestoreException = caught?.unwrapFirestoreException()
    assertNotNull("Expected FirebaseFirestoreException, got $caught", firestoreException)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException!!.code)
  }

  private companion object {
    const val ALICE_EMAIL = "alice.packwise@test.com"
    const val BOB_EMAIL = "bob.packwise@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3000L
  }
}
