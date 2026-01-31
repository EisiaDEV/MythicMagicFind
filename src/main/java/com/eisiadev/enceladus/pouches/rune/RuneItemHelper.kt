package com.eisiadev.enceladus.pouches.rune

import io.lumine.mythic.bukkit.MythicBukkit
import org.bukkit.ChatColor
import org.bukkit.inventory.ItemStack

object RuneItemHelper {
    
    private const val DEBUG = false
    const val POUCH_DISPLAY_NAME = "룬 파우치"
    
    private val TIER_ITEMS = mapOf(
        1 to "빈룬",
        2 to "룬가방",
        3 to "거대한룬가방",
        4 to "룬정수",
        5 to "루닉스프라이트"
    )
    
    private val ITEM_TO_TIER = TIER_ITEMS.entries.associate { (tier, name) -> name to tier }
    
    fun isPouchItem(item: ItemStack): Boolean {
        if (!item.hasItemMeta()) return false
        val meta = item.itemMeta ?: return false
        if (!meta.hasDisplayName()) return false
        return ChatColor.stripColor(meta.displayName) == POUCH_DISPLAY_NAME
    }
    
    fun generateRuneItem(tier: Int, count: Int): ItemStack? {
        val itemName = TIER_ITEMS[tier] ?: return null
        return generateRuneItemByName(itemName, count)
    }
    
    private fun generateRuneItemByName(itemName: String, count: Int): ItemStack? {
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
                println("[RunePouch] 아이템 생성 실패: $itemName")
                e.printStackTrace()
            }
        }
        return null
    }
    
    fun getRuneTierFromItem(item: ItemStack): Int? {
        try {
            val mythicItem = MythicBukkit.inst().itemManager.getMythicTypeFromItem(item)
            
            if (mythicItem != null) {
                val internalName = mythicItem
                return ITEM_TO_TIER[internalName]
            }
        } catch (e: Exception) {
            if (DEBUG) {
                println("[RunePouch] 티어 추출 실패:")
                e.printStackTrace()
            }
        }
        return null
    }
}