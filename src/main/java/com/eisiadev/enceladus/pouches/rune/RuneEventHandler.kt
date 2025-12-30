package com.eisiadev.enceladus.pouches.rune

import com.eisiadev.enceladus.pouches.base.AbstractPouchEventHandler
import com.eisiadev.enceladus.pouches.base.TierConfig
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

class RuneEventHandler(
    plugin: JavaPlugin,
    dataManager: RuneDataManager,
    inventoryManager: RuneInventoryManager
) : AbstractPouchEventHandler(plugin, dataManager, inventoryManager) {
    
    override fun isPouchItem(item: ItemStack): Boolean {
        return RuneItemHelper.isPouchItem(item)
    }
    
    override fun getTierFromItem(item: ItemStack): Int? {
        return RuneItemHelper.getRuneTierFromItem(item)
    }
    
    override fun getTierValue(tier: Int): Long {
        val config = dataManager.getTierConfig() as TierConfig.Custom
        return config.getTierValue(tier)
    }
    
    override fun getInventoryTitle() = "룬 파우치"
    override fun getPouchDisplayName() = "룬 파우치"
}