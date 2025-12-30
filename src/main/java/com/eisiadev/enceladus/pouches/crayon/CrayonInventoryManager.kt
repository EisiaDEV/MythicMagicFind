package com.eisiadev.enceladus.pouches.crayon

import com.eisiadev.enceladus.pouches.base.AbstractPouchInventoryManager
import com.eisiadev.enceladus.pouches.base.TierConfig
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

class CrayonInventoryManager(
    plugin: JavaPlugin,
    dataManager: CrayonDataManager
) : AbstractPouchInventoryManager(plugin, dataManager) {

    override fun getInventoryTitle() = "크레용 파우치"
    override fun getPouchDisplayName() = "크레용 파우치"
    override fun getItemUnitName() = "크레용"

    override fun generateItemForTier(tier: Int, count: Int): ItemStack? {
        return CrayonItemHelper.generateCrayonItem(tier, count)
    }

    override fun getTierFromItem(item: ItemStack): Int? {
        return CrayonItemHelper.getCrayonTierFromItem(item)
    }

    override fun getTierValue(tier: Int): Long {
        val config = dataManager.getTierConfig() as TierConfig.Exponential
        return config.getTierValue(tier)
    }
}