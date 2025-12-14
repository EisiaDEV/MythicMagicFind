package com.eisiadev.enceladus.magicfind.util

import io.lumine.mythic.bukkit.MythicBukkit
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.floor

object MagicFindCalculator {

    private const val DEBUG = false

    fun modifyDrops(event: MythicMobDeathEvent, killer: Player, magicFind: Double) {
        if (DEBUG) println("--- MagicFindCalculator Start [Killer: ${killer.name}, MF: $magicFind] ---")

        val mfMultiplier = 1.0 + (magicFind / 100.0)

        try {
            val mobType = event.mobType
            val config = getFieldValue(mobType, "config") ?: return
            val getStringListMethod = config::class.java.getMethod("getStringList", String::class.java)
            @Suppress("UNCHECKED_CAST")
            val rawDropLines = getStringListMethod.invoke(config, "Drops") as? List<String> ?: emptyList()

            if (rawDropLines.isEmpty()) return

            event.drops.clear()

            rawDropLines.forEach { line ->
                processConfigLine(line, mfMultiplier, event)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            println("!!! MagicFind Error: 오류 발생으로 기존 드롭 복구 시도 !!!")
        }
    }

    private fun processConfigLine(line: String, mfMultiplier: Double, event: MythicMobDeathEvent) {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return

        val parts = trimmed.split(Regex("\\s+"), limit = 3)
        val itemDef = parts[0]

        if (itemDef.equals("exp", ignoreCase = true) || itemDef.equals("experience", ignoreCase = true)) return

        val amountStr = parts.getOrNull(1) ?: "1"
        val chanceStr = parts.getOrNull(2) ?: "1.0"

        val isDropTable = !itemDef.contains("{") && Material.getMaterial(itemDef.uppercase()) == null

        if (isDropTable) {
            val dropManager = MythicBukkit.inst().dropManager
            val dropTableOpt = dropManager.getDropTable(itemDef)

            if (dropTableOpt.isPresent) {
                val tableChance = chanceStr.toDoubleOrNull() ?: 1.0
                val tableRepeats = parseAmountRange(amountStr)
                if (ThreadLocalRandom.current().nextDouble() <= tableChance) {
                    if (DEBUG) println("DropTable '$itemDef' 진입 (반복: $tableRepeats)")
                    val dropTable = dropTableOpt.get()

                    repeat(tableRepeats) {
                        processDropTableContent(dropTable, mfMultiplier, event)
                    }
                }
                return
            }
        }

        val baseChance = chanceStr.toDoubleOrNull() ?: 1.0
        val baseAmountRaw = parseRawAmount(amountStr)

        handleItemDrop(itemDef, baseAmountRaw, baseChance, mfMultiplier, event)
    }

    private fun processDropTableContent(dropTable: Any, mfMultiplier: Double, event: MythicMobDeathEvent) {
        try {
            val dropsField = getFieldValue(dropTable, "drops")
            val getViewMethod = dropsField?.javaClass?.getMethod("getView")
            val dropsList = getViewMethod?.invoke(dropsField) as? Collection<*> ?: return

            dropsList.forEach { drop ->
                if (drop == null) return@forEach

                val itemField = getFieldValue(drop, "item")
                val itemInternalName = if (itemField != null) {
                    val getName = itemField.javaClass.getMethod("getInternalName")
                    getName.invoke(itemField) as? String ?: "unknown"
                } else "unknown"

                val baseChance = getChanceFromDrop(drop)

                if (DEBUG && baseChance < 1.0) {
                    println("DEBUG: 내부 아이템 '$itemInternalName' 확률 인식됨: ${String.format("%.1f", baseChance*100)}%")
                }

                val amountObj = getFieldValue(drop, "amount")
                val baseAmountRaw = if (amountObj != null) parseAmountObject(amountObj) else 1

                if (itemInternalName != "unknown") {
                    handleItemDrop(
                        itemDef = itemInternalName,
                        baseAmountRaw = baseAmountRaw,
                        baseChance = baseChance,
                        mfMultiplier = mfMultiplier,
                        event = event,
                        mythicItemObject = itemField
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getChanceFromDrop(drop: Any): Double {
        val weightVal = getFieldDouble(drop, "weight")
        if (weightVal != null && weightVal < 1.0) {
            return weightVal
        }

        try {
            val method = drop.javaClass.getMethod("getWeight")
            val result = (method.invoke(drop) as? Number)?.toDouble()
            if (result != null && result < 1.0) {
                return result
            }
        } catch (e: Exception) {

        }

        val chanceVal = getFieldDouble(drop, "chance")
        if (chanceVal != null && chanceVal < 1.0) {
            return chanceVal
        }

        return 1.0
    }

    private fun handleItemDrop(
        itemDef: String,
        baseAmountRaw: Any,
        baseChance: Double,
        mfMultiplier: Double,
        event: MythicMobDeathEvent,
        mythicItemObject: Any? = null
    ) {
        val finalChance: Double
        val amountMultiplier: Int

        if (baseChance >= 1.0) {
            finalChance = 1.0
            amountMultiplier = floor(mfMultiplier).toInt().coerceAtLeast(1)
        } else {
            val totalChance = baseChance * mfMultiplier
            if (totalChance <= 1.0) {
                finalChance = totalChance
                amountMultiplier = 1
            } else {
                finalChance = 1.0
                amountMultiplier = floor(totalChance).toInt().coerceAtLeast(1)
            }
        }

        if (ThreadLocalRandom.current().nextDouble() <= finalChance) {
            val rolledBaseAmount = when (baseAmountRaw) {
                is Int -> baseAmountRaw
                is IntRange -> baseAmountRaw.random()
                else -> 1
            }

            val finalAmount = rolledBaseAmount * amountMultiplier

            if (finalAmount > 0) {
                val maxStackSize = 64
                val fullStacks = finalAmount / maxStackSize
                val remainder = finalAmount % maxStackSize

                repeat(fullStacks) {
                    val itemStack = generateItem(itemDef, maxStackSize, mythicItemObject)
                    if (itemStack != null) event.drops.add(itemStack)
                }

                if (remainder > 0) {
                    val itemStack = generateItem(itemDef, remainder, mythicItemObject)
                    if (itemStack != null) event.drops.add(itemStack)
                }
            }
        }
    }

    private fun parseAmountRange(str: String): Int {
        return try {
            if (str.contains("-")) {
                val s = str.split("-")
                val min = s[0].toIntOrNull() ?: 1
                val max = s[1].toIntOrNull() ?: 1
                ThreadLocalRandom.current().nextInt(min, max + 1)
            } else {
                str.toIntOrNull() ?: 1
            }
        } catch (e: Exception) { 1 }
    }

    private fun parseRawAmount(str: String): Any {
        return try {
            if (str.contains("-") || str.contains("to")) {
                val s = str.split(Regex("(-|to)"))
                val min = s[0].trim().toIntOrNull() ?: 1
                val max = s[1].trim().toIntOrNull() ?: 1
                min..max
            } else {
                str.toIntOrNull() ?: 1
            }
        } catch (e: Exception) { 1 }
    }

    private fun parseAmountObject(obj: Any): Any {
        return try {
            val minField = getFieldValue(obj, "min")
            val maxField = getFieldValue(obj, "max")
            if (minField is Number && maxField is Number) {
                minField.toInt()..maxField.toInt()
            } else {
                (obj as? Number)?.toInt() ?: 1
            }
        } catch (e: Exception) { 1 }
    }

    private fun generateItem(itemDef: String, amount: Int, mythicItemObject: Any?): ItemStack? {
        try {
            if (mythicItemObject != null) {
                val genMethod = mythicItemObject.javaClass.getMethod("generateItemStack", Int::class.javaPrimitiveType)
                return convertToBukkitStack(genMethod.invoke(mythicItemObject, amount), amount)
            }
            if (itemDef.contains("{") || MythicBukkit.inst().itemManager.getItem(itemDef).isPresent) {
                val itemOpt = MythicBukkit.inst().itemManager.getItem(itemDef)
                if (itemOpt.isPresent) {
                    return convertToBukkitStack(itemOpt.get().generateItemStack(amount), amount)
                }
            }
            val mat = Material.getMaterial(itemDef.split("{")[0].uppercase()) ?: return null
            return ItemStack(mat, amount)
        } catch (e: Exception) { return null }
    }

    private fun convertToBukkitStack(abstractItem: Any?, amount: Int): ItemStack? {
        if (abstractItem == null) return null
        val stack = when {
            abstractItem is ItemStack -> abstractItem
            abstractItem.javaClass.simpleName == "BukkitItemStack" -> {
                abstractItem.javaClass.getMethod("build").invoke(abstractItem) as? ItemStack
            }
            else -> null
        }
        stack?.amount = amount
        return stack
    }

    private fun getFieldValue(instance: Any, fieldName: String): Any? {
        var cls: Class<*>? = instance.javaClass
        while (cls != null && cls != Object::class.java) {
            try {
                val f = cls.getDeclaredField(fieldName)
                f.isAccessible = true
                return f.get(instance)
            } catch (e: NoSuchFieldException) {
                cls = cls.superclass
            }
        }
        return null
    }

    private fun getFieldDouble(instance: Any, fieldName: String): Double? {
        return (getFieldValue(instance, fieldName) as? Number)?.toDouble()
    }
}