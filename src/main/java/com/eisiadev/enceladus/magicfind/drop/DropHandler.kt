package com.eisiadev.enceladus.magicfind.drop

import com.eisiadev.enceladus.magicfind.config.MagicFindConfig
import com.eisiadev.enceladus.magicfind.item.ItemGenerator
import com.eisiadev.enceladus.magicfind.notification.RareDropNotifier
import com.eisiadev.enceladus.magicfind.pouch.PouchIntegration
import com.eisiadev.enceladus.magicfind.sack.SackIntegration
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent
import org.bukkit.entity.Player
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.floor

class DropHandler(
    private val config: MagicFindConfig,
    private val itemGenerator: ItemGenerator,
    private val pouchIntegration: PouchIntegration,
    private val sackIntegration: SackIntegration,
    private val rareDropNotifier: RareDropNotifier,
    private val debug: Boolean = false
) {

    fun handleItemDrop(
        itemDef: String,
        baseAmountRaw: Any,
        baseChance: Double,
        mfMultiplier: Double,
        event: MythicMobDeathEvent,
        killer: Player,
        magicFind: Double,
        mythicItemObject: Any? = null,
        skipRareDropCheck: Boolean = false
    ): Pair<Int, Int> {
        val (finalChance, amountMultiplier) = calculateChanceAndMultiplier(
            baseChance, mfMultiplier
        )

        val rolled = ThreadLocalRandom.current().nextDouble()
        if (debug) {
            println("DEBUG: '$itemDef' 확률 체크: rolled=${"%.4f".format(rolled)} vs finalChance=${"%.4f".format(finalChance)}")
        }

        if (rolled > finalChance) {
            return Pair(0, 0)
        }

        val rolledBaseAmount = when (baseAmountRaw) {
            is Int -> baseAmountRaw
            is IntRange -> baseAmountRaw.random()
            else -> 1
        }
        val finalAmount = rolledBaseAmount * amountMultiplier

        if (debug) println("DEBUG: '$itemDef' 드롭 성공! finalAmount=$finalAmount")

        if (finalAmount <= 0) {
            return Pair(0, 0)
        }

        if (!skipRareDropCheck) {
            rareDropNotifier.checkAndAnnounceRareDrop(
                itemDef, baseChance, finalAmount, killer, magicFind, mythicItemObject
            )
        }

        return distributeItem(itemDef, finalAmount, killer, event, mythicItemObject)
    }

    private fun calculateChanceAndMultiplier(
        baseChance: Double,
        mfMultiplier: Double
    ): Pair<Double, Int> {
        if (baseChance >= 1.0) {
            return Pair(1.0, floor(mfMultiplier).toInt().coerceAtLeast(1))
        }

        val totalChance = baseChance * mfMultiplier
        return if (totalChance <= 1.0) {
            Pair(totalChance, 1)
        } else {
            Pair(1.0, floor(totalChance).toInt().coerceAtLeast(1))
        }
    }

    private fun distributeItem(
        itemDef: String,
        finalAmount: Int,
        killer: Player,
        event: MythicMobDeathEvent,
        mythicItemObject: Any?
    ): Pair<Int, Int> {
        if (pouchIntegration.tryAddToPouch(killer, itemDef, finalAmount)) {
            if (debug) {
                println("[DropHandler] Pouch 시스템에 추가: $itemDef x$finalAmount")
            }
            return Pair(finalAmount, 0)
        }

        val itemStack = itemGenerator.generateItem(itemDef, 1, mythicItemObject)
            ?: run {
                if (debug) println("DEBUG: 아이템 생성 실패: $itemDef")
                return Pair(0, 0)
            }

        if (debug) println("DEBUG: 아이템 생성 성공: ${itemStack.type}")

        val sackSlot = sackIntegration.findSackSlot(killer, itemStack)
        if (sackSlot != null) {
            sackIntegration.addToSack(killer, sackSlot, finalAmount)
            if (debug) {
                println("[SackIntegration] ${killer.name}의 가방 슬롯 ${sackSlot}에 ${itemStack.type} x${finalAmount} 추가됨")
            }
            return Pair(finalAmount, 0)
        }

        if (debug) println("DEBUG: 가방 미등록 아이템 -> 월드 드롭")
        addToWorldDrops(itemDef, finalAmount, itemStack.maxStackSize, event, mythicItemObject)
        return Pair(0, finalAmount)
    }

    private fun addToWorldDrops(
        itemDef: String,
        finalAmount: Int,
        maxStackSize: Int,
        event: MythicMobDeathEvent,
        mythicItemObject: Any?
    ) {
        val fullStacks = finalAmount / maxStackSize
        val remainder = finalAmount % maxStackSize

        repeat(fullStacks) {
            itemGenerator.generateItem(itemDef, maxStackSize, mythicItemObject)?.let {
                event.drops.add(it)
            }
        }
        if (remainder > 0) {
            itemGenerator.generateItem(itemDef, remainder, mythicItemObject)?.let {
                event.drops.add(it)
            }
        }
    }
}