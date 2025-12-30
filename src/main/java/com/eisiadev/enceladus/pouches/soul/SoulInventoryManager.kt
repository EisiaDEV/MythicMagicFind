package com.eisiadev.enceladus.pouches.soul

import com.eisiadev.enceladus.pouches.base.AbstractPouchInventoryManager
import com.eisiadev.enceladus.pouches.base.TierConfig
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

class SoulInventoryManager(
    plugin: JavaPlugin,
    dataManager: SoulDataManager
) : AbstractPouchInventoryManager(plugin, dataManager) {
    
    override fun getInventoryTitle() = "혼 파우치"
    override fun getPouchDisplayName() = "혼 파우치"
    override fun getItemUnitName() = "혼"
    
    override fun generateItemForTier(tier: Int, count: Int): ItemStack? {
        return SoulItemHelper.generateSoulItem(tier, count)
    }
    
    override fun getTierFromItem(item: ItemStack): Int? {
        return SoulItemHelper.getSoulTierFromItem(item)
    }
    
    override fun getTierValue(tier: Int): Long {
        val config = dataManager.getTierConfig() as TierConfig.Custom
        return config.getTierValue(tier)
    }
}