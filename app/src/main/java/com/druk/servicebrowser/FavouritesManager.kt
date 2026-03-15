package com.druk.servicebrowser

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.graphics.drawable.IconCompat
import com.druk.servicebrowser.ui.RegTypeActivity

class FavouritesManager(private val context: Context) {

    private val sharedPreferences = context.getSharedPreferences("DEFAULT", Context.MODE_PRIVATE)
    private val regTypeManager = BonjourApplication.getRegTypeManager(context)
    private val favouriteRegTypes = HashSet(sharedPreferences.all.keys)

    fun getFavouriteRegTypes(): Set<String> = HashSet(favouriteRegTypes)

    fun isFavourite(regType: String): Boolean = regType in favouriteRegTypes

    fun addToFavourites(regType: String) {
        if (favouriteRegTypes.add(regType)) {
            sharedPreferences.edit().putBoolean(regType, true).apply()
            updateDynamicShortcuts()
        }
    }

    fun removeFromFavourites(regType: String) {
        if (favouriteRegTypes.remove(regType)) {
            sharedPreferences.edit().remove(regType).apply()
            updateDynamicShortcuts()
        }
    }

    internal fun updateDynamicShortcuts() {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return

        val shortcuts = favouriteRegTypes.map { regType ->
            val fullNameRegType = regTypeManager.getRegTypeDescription(regType) ?: regType
            ShortcutInfoCompat.Builder(context, regType)
                .setShortLabel(fullNameRegType)
                .setLongLabel(fullNameRegType)
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_star_accent))
                .setIntent(
                    RegTypeActivity.createIntent(context, regType, Config.LOCAL_DOMAIN)
                        .setAction(Intent.ACTION_VIEW)
                )
                .build()
                .toShortcutInfo()
        }

        shortcutManager.dynamicShortcuts = shortcuts
    }
}
