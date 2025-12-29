package com.eisiadev.enceladus.magicfind.drop

import com.eisiadev.enceladus.magicfind.config.MagicFindConfig
import com.eisiadev.enceladus.magicfind.item.ItemGenerator
import com.eisiadev.enceladus.magicfind.notification.RareDropNotifier
import com.eisiadev.enceladus.magicfind.pouch.PouchIntegration
import com.eisiadev.enceladus.magicfind.sack.SackIntegration
import com.eisiadev.enceladus.magicfind.util.ReflectionCache
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent
import org.bukkit.entity.Player
import java.lang.reflect.Method

/**
 * 드롭 테이블 처리를 담당하는 클래스
 */
class DropTableProcessor(
    private val config: MagicFindConfig,
    private val itemGenerator: ItemGenerator,
    private val pouchIntegration: PouchIntegration,
    private val sackIntegration: SackIntegration,
    private val rareDropNotifier: RareDropNotifier,
    private val debug: Boolean = false
) {

    private var dropsGetViewMethod: Method? = null
    private var itemGetInternalNameMethod: Method? = null

    fun processDropTableContent(
        dropTable: Any,
        mfMultiplier: Double,
        event: MythicMobDeathEvent,
        killer: Player,
        magicFind: Double,
        isFromBlacklistedTable: Boolean = false
    ): Pair<Int, Int> {
        var sackCount = 0
        var worldCount = 0

        try {
            val dropsField = ReflectionCache.getFieldValue(dropTable, "drops") ?: return Pair(0, 0)

            if (dropsGetViewMethod == null) {
                dropsGetViewMethod = ReflectionCache.getMethod(dropsField.javaClass, "getView")
            }

            val dropsList = dropsGetViewMethod?.invoke(dropsField) as? Collection<*>
                ?: return Pair(0, 0)

            dropsList.forEach { drop ->
                if (drop == null) return@forEach

                val result = processSingleDrop(
                    drop, mfMultiplier, event, killer, magicFind, isFromBlacklistedTable
                )
                sackCount += result.first
                worldCount += result.second
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return Pair(sackCount, worldCount)
    }

    private fun processSingleDrop(
        drop: Any,
        mfMultiplier: Double,
        event: MythicMobDeathEvent,
        killer: Player,
        magicFind: Double,
        isFromBlacklistedTable: Boolean
    ): Pair<Int, Int> {
        val itemField = ReflectionCache.getFieldValue(drop, "item")
        val itemInternalName = if (itemField != null) {
            getInternalName(itemField)
        } else "unknown"

        if (itemInternalName == "unknown") {
            return Pair(0, 0)
        }

        val baseChance = getChanceFromDrop(drop)
        val baseAmountRaw = parseAmountFromDrop(drop)

        val dropHandler = DropHandler(
            config, itemGenerator, pouchIntegration, sackIntegration, rareDropNotifier, debug
        )

        return dropHandler.handleItemDrop(
            itemInternalName,
            baseAmountRaw,
            baseChance,
            mfMultiplier,
            event,
            killer,
            magicFind,
            itemField,
            isFromBlacklistedTable
        )
    }

    private fun getInternalName(itemField: Any): String {
        if (itemGetInternalNameMethod == null) {
            itemGetInternalNameMethod = ReflectionCache.getMethod(
                itemField.javaClass,
                "getInternalName"
            )
        }
        return itemGetInternalNameMethod?.invoke(itemField) as? String ?: "unknown"
    }

    private fun getChanceFromDrop(drop: Any): Double {
        // Try weight field first
        ReflectionCache.getFieldValue(drop, "weight")?.let { weight ->
            (weight as? Number)?.toDouble()?.let { if (it < 1.0) return it }
        }

        // Try getWeight method
        try {
            val method = ReflectionCache.getMethod(drop.javaClass, "getWeight")
            (method?.invoke(drop) as? Number)?.toDouble()?.let {
                if (it < 1.0) return it
            }
        } catch (e: Exception) {
            if (debug) e.printStackTrace()
        }

        // Try chance field
        ReflectionCache.getFieldValue(drop, "chance")?.let { chance ->
            (chance as? Number)?.toDouble()?.let { if (it < 1.0) return it }
        }

        return 1.0
    }

    private fun parseAmountFromDrop(drop: Any): Any {
        val amountObj = ReflectionCache.getFieldValue(drop, "amount") ?: return 1

        try {
            val minField = ReflectionCache.getFieldValue(amountObj, "min")
            val maxField = ReflectionCache.getFieldValue(amountObj, "max")

            if (minField is Number && maxField is Number) {
                return minField.toInt()..maxField.toInt()
            }
        } catch (e: Exception) {
            if (debug) e.printStackTrace()
        }

        return (amountObj as? Number)?.toInt() ?: 1
    }
}