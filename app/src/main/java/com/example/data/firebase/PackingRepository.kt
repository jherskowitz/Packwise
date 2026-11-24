package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.AlwaysBringItem
import com.example.data.model.CATEGORY_ALWAYS_DO
import com.example.data.model.DraftPackingItem
import com.example.data.model.PackingItem
import com.example.data.model.SOURCE_ALWAYS_BRING
import com.example.data.model.SOURCE_ALWAYS_DO
import com.example.data.model.TravelGuideInfo
import com.example.data.model.Trip
import com.example.data.model.TripItemScope
import com.example.data.model.VaultItemType
import com.example.data.model.WeatherForecastSummary
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.CollectionReference
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
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

class PackingRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    db = FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private fun requireUserId(): String {
    return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
  }

  private fun tripsCol(uid: String): CollectionReference =
    db.collection("users").document(uid).collection("trips")

  private fun tripItemsCol(uid: String, tripId: String): CollectionReference =
    tripsCol(uid).document(tripId).collection("items")

  private fun alwaysBringCol(uid: String): CollectionReference =
    db.collection("users").document(uid).collection("alwaysBring")

  private fun <T> Flow<T>.handleSnapshotErrors(
    uid: String,
    operationType: OperationType,
    path: String,
  ): Flow<T> = catch { error ->
    val firestoreError = generateSequence(error) { it.cause }
      .filterIsInstance<FirebaseFirestoreException>()
      .firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    val toLog = firestoreError ?: (error as? Exception)
    if (toLog != null && auth.currentUser?.uid == uid) {
      handleFirestoreError(toLog, operationType, path, auth)
    }
    throw error
  }

  fun observeTrips(userId: String): Flow<List<Trip>> = flow {
    if (userId.isBlank()) throw IllegalStateException("User ID is required.")
    val path = "users/$userId/trips"
    emitAll(
      tripsCol(userId)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(Trip::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.startDateEpochDay }
        }
        .handleSnapshotErrors(userId, OperationType.LIST, path)
    )
  }

  fun observeTripItems(userId: String, tripId: String): Flow<List<PackingItem>> = flow {
    if (userId.isBlank() || tripId.isBlank()) {
      emit(emptyList())
      return@flow
    }
    val path = "users/$userId/trips/$tripId/items"
    emitAll(
      tripItemsCol(userId, tripId)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(PackingItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedWith(compareBy<PackingItem> { it.isPacked }.thenBy { it.category }.thenBy { it.name })
        }
        .handleSnapshotErrors(userId, OperationType.LIST, path)
    )
  }

  fun observeAlwaysBringItems(userId: String): Flow<List<AlwaysBringItem>> = flow {
    if (userId.isBlank()) throw IllegalStateException("User ID is required.")
    val path = "users/$userId/alwaysBring"
    emitAll(
      alwaysBringCol(userId)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(AlwaysBringItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedWith(compareBy<AlwaysBringItem> { it.category }.thenBy { it.name })
        }
        .handleSnapshotErrors(userId, OperationType.LIST, path)
    )
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
    knownAlwaysBring: List<AlwaysBringItem> = emptyList(),
    headerImageUrl: String = "",
  ): Result<String> {
    var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/trips"
    return try {
      val uid = requireUserId()
      val tripRef = tripsCol(uid).document()
      val tripId = tripRef.id
      path = tripRef.path

      val alwaysBringExisting = knownAlwaysBring.ifEmpty {
        withTimeoutOrNull(3_000L) {
          alwaysBringCol(uid).get().await()
            .toObjects(AlwaysBringItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }.orEmpty()
      }

      val dedupedNames = mutableSetOf<String>()
      val combinedDrafts = mutableListOf<DraftPackingItem>()

      alwaysBringExisting.forEach { ab ->
        val key = ab.name.trim().lowercase(Locale.ROOT)
        if (key.isNotBlank() && dedupedNames.add(key)) {
          if (ab.isAlwaysDo) {
            combinedDrafts.add(
              DraftPackingItem(
                name = ab.name.trim(),
                category = CATEGORY_ALWAYS_DO,
                quantity = 1L,
                isAlwaysBring = false,
                contextReason = ab.notes.ifBlank { "Before leaving home" },
                sourceType = SOURCE_ALWAYS_DO,
              )
            )
          } else {
            combinedDrafts.add(
              DraftPackingItem(
                name = ab.name.trim(),
                category = ab.category.ifBlank { "Always Bring" },
                quantity = ab.quantity.coerceIn(1L, 99L),
                isAlwaysBring = true,
                contextReason = ab.notes.ifBlank { "Always Bring essential" },
                sourceType = SOURCE_ALWAYS_BRING,
              )
            )
          }
        }
      }

      draftItems.forEach { draft ->
        val key = draft.name.trim().lowercase(Locale.ROOT)
        if (key.isNotBlank() && dedupedNames.add(key)) {
          combinedDrafts.add(draft.copy(name = draft.name.trim()))
        }
      }

      val tripPayload = mapOf(
        "userId" to uid,
        "destination" to destination.trim().take(120),
        "country" to country.trim().take(80),
        "latitude" to latitude,
        "longitude" to longitude,
        "startDateEpochDay" to startDateEpochDay,
        "endDateEpochDay" to endDateEpochDay,
        "durationDays" to durationDays.coerceIn(1L, 365L),
        "activities" to activities.take(15),
        "calendarEventsSummary" to calendarEventsSummary.take(25),
        "weatherSummary" to weatherSummary.take(400),
        "weatherTempMinC" to weatherTempMinC,
        "weatherTempMaxC" to weatherTempMaxC,
        "weatherRainProbMax" to weatherRainProbMax.coerceIn(0L, 100L),
        "weatherAlertActive" to weatherAlertActive,
        "weatherAlertMessage" to weatherAlertMessage.take(400),
        "weatherNotificationsEnabled" to weatherNotificationsEnabled,
        "status" to "active",
        "resetCount" to 0L,
        "headerImageUrl" to headerImageUrl.trim().take(950),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )

      val batch = db.batch()
      batch.set(tripRef, tripPayload)

      combinedDrafts.take(250).forEach { draft ->
        val itemRef = tripItemsCol(uid, tripId).document()
        batch.set(
          itemRef,
          mapOf(
            "userId" to uid,
            "tripId" to tripId,
            "name" to draft.name.take(120),
            "category" to draft.category.ifBlank { "General" }.take(60),
            "quantity" to draft.quantity.coerceIn(1L, 99L),
            "isPacked" to false,
            "isAlwaysBring" to draft.isAlwaysBring,
            "sourceType" to draft.sourceType.ifBlank { "ai" }.take(40),
            "contextReason" to draft.contextReason.take(200),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
      }

      withTimeout(12_000L) {
        batch.commit().await()
      }
      Result.success(tripId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path, auth)
      Result.failure(e)
    }
  }

  suspend fun addPackingItem(tripId: String, item: DraftPackingItem): Result<Unit> =
    addPackingItemsBatch(tripId, listOf(item)).map { Unit }

  suspend fun addPackingItemsBatch(tripId: String, items: List<DraftPackingItem>): Result<Int> {
    var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/trips/$tripId/items"
    return try {
      val uid = requireUserId()
      require(tripId.isNotBlank()) { "Trip ID must not be blank" }
      if (items.isEmpty()) return Result.success(0)

      val batch = db.batch()
      var count = 0
      items.take(200).forEach { draft ->
        val trimmed = draft.name.trim()
        if (trimmed.isNotBlank()) {
          val docRef = tripItemsCol(uid, tripId).document()
          path = docRef.path
          batch.set(
            docRef,
            mapOf(
              "userId" to uid,
              "tripId" to tripId,
              "name" to trimmed.take(120),
              "category" to draft.category.ifBlank { "General" }.take(60),
              "quantity" to draft.quantity.coerceIn(1L, 99L),
              "isPacked" to false,
              "isAlwaysBring" to draft.isAlwaysBring,
              "sourceType" to draft.sourceType.ifBlank { "manual" }.take(40),
              "contextReason" to draft.contextReason.take(200),
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          count++
        }
      }
      if (count > 0) batch.commit().await()
      Result.success(count)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path, auth)
      Result.failure(e)
    }
  }

  suspend fun toggleItemPacked(tripId: String, itemId: String, isPacked: Boolean): Result<Unit> {
    var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/trips/$tripId/items/$itemId"
    return try {
      val uid = requireUserId()
      val docRef = tripItemsCol(uid, tripId).document(itemId)
      path = docRef.path
      docRef.update(
        mapOf(
          "isPacked" to isPacked,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path, auth)
      Result.failure(e)
    }
  }

  suspend fun deletePackingItem(tripId: String, itemId: String): Result<Unit> {
    var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/trips/$tripId/items/$itemId"
    return try {
      val uid = requireUserId()
      val docRef = tripItemsCol(uid, tripId).document(itemId)
      path = docRef.path
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, path, auth)
      Result.failure(e)
    }
  }

  suspend fun promoteItemToAlwaysBring(tripId: String, item: PackingItem): Result<Unit> {
    var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/alwaysBring"
    return try {
      val uid = requireUserId()
      val abRef = alwaysBringCol(uid).document()
      val itemRef = tripItemsCol(uid, tripId).document(item.id)
      path = abRef.path

      val batch = db.batch()
      batch.set(
        abRef,
        mapOf(
          "userId" to uid,
          "name" to item.name.trim().take(120),
          "category" to item.category.ifBlank { "Always Bring" }.take(60),
          "quantity" to item.quantity.coerceIn(1L, 99L),
          "notes" to item.contextReason.ifBlank { "Promoted from trip checklist" }.take(200),
          "source" to "custom",
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
      if (item.id.isNotBlank()) {
        batch.update(
          itemRef,
          mapOf(
            "isAlwaysBring" to true,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
      }
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path, auth)
      Result.failure(e)
    }
  }

  suspend fun promoteItemToAlwaysDo(tripId: String, item: PackingItem): Result<Unit> {
    var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/alwaysBring"
    return try {
      val uid = requireUserId()
      val abRef = alwaysBringCol(uid).document()
      val itemRef = tripItemsCol(uid, tripId).document(item.id)
      path = abRef.path

      val batch = db.batch()
      batch.set(
        abRef,
        mapOf(
          "userId" to uid,
          "name" to item.name.trim().take(120),
          "category" to CATEGORY_ALWAYS_DO,
          "quantity" to 1L,
          "notes" to item.contextReason.ifBlank { "Before leaving home on every trip" }.take(200),
          "source" to SOURCE_ALWAYS_DO,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
      if (item.id.isNotBlank()) {
        batch.update(
          itemRef,
          mapOf(
            "category" to CATEGORY_ALWAYS_DO,
            "isAlwaysBring" to false,
            "sourceType" to SOURCE_ALWAYS_DO,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
      }
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path, auth)
      Result.failure(e)
    }
  }

  suspend fun recategorizeTripItem(
    tripId: String,
    item: PackingItem,
    newScope: TripItemScope,
    newCategory: String,
    saveToVault: Boolean,
  ): Result<Unit> {
    var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/trips/$tripId/items/${item.id}"
    return try {
      val uid = requireUserId()
      require(tripId.isNotBlank() && item.id.isNotBlank()) { "Trip ID and Item ID are required." }

      val resolvedCategory = when (newScope) {
        TripItemScope.ALWAYS_DO -> CATEGORY_ALWAYS_DO
        else -> newCategory.trim().ifBlank { "Essentials" }.take(60)
      }
      val resolvedIsAlwaysBring = newScope == TripItemScope.ALWAYS_BRING
      val resolvedSourceType = when (newScope) {
        TripItemScope.ALWAYS_DO -> SOURCE_ALWAYS_DO
        TripItemScope.ALWAYS_BRING -> SOURCE_ALWAYS_BRING
        TripItemScope.TRIP_SPECIFIC -> if (item.sourceType in setOf(SOURCE_ALWAYS_DO, SOURCE_ALWAYS_BRING)) {
          "manual"
        } else {
          item.sourceType.ifBlank { "manual" }
        }
      }
      val resolvedReason = when {
        newScope == TripItemScope.ALWAYS_DO &&
          (item.contextReason.isBlank() || item.contextReason == "Always Bring essential") ->
          "Before leaving home on every trip"
        newScope == TripItemScope.ALWAYS_BRING &&
          (item.contextReason.isBlank() || item.contextReason == "Before leaving home") ->
          "Always Bring essential"
        else -> item.contextReason
      }.take(200)

      val itemRef = tripItemsCol(uid, tripId).document(item.id)
      path = itemRef.path

      val batch = db.batch()
      batch.update(
        itemRef,
        mapOf(
          "category" to resolvedCategory,
          "isAlwaysBring" to resolvedIsAlwaysBring,
          "sourceType" to resolvedSourceType,
          "contextReason" to resolvedReason,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )

      if (saveToVault && (newScope == TripItemScope.ALWAYS_BRING || newScope == TripItemScope.ALWAYS_DO)) {
        val vaultTargetSource = if (newScope == TripItemScope.ALWAYS_DO) SOURCE_ALWAYS_DO else "custom"
        val existingVaultDoc = withTimeoutOrNull(2_000L) {
          alwaysBringCol(uid).whereEqualTo("name", item.name).limit(1).get().await().documents.firstOrNull()
        }
        if (existingVaultDoc != null) {
          batch.update(
            existingVaultDoc.reference,
            mapOf(
              "category" to resolvedCategory,
              "source" to vaultTargetSource,
              "notes" to resolvedReason.ifBlank {
                if (newScope == TripItemScope.ALWAYS_DO) "Before leaving home on every trip" else "Always bring on every trip"
              }.take(200),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
        } else {
          val abRef = alwaysBringCol(uid).document()
          batch.set(
            abRef,
            mapOf(
              "userId" to uid,
              "name" to item.name.trim().take(120),
              "category" to resolvedCategory,
              "quantity" to if (newScope == TripItemScope.ALWAYS_DO) 1L else item.quantity.coerceIn(1L, 99L),
              "notes" to resolvedReason.ifBlank {
                if (newScope == TripItemScope.ALWAYS_DO) "Before leaving home on every trip" else "Always bring on every trip"
              }.take(200),
              "source" to vaultTargetSource,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
        }
      }

      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path, auth)
      Result.failure(e)
    }
  }

  suspend fun recategorizeVaultItem(
    item: AlwaysBringItem,
    targetType: VaultItemType,
    newCategory: String,
    newNotes: String,
  ): Result<Unit> {
    var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/alwaysBring/${item.id}"
    return try {
      val uid = requireUserId()
      require(item.id.isNotBlank()) { "Vault item ID must not be blank" }
      val docRef = alwaysBringCol(uid).document(item.id)
      path = docRef.path

      val resolvedCategory = when (targetType) {
        VaultItemType.ALWAYS_DO -> CATEGORY_ALWAYS_DO
        VaultItemType.ALWAYS_BRING -> newCategory.trim().ifBlank { "Essentials" }
          .let { if (it.equals(CATEGORY_ALWAYS_DO, ignoreCase = true)) "Essentials" else it }
          .take(60)
      }
      val resolvedSource = when (targetType) {
        VaultItemType.ALWAYS_DO -> SOURCE_ALWAYS_DO
        VaultItemType.ALWAYS_BRING -> if (item.source == SOURCE_ALWAYS_DO) "custom" else item.source.ifBlank { "custom" }
      }
      val resolvedNotes = newNotes.trim().ifBlank {
        if (targetType == VaultItemType.ALWAYS_DO) "Before leaving home on every trip" else "Always bring on every trip"
      }.take(200)

      docRef.update(
        mapOf(
          "category" to resolvedCategory,
          "source" to resolvedSource,
          "notes" to resolvedNotes,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()

      // Best-effort sync to matching-name items inside active trips so existing checklists stay consistent.
      runCatching {
        withTimeoutOrNull(3_000L) {
          val activeTrips = tripsCol(uid).whereEqualTo("status", "active").get().await().documents
          if (activeTrips.isNotEmpty()) {
            val syncBatch = db.batch()
            var hasUpdates = false
            activeTrips.take(10).forEach { tripDoc ->
              val matches = tripItemsCol(uid, tripDoc.id)
                .whereEqualTo("name", item.name)
                .get()
                .await()
                .documents
              matches.forEach { itemDoc ->
                hasUpdates = true
                syncBatch.update(
                  itemDoc.reference,
                  mapOf(
                    "category" to resolvedCategory,
                    "isAlwaysBring" to (targetType == VaultItemType.ALWAYS_BRING),
                    "sourceType" to if (targetType == VaultItemType.ALWAYS_DO) SOURCE_ALWAYS_DO else SOURCE_ALWAYS_BRING,
                    "contextReason" to resolvedNotes,
                    "updatedAt" to FieldValue.serverTimestamp(),
                  )
                )
              }
            }
            if (hasUpdates) syncBatch.commit().await()
          }
        }
      }

      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path, auth)
      Result.failure(e)
    }
  }

  suspend fun updateTripWeather(tripId: String, forecast: WeatherForecastSummary): Result<Unit> {
    var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/trips/$tripId"
    return try {
      val uid = requireUserId()
      val tripRef = tripsCol(uid).document(tripId)
      path = tripRef.path
      tripRef.update(
        mapOf(
          "weatherSummary" to forecast.summaryText.take(400),
          "weatherTempMinC" to forecast.tempMinC,
          "weatherTempMaxC" to forecast.tempMaxC,
          "weatherRainProbMax" to forecast.rainProbMax.coerceIn(0L, 100L),
          "weatherAlertActive" to forecast.alertActive,
          "weatherAlertMessage" to forecast.alertMessage.take(400),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path, auth)
      Result.failure(e)
    }
  }

  suspend fun updateTripTravelGuide(tripId: String, guide: TravelGuideInfo): Result<Unit> {
    var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/trips/$tripId"
    return try {
      val uid = requireUserId()
      val tripRef = tripsCol(uid).document(tripId)
      path = tripRef.path
      tripRef.update(
        mapOf(
          "travelGuideJson" to guide.toJsonString().take(7_800),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path, auth)
      Result.failure(e)
    }
  }

  suspend fun updateTripHeaderImage(tripId: String, headerImageUrl: String): Result<Unit> {
    if (headerImageUrl.isBlank()) return Result.success(Unit)
    var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/trips/$tripId"
    return try {
      val uid = requireUserId()
      val tripRef = tripsCol(uid).document(tripId)
      path = tripRef.path
      tripRef.update(
        mapOf(
          "headerImageUrl" to headerImageUrl.trim().take(950),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path, auth)
      Result.failure(e)
    }
  }

  suspend fun updateTripNotifications(tripId: String, enabled: Boolean): Result<Unit> {
    var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/trips/$tripId"
    return try {
      val uid = requireUserId()
      val tripRef = tripsCol(uid).document(tripId)
      path = tripRef.path
      tripRef.update(
        mapOf(
          "weatherNotificationsEnabled" to enabled,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path, auth)
      Result.failure(e)
    }
  }

  suspend fun completeAndResetTrip(
    trip: Trip,
    items: List<PackingItem>,
    markCompletedStatus: Boolean = false,
  ): Result<Unit> {
    var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/trips/${trip.id}"
    return try {
      val uid = requireUserId()
      val tripRef = tripsCol(uid).document(trip.id)
      path = tripRef.path

      val batch = db.batch()
      items.filter { it.isPacked && it.id.isNotBlank() }.forEach { item ->
        batch.update(
          tripItemsCol(uid, trip.id).document(item.id),
          mapOf(
            "isPacked" to false,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
      }
      batch.update(
        tripRef,
        mapOf(
          "status" to if (markCompletedStatus) "completed" else "active",
          "resetCount" to (trip.resetCount + 1L),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path, auth)
      Result.failure(e)
    }
  }

  suspend fun deleteTrip(tripId: String): Result<Unit> {
    var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/trips/$tripId"
    return try {
      val uid = requireUserId()
      val tripRef = tripsCol(uid).document(tripId)
      path = tripRef.path
      val itemDocs = tripItemsCol(uid, tripId).get().await().documents
      val batch = db.batch()
      itemDocs.forEach { batch.delete(it.reference) }
      batch.delete(tripRef)
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, path, auth)
      Result.failure(e)
    }
  }

  suspend fun seedDefaultAlwaysBringIfEmpty(): Result<Unit> {
    var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/alwaysBring"
    return try {
      val uid = requireUserId()
      val col = alwaysBringCol(uid)
      path = col.path
      val existing = col.limit(1).get().await()
      if (!existing.isEmpty) return Result.success(Unit)

      val defaults = listOf(
        Triple("Passport / Government ID", "Documents", 1L),
        Triple("Phone Charger & USB-C Cable", "Tech", 1L),
        Triple("Daily Medications", "Health", 1L),
        Triple("Toothbrush & Toothpaste", "Toiletries", 1L),
        Triple("Reusable Water Bottle", "Essentials", 1L),
        Triple("Earbuds / Headphones", "Tech", 1L),
      )

      val batch = db.batch()
      defaults.forEach { (name, cat, qty) ->
        val docRef = col.document()
        batch.set(
          docRef,
          mapOf(
            "userId" to uid,
            "name" to name,
            "category" to cat,
            "quantity" to qty,
            "notes" to "Always bring on every trip",
            "source" to "default",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
      }
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path, auth)
      Result.failure(e)
    }
  }

  suspend fun addAlwaysBringItem(
    name: String,
    category: String,
    quantity: Long = 1L,
    notes: String = "Always bring on every trip",
    source: String = "custom",
  ): Result<Unit> {
    var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/alwaysBring"
    return try {
      val uid = requireUserId()
      val docRef = alwaysBringCol(uid).document()
      path = docRef.path
      docRef.set(
        mapOf(
          "userId" to uid,
          "name" to name.trim().take(120),
          "category" to category.ifBlank { "Essentials" }.take(60),
          "quantity" to quantity.coerceIn(1L, 99L),
          "notes" to notes.take(200),
          "source" to source.ifBlank { "custom" }.take(40),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path, auth)
      Result.failure(e)
    }
  }

  suspend fun addAlwaysBringItemsBatch(
    items: List<Triple<String, String, String>>,
    isAlwaysDo: Boolean = false,
  ): Result<Int> {
    var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/alwaysBring"
    return try {
      val uid = requireUserId()
      if (items.isEmpty()) return Result.success(0)
      val batch = db.batch()
      var count = 0
      items.take(200).forEach { (name, cat, notes) ->
        val trimmed = name.trim()
        if (trimmed.isNotBlank()) {
          val docRef = alwaysBringCol(uid).document()
          path = docRef.path
          batch.set(
            docRef,
            mapOf(
              "userId" to uid,
              "name" to trimmed.take(120),
              "category" to if (isAlwaysDo) CATEGORY_ALWAYS_DO else cat.ifBlank { "Imported" }.take(60),
              "quantity" to 1L,
              "notes" to notes.ifBlank {
                if (isAlwaysDo) "Before leaving home on every trip" else "Always bring on every trip"
              }.take(200),
              "source" to if (isAlwaysDo) SOURCE_ALWAYS_DO else "google_tasks",
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          count++
        }
      }
      if (count > 0) batch.commit().await()
      Result.success(count)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path, auth)
      Result.failure(e)
    }
  }

  suspend fun deleteAlwaysBringItem(itemId: String): Result<Unit> {
    var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/alwaysBring/$itemId"
    return try {
      val uid = requireUserId()
      val docRef = alwaysBringCol(uid).document(itemId)
      path = docRef.path
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, path, auth)
      Result.failure(e)
    }
  }

  suspend fun getActiveTripsOnce(): List<Trip> {
    val uid = auth.currentUser?.uid?.takeIf { it.isNotBlank() } ?: return emptyList()
    return try {
      tripsCol(uid).get().await()
        .toObjects(Trip::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.LIST, "users/$uid/trips", auth)
      emptyList()
    }
  }

  suspend fun getTripOnce(tripId: String): Trip? {
    val uid = auth.currentUser?.uid?.takeIf { it.isNotBlank() } ?: return null
    return try {
      tripsCol(uid).document(tripId).get().await()
        .toObject(Trip::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, "users/$uid/trips/$tripId", auth)
      null
    }
  }

  suspend fun getTripItemsOnce(tripId: String): List<PackingItem> {
    val uid = auth.currentUser?.uid?.takeIf { it.isNotBlank() } ?: return emptyList()
    return try {
      withTimeoutOrNull(4_000L) {
        tripItemsCol(uid, tripId).get().await()
          .toObjects(PackingItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }.orEmpty()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.LIST, "users/$uid/trips/$tripId/items", auth)
      emptyList()
    }
  }
}
