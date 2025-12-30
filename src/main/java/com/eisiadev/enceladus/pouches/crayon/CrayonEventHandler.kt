package com.eisiadev.enceladus.pouches.crayon

import com.eisiadev.enceladus.pouches.base.AbstractPouchEventHandler
import com.eisiadev.enceladus.pouches.base.TierConfig
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

class CrayonEventHandler(
    plugin: JavaPlugin,
    dataManager: CrayonDataManager,
    inventoryManager: CrayonInventoryManager
) : AbstractPouchEventHandler(plugin, dataManager, inventoryManager) {

    override fun isPouchItem(item: ItemStack): Boolean {
        return CrayonItemHelper.isPouchItem(item)
    }

    override fun getTierFromItem(item: ItemStack): Int? {
        return CrayonItemHelper.getCrayonTierFromItem(item)
    }

    override fun getTierValue(tier: Int): Long {
        val config = dataManager.getTierConfig() as TierConfig.Exponential
        return config.getTierValue(tier)
    }

    override fun getInventoryTitle() = "크레용 파우치"
    override fun getPouchDisplayName() = "크레용 파우치"
}