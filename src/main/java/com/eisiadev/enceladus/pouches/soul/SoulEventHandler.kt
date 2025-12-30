package com.eisiadev.enceladus.pouches.soul

import com.eisiadev.enceladus.pouches.base.AbstractPouchEventHandler
import com.eisiadev.enceladus.pouches.base.TierConfig
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

class SoulEventHandler(
    plugin: JavaPlugin,
    dataManager: SoulDataManager,
    inventoryManager: SoulInventoryManager
) : AbstractPouchEventHandler(plugin, dataManager, inventoryManager) {
    
    override fun isPouchItem(item: ItemStack): Boolean {
        return SoulItemHelper.isPouchItem(item)
    }
    
    override fun getTierFromItem(item: ItemStack): Int? {
        return SoulItemHelper.getSoulTierFromItem(item)
    }
    
    override fun getTierValue(tier: Int): Long {
        val config = dataManager.getTierConfig() as TierConfig.Custom
        return config.getTierValue(tier)
    }
    
    override fun getInventoryTitle() = "혼 파우치"
    override fun getPouchDisplayName() = "혼 파우치"
}