package com.eisiadev.enceladus.pouches.rift

import com.eisiadev.enceladus.pouches.base.AbstractPouchInventoryManager
import com.eisiadev.enceladus.pouches.base.TierConfig
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

class RiftInventoryManager(
    plugin: JavaPlugin,
    dataManager: RiftDataManager
) : AbstractPouchInventoryManager(plugin, dataManager) {
    
    override fun getInventoryTitle() = "리프트 파우치"
    override fun getPouchDisplayName() = "리프트 파우치"
    override fun getItemUnitName() = "샤드"
    
    override fun generateItemForTier(tier: Int, count: Int): ItemStack? {
        return RiftItemHelper.generateRiftItem(tier, count)
    }
    
    override fun getTierFromItem(item: ItemStack): Int? {
        return RiftItemHelper.getRiftTierFromItem(item)
    }
    
    override fun getTierValue(tier: Int): Long {
        val config = dataManager.getTierConfig() as TierConfig.Exponential
        return config.getTierValue(tier)
    }
}