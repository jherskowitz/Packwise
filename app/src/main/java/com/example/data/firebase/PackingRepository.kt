package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.AlwaysBringItem
import com.example.data.model.DraftPackingItem
import com.example.data.model.PackingItem
import com.example.data.model.Trip
import com.example.data.model.WeatherForecastSummary
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class PackingRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    ),
    Firebase.auth,
  )

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
  }

  private fun alwaysBringCol(uid: String) =
    db.collection("users").document(uid).collection("alwaysBring")

  private fun tripsCol(uid: String) =
    db.collection("users").document(uid).collection("trips")

  private fun tripItemsCol(uid: String, tripId: String) =
    tripsCol(uid).document(tripId).collection("items")

  private fun <T> Flow<T>.handleSnapshotErrors(
    uid: String,
    operationType: OperationType,
    path: String,
  ): Flow<T> = catch { error ->
    val firestoreError = generateSequence(error) { it.cause }
      .filterIsInstance<FirebaseFirestoreException>()
      .firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    val exceptionToLog = firestoreError ?: (error as? Exception)
    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
      handleFirestoreError(exceptionToLog, operationType, path, auth)
    }
    throw error
  }

  // --- ALWAYS BRING ESSENTIALS ---

  fun observeAlwaysBringItems(userId: String): Flow<List<AlwaysBringItem>> = flow {
    val path = "users/$userId/alwaysBring"
    emitAll(
      alwaysBringCol(userId)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            AlwaysBringItem::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
          ).sortedBy { it.category + it.name }
        }
        .handleSnapshotErrors(userId, OperationType.LIST, path)
    )
  }

  suspend fun seedDefaultAlwaysBringIfEmpty(): Result<Unit> = runCatching {
    val uid = requireUserId()
    val path = "users/$uid/alwaysBring"
    try {
      val existing = alwaysBringCol(uid).limit(1).get().await()
      if (!existing.isEmpty) return@runCatching
      val defaults = listOf(
        Triple("Passport / Government ID", "Documents", 1L),
        Triple("Phone Charger & USB-C Cable", "Tech", 1L),
        Triple("Toothbrush & Toothpaste", "Toiletries", 1L),
        Triple("Daily Medications", "Health", 1L),
        Triple("Reusable Water Bottle", "Essentials", 1L),
        Triple("Earbuds / Headphones", "Tech", 1L),
      )
      val batch = db.batch()
      defaults.forEach { (name, category, qty) ->
        val ref = alwaysBringCol(uid).document()
        batch.set(
          ref,
          mapOf(
            "userId" to uid,
            "name" to name,
            "category" to category,
            "quantity" to qty,
            "notes" to "Always bring on every trip",
            "source" to "default",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
      }
      batch.commit().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path, auth)
      throw e
    }
  }

  suspend fun addAlwaysBringItem(
    name: String,
    category: String,
    quantity: Long = 1L,
    notes: String = "",
    source: String = "custom",
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val docRef = alwaysBringCol(uid).document()
    val payload = mapOf(
      "userId" to uid,
      "name" to name.trim().take(120),
      "category" to category.trim().ifBlank { "Essentials" }.take(40),
      "quantity" to quantity.coerceIn(1L, 99L),
      "notes" to notes.trim().take(300),
      "source" to source,
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
    )
    try {
      docRef.set(payload).await()
      docRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
      throw e
    }
  }

  suspend fun addAlwaysBringItemsBatch(items: List<Triple<String, String, String>>): Result<Int> = runCatching {
    val uid = requireUserId()
    if (items.isEmpty()) return@runCatching 0
    val batch = db.batch()
    items.take(40).forEach { (name, category, notes) ->
      val docRef = alwaysBringCol(uid).document()
      batch.set(
        docRef,
        mapOf(
          "userId" to uid,
          "name" to name.trim().take(120),
          "category" to category.trim().ifBlank { "Essentials" }.take(40),
          "quantity" to 1L,
          "notes" to notes.trim().take(300),
          "source" to "google_tasks",
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
    }
    try {
      batch.commit().await()
      items.size.coerceAtMost(40)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "users/$uid/alwaysBring", auth)
      throw e
    }
  }

  suspend fun deleteAlwaysBringItem(itemId: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    val docRef = alwaysBringCol(uid).document(itemId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
      throw e
    }
  }

  // --- TRIPS ---

  fun observeTrips(userId: String): Flow<List<Trip>> = flow {
    val path = "users/$userId/trips"
    emitAll(
      tripsCol(userId)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            Trip::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
          ).sortedByDescending { it.startDateEpochDay }
        }
        .handleSnapshotErrors(userId, OperationType.LIST, path)
    )
  }

  suspend fun getActiveTripsOnce(): List<Trip> {
    val uid = requireUserId()
    val path = "users/$uid/trips"
    return try {
      tripsCol(uid)
        .whereEqualTo("status", "active")
        .get()
        .await()
        .toObjects(Trip::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.LIST, path, auth)
      throw e
    }
  }

  suspend fun getTripOnce(tripId: String): Trip? {
    val uid = requireUserId()
    val docRef = tripsCol(uid).document(tripId)
    return try {
      docRef.get().await()
        .toObject(Trip::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path, auth)
      throw e
    }
  }

  suspend fun createTripWithItems(
    destination: String,
    country: String,
    latitude: Double,
    longitude: Double,
    startDateEpochDay: Long,
    endDateEpochDay: Long,
    durationDays: Long,
    activities: List<String>,
    calendarEventsSummary: List<String>,
    weatherSummary: String,
    weatherTempMinC: Double,
    weatherTempMaxC: Double,
    weatherRainProbMax: Long,
    weatherAlertActive: Boolean,
    weatherAlertMessage: String,
    weatherNotificationsEnabled: Boolean,
    draftItems: List<DraftPackingItem>,
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val tripRef = tripsCol(uid).document()
    val tripId = tripRef.id

    val tripPayload = mapOf(
      "userId" to uid,
      "destination" to destination.trim().take(120),
      "country" to country.trim().take(80),
      "latitude" to latitude.coerceIn(-90.0, 90.0),
      "longitude" to longitude.coerceIn(-180.0, 180.0),
      "startDateEpochDay" to startDateEpochDay,
      "endDateEpochDay" to endDateEpochDay.coerceAtLeast(startDateEpochDay),
      "durationDays" to durationDays.coerceIn(1L, 365L),
      "activities" to activities.take(15),
      "calendarEventsSummary" to calendarEventsSummary.take(25),
      "weatherSummary" to weatherSummary.take(300),
      "weatherTempMinC" to weatherTempMinC,
      "weatherTempMaxC" to weatherTempMaxC,
      "weatherRainProbMax" to weatherRainProbMax.coerceIn(0L, 100L),
      "weatherAlertActive" to weatherAlertActive,
      "weatherAlertMessage" to weatherAlertMessage.take(300),
      "status" to "active",
      "resetCount" to 0L,
      "weatherNotificationsEnabled" to weatherNotificationsEnabled,
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
    )

    val batch = db.batch()
    batch.set(tripRef, tripPayload)

    draftItems.take(80).forEach { draft ->
      val itemRef = tripItemsCol(uid, tripId).document()
      batch.set(
        itemRef,
        mapOf(
          "userId" to uid,
          "tripId" to tripId,
          "name" to draft.name.trim().take(140),
          "category" to draft.category.trim().ifBlank { "General" }.take(40),
          "quantity" to draft.quantity.coerceIn(1L, 99L),
          "isPacked" to false,
          "isAlwaysBring" to draft.isAlwaysBring,
          "contextReason" to draft.contextReason.trim().take(200),
          "sourceType" to draft.sourceType,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
    }

    try {
      batch.commit().await()
      tripId
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, tripRef.path, auth)
      throw e
    }
  }

  suspend fun updateTripWeather(
    tripId: String,
    forecast: WeatherForecastSummary,
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    val docRef = tripsCol(uid).document(tripId)
    val updates = mapOf(
      "weatherSummary" to forecast.summaryText.take(300),
      "weatherTempMinC" to forecast.tempMinC,
      "weatherTempMaxC" to forecast.tempMaxC,
      "weatherRainProbMax" to forecast.rainProbMax.coerceIn(0L, 100L),
      "weatherAlertActive" to forecast.alertActive,
      "weatherAlertMessage" to forecast.alertMessage.take(300),
      "updatedAt" to FieldValue.serverTimestamp(),
    )
    try {
      docRef.update(updates).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      throw e
    }
  }

  suspend fun updateTripNotifications(
    tripId: String,
    enabled: Boolean,
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    val docRef = tripsCol(uid).document(tripId)
    try {
      docRef.update(
        mapOf(
          "weatherNotificationsEnabled" to enabled,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      throw e
    }
  }

  /**
   * Resets a trip after completion: marks status = "completed" (or "active" if reusing immediately),
   * increments resetCount, clears transient weather alerts, and resets ALL packing items' isPacked = false
   * so every list (and Always Bring essentials) is fresh for the next journey.
   */
  suspend fun completeAndResetTrip(
    trip: Trip,
    items: List<PackingItem>,
    reopenImmediately: Boolean = false,
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    val tripRef = tripsCol(uid).document(trip.id)
    val batch = db.batch()
    val nextStatus = if (reopenImmediately) "active" else "completed"
    batch.update(
      tripRef,
      mapOf(
        "status" to nextStatus,
        "resetCount" to (trip.resetCount + 1L),
        "weatherAlertActive" to false,
        "weatherAlertMessage" to "",
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    )
    items.filter { it.isPacked }.forEach { item ->
      val itemRef = tripItemsCol(uid, trip.id).document(item.id)
      batch.update(
        itemRef,
        mapOf(
          "isPacked" to false,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
    }
    try {
      batch.commit().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, tripRef.path, auth)
      throw e
    }
  }

  suspend fun deleteTrip(tripId: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    val tripRef = tripsCol(uid).document(tripId)
    try {
      val items = tripItemsCol(uid, tripId).get().await()
      val batch = db.batch()
      items.documents.forEach { batch.delete(it.reference) }
      batch.delete(tripRef)
      batch.commit().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, tripRef.path, auth)
      throw e
    }
  }

  // --- TRIP PACKING ITEMS ---

  fun observeTripItems(userId: String, tripId: String): Flow<List<PackingItem>> = flow {
    val path = "users/$userId/trips/$tripId/items"
    emitAll(
      tripItemsCol(userId, tripId)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            PackingItem::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
          ).sortedWith(compareBy<PackingItem>({ it.isPacked }, { !it.isAlwaysBring }, { it.category }, { it.name }))
        }
        .handleSnapshotErrors(userId, OperationType.LIST, path)
    )
  }

  suspend fun getTripItemsOnce(tripId: String): List<PackingItem> {
    val uid = requireUserId()
    val path = "users/$uid/trips/$tripId/items"
    return try {
      tripItemsCol(uid, tripId)
        .get()
        .await()
        .toObjects(PackingItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.LIST, path, auth)
      throw e
    }
  }

  suspend fun addPackingItem(
    tripId: String,
    draft: DraftPackingItem,
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val docRef = tripItemsCol(uid, tripId).document()
    val payload = mapOf(
      "userId" to uid,
      "tripId" to tripId,
      "name" to draft.name.trim().take(140),
      "category" to draft.category.trim().ifBlank { "General" }.take(40),
      "quantity" to draft.quantity.coerceIn(1L, 99L),
      "isPacked" to false,
      "isAlwaysBring" to draft.isAlwaysBring,
      "contextReason" to draft.contextReason.trim().take(200),
      "sourceType" to draft.sourceType,
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
    )
    try {
      docRef.set(payload).await()
      docRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
      throw e
    }
  }

  suspend fun addPackingItemsBatch(
    tripId: String,
    drafts: List<DraftPackingItem>,
  ): Result<Int> = runCatching {
    val uid = requireUserId()
    if (drafts.isEmpty()) return@runCatching 0
    val batch = db.batch()
    drafts.take(60).forEach { draft ->
      val docRef = tripItemsCol(uid, tripId).document()
      batch.set(
        docRef,
        mapOf(
          "userId" to uid,
          "tripId" to tripId,
          "name" to draft.name.trim().take(140),
          "category" to draft.category.trim().ifBlank { "General" }.take(40),
          "quantity" to draft.quantity.coerceIn(1L, 99L),
          "isPacked" to false,
          "isAlwaysBring" to draft.isAlwaysBring,
          "contextReason" to draft.contextReason.trim().take(200),
          "sourceType" to draft.sourceType,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
    }
    try {
      batch.commit().await()
      drafts.size.coerceAtMost(60)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "users/$uid/trips/$tripId/items", auth)
      throw e
    }
  }

  suspend fun toggleItemPacked(tripId: String, itemId: String, isPacked: Boolean): Result<Unit> = runCatching {
    val uid = requireUserId()
    val docRef = tripItemsCol(uid, tripId).document(itemId)
    try {
      docRef.update(
        mapOf(
          "isPacked" to isPacked,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      throw e
    }
  }

  suspend fun promoteItemToAlwaysBring(tripId: String, item: PackingItem): Result<Unit> = runCatching {
    val uid = requireUserId()
    val itemRef = tripItemsCol(uid, tripId).document(item.id)
    val abRef = alwaysBringCol(uid).document()
    val batch = db.batch()
    batch.update(
      itemRef,
      mapOf(
        "isAlwaysBring" to true,
        "sourceType" to "always_bring",
        "contextReason" to "Always Bring essential",
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    )
    batch.set(
      abRef,
      mapOf(
        "userId" to uid,
        "name" to item.name.take(120),
        "category" to item.category.take(40),
        "quantity" to item.quantity.coerceIn(1L, 99L),
        "notes" to "Promoted from trip checklist",
        "source" to "custom",
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    )
    try {
      batch.commit().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, itemRef.path, auth)
      throw e
    }
  }

  suspend fun deletePackingItem(tripId: String, itemId: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    val docRef = tripItemsCol(uid, tripId).document(itemId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
      throw e
    }
  }
}
