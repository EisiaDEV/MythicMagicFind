package com.eisiadev.enceladus.pouches.powder

import com.eisiadev.enceladus.pouches.base.AbstractPouchEventHandler
import com.eisiadev.enceladus.pouches.base.TierConfig
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

class PowderEventHandler(
    plugin: JavaPlugin,
    dataManager: PowderDataManager,
    inventoryManager: PowderInventoryManager
) : AbstractPouchEventHandler(plugin, dataManager, inventoryManager) {
    
    override fun isPouchItem(item: ItemStack): Boolean {
        return PowderItemHelper.isPouchItem(item)
    }
    
    override fun getTierFromItem(item: ItemStack): Int? {
        return PowderItemHelper.getPowderTierFromItem(item)
    }
    
    override fun getTierValue(tier: Int): Long {
        val config = dataManager.getTierConfig() as TierConfig.Exponential
        return config.getTierValue(tier)
    }
    
    override fun getInventoryTitle() = "신비한 가루 파우치"
    override fun getPouchDisplayName() = "신비한 가루 파우치"
}