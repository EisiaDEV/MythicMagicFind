package com.eisiadev.enceladus.pouches.soul

import io.lumine.mythic.bukkit.MythicBukkit
import org.bukkit.ChatColor
import org.bukkit.inventory.ItemStack

object SoulItemHelper {
    
    private const val DEBUG = false
    const val POUCH_DISPLAY_NAME = "혼 파우치"
    
    private val TIER_ITEMS = mapOf(
        1 to "혼파편",
        2 to "불순한혼",
        3 to "가공한혼",
        4 to "정제된혼",
        5 to "정화의혼",
        6 to "격세의혼",
        7 to "천명의혼",
        8 to "현극의혼",
        9 to "공명의혼",
        10 to "영속의혼"
    )
    
    private val ITEM_TO_TIER = TIER_ITEMS.entries.associate { (tier, name) -> name to tier }
    
    fun isPouchItem(item: ItemStack): Boolean {
        if (!item.hasItemMeta()) return false
        val meta = item.itemMeta ?: return false
        if (!meta.hasDisplayName()) return false
        return ChatColor.stripColor(meta.displayName) == POUCH_DISPLAY_NAME
    }
    
    fun generateSoulItem(tier: Int, count: Int): ItemStack? {
        val itemName = TIER_ITEMS[tier] ?: return null
        return generateSoulItemByName(itemName, count)
    }
    
    private fun generateSoulItemByName(itemName: String, count: Int): ItemStack? {
        try {
            val itemOpt = MythicBukkit.inst().itemManager.getItem(itemName)
            
            if (itemOpt.isPresent) {
                val mythicItem = itemOpt.get()
                val itemStack = mythicItem.generateItemStack(count)
                
                return when {
                    itemStack is ItemStack -> itemStack
                    itemStack.javaClass.simpleName == "BukkitItemStack" -> {
                        itemStack.javaClass.getMethod("build").invoke(itemStack) as? ItemStack
                    }
                    else -> null
                }
            }
        } catch (e: Exception) {
            if (DEBUG) {
                println("[SoulPouch] 아이템 생성 실패: $itemName")
                e.printStackTrace()
            }
        }
        return null
    }
    
    fun getSoulTierFromItem(item: ItemStack): Int? {
        try {
            val mythicItem = MythicBukkit.inst().itemManager.getMythicTypeFromItem(item)
            
            if (mythicItem != null) {
                val internalName = mythicItem
                return ITEM_TO_TIER[internalName]
            }
        } catch (e: Exception) {
            if (DEBUG) {
                println("[SoulPouch] 티어 추출 실패:")
                e.printStackTrace()
            }
        }
        return null
    }
}