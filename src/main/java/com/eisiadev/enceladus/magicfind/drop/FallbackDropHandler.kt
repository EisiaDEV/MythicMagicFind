package com.eisiadev.enceladus.magicfind.drop

import com.eisiadev.enceladus.magicfind.pouch.PouchIntegration
import com.eisiadev.enceladus.magicfind.sack.SackIntegration
import io.lumine.mythic.bukkit.MythicBukkit
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import kotlin.math.floor

class FallbackDropHandler(
    private val pouchIntegration: PouchIntegration,
    private val sackIntegration: SackIntegration,
    private val debug: Boolean = false
) {

    fun restoreOriginalDrops(
        originalDrops: List<ItemStack>,
        mfMultiplier: Double,
        killer: Player,
        event: MythicMobDeathEvent
    ) {
        var itemsAddedToSack = 0
        var itemsAddedToWorld = 0

        originalDrops.forEach { originalItem ->
            val amountMultiplier = floor(mfMultiplier).toInt().coerceAtLeast(1)
            val finalAmount = originalItem.amount * amountMultiplier

            if (finalAmount > 0) {
                val result = processOriginalItem(originalItem, finalAmount, killer, event)
                itemsAddedToSack += result.first
                itemsAddedToWorld += result.second
            }
        }

        if (debug) {
            println("[MagicFind] 복구 완료 - 가방: ${itemsAddedToSack}개, 월드: ${itemsAddedToWorld}개")
        }
    }

    private fun processOriginalItem(
        originalItem: ItemStack,
        finalAmount: Int,
        killer: Player,
        event: MythicMobDeathEvent
    ): Pair<Int, Int> {
        // 1. Try Pouch systems first (using MythicMobs internal name if available)
        val mythicType = MythicBukkit.inst().itemManager.getMythicTypeFromItem(originalItem)
        if (mythicType != null &&
            pouchIntegration.tryAddToPouch(killer, mythicType, finalAmount)) {
            if (debug) {
                println("[MagicFind] 복구 아이템 Pouch 추가: $mythicType x$finalAmount")
            }
            return Pair(finalAmount, 0)
        }

        // 2. Try sack
        val sackSlot = sackIntegration.findSackSlot(killer, originalItem)
        if (sackSlot != null) {
            sackIntegration.addToSack(killer, sackSlot, finalAmount)
            if (debug) {
                println("[MagicFind] 복구 아이템 가방 추가: ${originalItem.type} x$finalAmount")
            }
            return Pair(finalAmount, 0)
        }

        // 3. Drop to world
        addToWorldDrops(originalItem, finalAmount, event)
        if (debug) {
            println("[MagicFind] 복구 아이템 월드 추가: ${originalItem.type} x$finalAmount")
        }
        return Pair(0, finalAmount)
    }

    private fun addToWorldDrops(
        originalItem: ItemStack,
        finalAmount: Int,
        event: MythicMobDeathEvent
    ) {
        val maxStackSize = originalItem.maxStackSize
        val fullStacks = finalAmount / maxStackSize
        val remainder = finalAmount % maxStackSize

        repeat(fullStacks) {
            val stack = originalItem.clone()
            stack.amount = maxStackSize
            event.drops.add(stack)
        }

        if (remainder > 0) {
            val stack = originalItem.clone()
            stack.amount = remainder
            event.drops.add(stack)
        }
    }
}