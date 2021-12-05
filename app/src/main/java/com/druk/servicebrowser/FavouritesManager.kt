package com.druk.servicebrowser

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Build
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.graphics.drawable.IconCompat
import com.druk.servicebrowser.ui.RegTypeActivity.Companion.createIntent
import java.util.*

class FavouritesManager internal constructor(private val context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("DEFAULT", Context.MODE_PRIVATE)
    private val regTypeManager: RegTypeManager = BonjourApplication.getRegTypeManager(context)
    private val favouriteRegTypes: MutableSet<String> = HashSet(sharedPreferences.all.keys)

    fun isFavourite(regType: String): Boolean {
        return favouriteRegTypes.contains(regType)
    }

    fun addToFavourites(regType: String) {
        val success = favouriteRegTypes.add(regType)
        if (success) {
            sharedPreferences.edit().putBoolean(regType, true).apply()
            updateDynamicShortcuts()
        }
    }

    fun removeFromFavourites(regType: String) {
        val success = favouriteRegTypes.remove(regType)
        if (success) {
            sharedPreferences.edit().remove(regType).apply()
            updateDynamicShortcuts()
        }
    }

    fun updateDynamicShortcuts() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return
        }
        val shortcutManager = context.getSystemService(
            ShortcutManager::class.java
        ) ?: return
        val shortcuts: MutableList<ShortcutInfo> = LinkedList()
        for (regType in favouriteRegTypes) {
            val fullNameRegType = regTypeManager.getRegTypeDescription(regType) ?: regType
            val newShortcut = ShortcutInfoCompat.Builder(context, regType)
                .setShortLabel(fullNameRegType)
                .setLongLabel(fullNameRegType)
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_star_accent))
                .setIntent(createIntent(context, regType, Config.LOCAL_DOMAIN)
                    .setAction(Intent.ACTION_VIEW))
                .build()
            shortcuts.add(newShortcut.toShortcutInfo())
        }
        shortcutManager.dynamicShortcuts = shortcuts
    }
}