package com.eisiadev.enceladus.pouches.rune

import com.eisiadev.enceladus.pouches.base.AbstractPouchInventoryManager
import com.eisiadev.enceladus.pouches.base.TierConfig
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

class RuneInventoryManager(
    plugin: JavaPlugin,
    dataManager: RuneDataManager
) : AbstractPouchInventoryManager(plugin, dataManager) {
    
    override fun getInventoryTitle() = "룬 파우치"
    override fun getPouchDisplayName() = "룬 파우치"
    override fun getItemUnitName() = "룬"
    
    override fun generateItemForTier(tier: Int, count: Int): ItemStack? {
        return RuneItemHelper.generateRuneItem(tier, count)
    }
    
    override fun getTierFromItem(item: ItemStack): Int? {
        return RuneItemHelper.getRuneTierFromItem(item)
    }
    
    override fun getTierValue(tier: Int): Long {
        val config = dataManager.getTierConfig() as TierConfig.Custom
        return config.getTierValue(tier)
    }
}