package com.eisiadev.enceladus.magicfind.sack

import ch.njol.skript.aliases.ItemType
import ch.njol.skript.variables.Variables
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class SackIntegration(private val debug: Boolean = false) {
    
    fun findSackSlot(player: Player, itemStack: ItemStack): Int? {
        try {
            if (debug) {
                println("[SackIntegration] 가방 슬롯 검색 시작: ${player.name}, 아이템: ${itemStack.type}")
            }
            
            val uuid = player.uniqueId.toString()
            
            for (slot in 1..27) {
                val varName = "sel_item.${uuid}::${slot}"
                val rawValue = Variables.getVariable(varName, null, false)
                
                val registeredItem = when (rawValue) {
                    is ItemType -> rawValue.random
                    is ItemStack -> rawValue
                    else -> null
                }
                
                if (registeredItem != null && isSameItem(registeredItem, itemStack)) {
                    if (debug) println("[SackIntegration] 매칭 성공! 슬롯 $slot")
                    return slot
                }
            }
            
            if (debug) println("[SackIntegration] 매칭되는 슬롯 없음")
        } catch (e: Exception) {
            println("[SackIntegration] 가방 슬롯 검색 오류: ${e.message}")
            e.printStackTrace()
        }
        return null
    }
    
    fun addToSack(player: Player, slot: Int, amount: Int) {
        try {
            val uuid = player.uniqueId.toString()
            val varName = "amount.sel_item.${uuid}::${slot}"
            val amountObj = Variables.getVariable(varName, null, false)
            val currentAmount = if (amountObj is Number) amountObj.toLong() else 0L
            val newAmount = currentAmount + amount
            
            Variables.setVariable(varName, newAmount, null, false)
            if (debug) {
                println("[SackIntegration] ${player.name}의 슬롯 ${slot}: $currentAmount -> $newAmount")
            }
        } catch (e: Exception) {
            println("[SackIntegration] 가방 저장 오류: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun isSameItem(item1: ItemStack, item2: ItemStack): Boolean {
        if (item1.type != item2.type) return false
        
        val meta1 = item1.itemMeta
        val meta2 = item2.itemMeta
        
        if (meta1 == null && meta2 == null) return true
        if (meta1 == null || meta2 == null) return false
        
        if (meta1.hasCustomModelData() != meta2.hasCustomModelData()) return false
        if (meta1.hasCustomModelData() && meta1.customModelData != meta2.customModelData) {
            return false
        }
        
        if (meta1.hasDisplayName() != meta2.hasDisplayName()) return false
        if (meta1.hasDisplayName() && meta1.displayName != meta2.displayName) {
            return false
        }
        
        return true
    }
}