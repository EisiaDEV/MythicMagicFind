package com.eisiadev.enceladus.pouches.powder

import com.eisiadev.enceladus.pouches.base.AbstractPouchInventoryManager
import com.eisiadev.enceladus.pouches.base.TierConfig
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

class PowderInventoryManager(
    plugin: JavaPlugin,
    dataManager: PowderDataManager
) : AbstractPouchInventoryManager(plugin, dataManager) {
    
    override fun getInventoryTitle() = "신비한 가루 파우치"
    override fun getPouchDisplayName() = "신비한 가루 파우치"
    override fun getItemUnitName() = "파우더"
    
    override fun generateItemForTier(tier: Int, count: Int): ItemStack? {
        return PowderItemHelper.generatePowderItem(tier, count)
    }
    
    override fun getTierFromItem(item: ItemStack): Int? {
        return PowderItemHelper.getPowderTierFromItem(item)
    }
    
    override fun getTierValue(tier: Int): Long {
        val config = dataManager.getTierConfig() as TierConfig.Exponential
        return config.getTierValue(tier)
    }
}