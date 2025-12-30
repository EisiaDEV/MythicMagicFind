package com.eisiadev.enceladus.pouches.crayon

import io.lumine.mythic.bukkit.MythicBukkit
import org.bukkit.ChatColor
import org.bukkit.inventory.ItemStack

object CrayonItemHelper {

    private const val DEBUG = false
    const val POUCH_DISPLAY_NAME = "크레용 파우치"

    fun isPouchItem(item: ItemStack): Boolean {
        if (!item.hasItemMeta()) return false
        val meta = item.itemMeta ?: return false
        if (!meta.hasDisplayName()) return false
        return ChatColor.stripColor(meta.displayName) == POUCH_DISPLAY_NAME
    }

    fun generateCrayonItem(tier: Int, count: Int): ItemStack? {
        try {
            val itemName = "크레용_${tier}"
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
                println("[CrayonPouch] 아이템 생성 실패: 크레용_${tier}")
                e.printStackTrace()
            }
        }
        return null
    }

    fun getCrayonTierFromItem(item: ItemStack): Int? {
        try {
            val mythicItem = MythicBukkit.inst().itemManager.getMythicTypeFromItem(item)

            if (mythicItem != null) {
                val internalName = mythicItem

                val tierMatch = Regex("크레용_(\\d+)").find(internalName) ?: return null
                val tier = tierMatch.groupValues[1].toIntOrNull() ?: return null

                if (tier in 1..6) {
                    return tier
                }
            }
        } catch (e: Exception) {
            if (DEBUG) {
                println("[CrayonPouch] 티어 추출 실패:")
                e.printStackTrace()
            }
        }
        return null
    }
}