package com.eisiadev.enceladus.magicfind.drop

import com.eisiadev.enceladus.magicfind.config.MagicFindConfig
import com.eisiadev.enceladus.magicfind.item.ItemGenerator
import com.eisiadev.enceladus.magicfind.notification.RareDropNotifier
import com.eisiadev.enceladus.magicfind.pouch.PouchIntegration
import com.eisiadev.enceladus.magicfind.sack.SackIntegration
import com.eisiadev.enceladus.magicfind.util.ReflectionCache
import io.lumine.mythic.bukkit.MythicBukkit
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent
import org.bukkit.Material
import org.bukkit.entity.Player
import java.lang.reflect.Method
import java.util.concurrent.ThreadLocalRandom

/**
 * 드롭 처리 로직을 담당하는 클래스
 */
class DropProcessor(
    private val config: MagicFindConfig,
    private val itemGenerator: ItemGenerator,
    private val pouchIntegration: PouchIntegration,
    private val sackIntegration: SackIntegration,
    private val rareDropNotifier: RareDropNotifier,
    private val debug: Boolean = false
) {

    private var configGetStringListMethod: Method? = null

    fun processDrops(
        event: MythicMobDeathEvent,
        killer: Player,
        magicFind: Double
    ) {
        if (debug) println("--- DropProcessor Start [Killer: ${killer.name}, MF: $magicFind] ---")

        val mfMultiplier = (1.0 + (magicFind / 100.0)).coerceAtLeast(1.0)

        try {
            val mobType = event.mobType
            val configObj = ReflectionCache.getFieldValue(mobType, "config") ?: run {
                if (debug) println("[MagicFind] config 필드를 찾을 수 없음")
                return
            }

            if (configGetStringListMethod == null) {
                configGetStringListMethod = ReflectionCache.getMethod(
                    configObj.javaClass,
                    "getStringList",
                    String::class.java
                )
            }

            @Suppress("UNCHECKED_CAST")
            val rawDropLines = configGetStringListMethod?.invoke(configObj, "Drops") as? List<String>
                ?: emptyList()

            if (rawDropLines.isEmpty()) return

            val originalDrops = ArrayList(event.drops)
            event.drops.clear()

            var itemsAddedToSack = 0
            var itemsAddedToWorld = 0

            rawDropLines.forEach { line ->
                val result = processConfigLine(line, mfMultiplier, event, killer, magicFind)
                itemsAddedToSack += result.first
                itemsAddedToWorld += result.second
            }

            if (debug) {
                println("[MagicFind] 처리 완료 - 가방: ${itemsAddedToSack}개, 월드: ${itemsAddedToWorld}개")
            }

            handleEmptyDrops(event, originalDrops, killer, mfMultiplier, itemsAddedToSack)

        } catch (e: Exception) {
            e.printStackTrace()
            println("!!! MagicFind Error: 오류 발생 !!!")
        }
    }

    private fun processConfigLine(
        line: String,
        mfMultiplier: Double,
        event: MythicMobDeathEvent,
        killer: Player,
        magicFind: Double
    ): Pair<Int, Int> {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return Pair(0, 0)

        val parts = trimmed.split(Regex("\\s+"), limit = 3)
        val itemDef = parts[0]

        if (itemDef.equals("exp", ignoreCase = true) ||
            itemDef.equals("experience", ignoreCase = true)) {
            return Pair(0, 0)
        }

        val amountStr = parts.getOrNull(1) ?: "1"
        val chanceStr = parts.getOrNull(2) ?: "1.0"

        // ⭐ 수정된 드롭테이블 판별 로직
        val isDropTable = !itemDef.contains("{") &&
                Material.getMaterial(itemDef.uppercase()) == null &&
                MythicBukkit.inst().dropManager.getDropTable(itemDef).isPresent  // ✅ 추가!

        return if (isDropTable) {
            processDropTable(itemDef, amountStr, chanceStr, mfMultiplier, event, killer, magicFind)
        } else {
            val dropHandler = DropHandler(
                config, itemGenerator, pouchIntegration, sackIntegration, rareDropNotifier, debug
            )
            dropHandler.handleItemDrop(
                itemDef,
                parseRawAmount(amountStr),
                chanceStr.toDoubleOrNull() ?: 1.0,
                mfMultiplier,
                event,
                killer,
                magicFind
            )
        }
    }

    private fun processDropTable(
        itemDef: String,
        amountStr: String,
        chanceStr: String,
        mfMultiplier: Double,
        event: MythicMobDeathEvent,
        killer: Player,
        magicFind: Double
    ): Pair<Int, Int> {
        val dropTableOpt = MythicBukkit.inst().dropManager.getDropTable(itemDef)
        if (!dropTableOpt.isPresent) return Pair(0, 0)

        val isBlacklisted = config.blacklistedDropTables.contains(itemDef)
        val tableChance = chanceStr.toDoubleOrNull() ?: 1.0
        val tableRepeats = parseAmountRange(amountStr)

        var sackCount = 0
        var worldCount = 0

        if (ThreadLocalRandom.current().nextDouble() <= tableChance) {
            if (debug) println("DropTable '$itemDef' 진입 (반복: $tableRepeats)")
            repeat(tableRepeats) {
                val tableProcessor = DropTableProcessor(
                    config, itemGenerator, pouchIntegration, sackIntegration, rareDropNotifier, debug
                )
                val result = tableProcessor.processDropTableContent(
                    dropTableOpt.get(), mfMultiplier, event, killer, magicFind, isBlacklisted
                )
                sackCount += result.first
                worldCount += result.second
            }
        }
        return Pair(sackCount, worldCount)
    }

    private fun handleEmptyDrops(
        event: MythicMobDeathEvent,
        originalDrops: List<org.bukkit.inventory.ItemStack>,
        killer: Player,
        mfMultiplier: Double,
        itemsAddedToSack: Int
    ) {
        val newDropsCount = event.drops.size
        if (newDropsCount == 0 && originalDrops.isNotEmpty() && itemsAddedToSack == 0) {
            if (debug) println("[MagicFind] 드롭 없음 감지 -> 원본 드롭에 MF 적용하여 복구")

            val fallbackHandler = FallbackDropHandler(pouchIntegration, sackIntegration, debug)
            fallbackHandler.restoreOriginalDrops(
                originalDrops, mfMultiplier, killer, event
            )
        }
    }

    private fun parseAmountRange(str: String): Int = try {
        if (str.contains("-")) {
            val s = str.split("-")
            ThreadLocalRandom.current().nextInt(
                s[0].toIntOrNull() ?: 1,
                (s[1].toIntOrNull() ?: 1) + 1
            )
        } else str.toIntOrNull() ?: 1
    } catch (e: Exception) { 1 }

    private fun parseRawAmount(str: String): Any = try {
        if (str.contains("-") || str.contains("to")) {
            val s = str.split(Regex("(-|to)"))
            val min = s[0].trim().toIntOrNull() ?: 1
            val max = s[1].trim().toIntOrNull() ?: 1
            min..max
        } else str.toIntOrNull() ?: 1
    } catch (e: Exception) { 1 }
}