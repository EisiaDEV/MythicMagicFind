package com.eisiadev.enceladus.pouches.rift

import com.eisiadev.enceladus.pouches.base.AbstractPouchEventHandler
import com.eisiadev.enceladus.pouches.base.TierConfig
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

class RiftEventHandler(
    plugin: JavaPlugin,
    dataManager: RiftDataManager,
    inventoryManager: RiftInventoryManager
) : AbstractPouchEventHandler(plugin, dataManager, inventoryManager) {
    
    override fun isPouchItem(item: ItemStack): Boolean {
        return RiftItemHelper.isPouchItem(item)
    }
    
    override fun getTierFromItem(item: ItemStack): Int? {
        return RiftItemHelper.getRiftTierFromItem(item)
    }
    
    override fun getTierValue(tier: Int): Long {
        val config = dataManager.getTierConfig() as TierConfig.Exponential
        return config.getTierValue(tier)
    }
    
    override fun getInventoryTitle() = "리프트 파우치"
    override fun getPouchDisplayName() = "리프트 파우치"
}