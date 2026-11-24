package com.example.data.local

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Sanitizer
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.CATEGORY_ALWAYS_DO
import com.example.data.model.PackingItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

private val Context.categoryDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "packwise_categories",
)

class CategoryPreferences(private val context: Context) {

  private val appContext = context.applicationContext

  val customCategoriesFlow: Flow<List<String>> = appContext.categoryDataStore.data.map { prefs ->
    prefs[KEY_CUSTOM_CATEGORIES].orEmpty()
      .map { it.trim() }
      .filter { it.isNotBlank() }
      .sortedBy { it.lowercase(Locale.ROOT) }
  }

  suspend fun addCustomCategory(name: String) {
    val clean = name.trim().take(60)
    if (clean.isBlank() || clean.equals(CATEGORY_ALWAYS_DO, ignoreCase = true)) return
    appContext.categoryDataStore.edit { prefs ->
      val current = prefs[KEY_CUSTOM_CATEGORIES].orEmpty()
        .filterNot { it.equals(clean, ignoreCase = true) }
        .toSet()
      prefs[KEY_CUSTOM_CATEGORIES] = current + clean
    }
  }

  suspend fun renameCustomCategory(oldName: String, newName: String) {
    val cleanNew = newName.trim().take(60)
    if (cleanNew.isBlank() || cleanNew.equals(CATEGORY_ALWAYS_DO, ignoreCase = true)) return
    appContext.categoryDataStore.edit { prefs ->
      val current = prefs[KEY_CUSTOM_CATEGORIES].orEmpty()
      val updated = current
        .filterNot { it.equals(oldName.trim(), ignoreCase = true) || it.equals(cleanNew, ignoreCase = true) }
        .toMutableSet()
      updated.add(cleanNew)
      prefs[KEY_CUSTOM_CATEGORIES] = updated
    }
  }

  suspend fun removeCustomCategory(name: String) {
    val clean = name.trim()
    if (clean.isBlank()) return
    appContext.categoryDataStore.edit { prefs ->
      val current = prefs[KEY_CUSTOM_CATEGORIES].orEmpty()
      prefs[KEY_CUSTOM_CATEGORIES] = current.filterNot { it.equals(clean, ignoreCase = true) }.toSet()
    }
  }

  companion object {
    private val KEY_CUSTOM_CATEGORIES = stringSetPreferencesKey("custom_packing_categories")

    val DEFAULT_PACKING_CATEGORIES: List<String> = listOf(
      "Essentials",
      "Documents",
      "Clothing",
      "Outerwear",
      "Footwear",
      "Weather Gear",
      "Activity Gear",
      "Tech",
      "Toiletries",
      "Health",
    )

    fun displayCategoryFor(item: PackingItem): String =
      if (item.isAlwaysDo) {
        CATEGORY_ALWAYS_DO
      } else {
        item.category.trim().ifBlank { "Essentials" }
      }

    fun categorySortOrder(categoryName: String): Int {
      if (categoryName.equals(CATEGORY_ALWAYS_DO, ignoreCase = true)) return -1
      val idx = DEFAULT_PACKING_CATEGORIES.indexOfFirst { it.equals(categoryName.trim(), ignoreCase = true) }
      return if (idx >= 0) idx else DEFAULT_PACKING_CATEGORIES.size + 1
    }

    fun mergeAvailableCategories(
      activeCategories: Collection<String>,
      customCategories: Collection<String>,
    ): List<String> {
      val seen = mutableSetOf<String>()
      val merged = mutableListOf<String>()
      (DEFAULT_PACKING_CATEGORIES + customCategories + activeCategories).forEach { raw ->
        val clean = raw.trim()
        if (clean.isNotBlank() &&
          !clean.equals(CATEGORY_ALWAYS_DO, ignoreCase = true) &&
          seen.add(clean.lowercase(Locale.ROOT))
        ) {
          merged.add(clean)
        }
      }
      return merged
    }

    fun iconForCategory(categoryName: String): ImageVector =
      when (categoryName.trim().lowercase(Locale.ROOT)) {
        CATEGORY_ALWAYS_DO.lowercase(Locale.ROOT), "always-do" -> Icons.Rounded.Home
        "essentials", "always bring" -> Icons.Rounded.Inventory2
        "documents", "id & travel" -> Icons.Rounded.Badge
        "clothing", "apparel", "outfits" -> Icons.Rounded.Checkroom
        "outerwear", "cold weather" -> Icons.Rounded.AcUnit
        "footwear", "shoes" -> Icons.Rounded.DirectionsWalk
        "weather gear", "rain gear" -> Icons.Rounded.WbSunny
        "activity gear", "activities", "sports" -> Icons.Rounded.Hiking
        "tech", "electronics", "gadgets" -> Icons.Rounded.Devices
        "toiletries", "personal care" -> Icons.Rounded.Sanitizer
        "health", "medical", "first aid" -> Icons.Rounded.MedicalServices
        else -> Icons.Rounded.Label
      }
  }
}
