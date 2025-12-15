package com.eisiadev.enceladus.magicfind.util

import ch.njol.skript.aliases.ItemType
import ch.njol.skript.variables.Variables
import io.lumine.mythic.bukkit.MythicBukkit
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.floor

object MagicFindCalculator {

    private const val DEBUG = false
    private lateinit var config: FileConfiguration
    private var configFile: File? = null

    data class RarityTier(
        val id: String, val enabled: Boolean, val minChance: Double, val maxChance: Double,
        val message: String, val sound: String, val volume: Float, val pitch: Float, val broadcast: Boolean
    )

    private var rarityTiers = mutableListOf<RarityTier>()
    private var blacklistedItems = mutableSetOf<String>()
    private var blacklistedDropTables = mutableSetOf<String>()

    fun initialize(plugin: JavaPlugin) {
        configFile = File(plugin.dataFolder, "config.yml")
        if (!configFile!!.exists()) plugin.saveResource("config.yml", false)
        loadConfig()
    }

    fun loadConfig() {
        if (configFile == null || !configFile!!.exists()) {
            loadDefaultTiers()
            return
        }

        try {
            config = YamlConfiguration.loadConfiguration(configFile!!)
            rarityTiers.clear()
            blacklistedItems.clear()
            blacklistedDropTables.clear()

            config.getConfigurationSection("blacklist")?.let { section ->
                blacklistedItems.addAll(section.getStringList("items").map { it.uppercase() })
                blacklistedDropTables.addAll(section.getStringList("droptables"))
                println("[MagicFind] Loaded ${blacklistedItems.size} blacklisted items and ${blacklistedDropTables.size} blacklisted droptables")
            }

            val section = config.getConfigurationSection("rare_drops") ?: run {
                loadDefaultTiers()
                return
            }

            section.getKeys(false).forEach { key ->
                section.getConfigurationSection(key)?.let { tier ->
                    rarityTiers.add(RarityTier(
                        id = key,
                        enabled = tier.getBoolean("enabled", true),
                        minChance = tier.getDouble("min_chance", 0.0),
                        maxChance = tier.getDouble("max_chance", 1.0),
                        message = tier.getString("message") ?: "{item} x{amount}",
                        sound = tier.getString("sound") ?: "ENTITY_EXPERIENCE_ORB_PICKUP",
                        volume = tier.getDouble("volume", 1.0).toFloat(),
                        pitch = tier.getDouble("pitch", 1.0).toFloat(),
                        broadcast = tier.getBoolean("broadcast", false)
                    ))
                }
            }

            rarityTiers.sortByDescending { it.maxChance }
            println("[MagicFind] Loaded ${rarityTiers.size} rarity tiers from config")
        } catch (e: Exception) {
            e.printStackTrace()
            println("[MagicFind] Failed to load config, using default settings")
            loadDefaultTiers()
        }
    }

    private fun loadDefaultTiers() {
        rarityTiers = mutableListOf(
            RarityTier("occasional", true, 0.10, 0.20, "&9Occasional DROP! {item} &ex{amount} &f{chance}", "slayerdrop.occasional_drop", 1.0f, 1.0f, false),
            RarityTier("rare", true, 0.02, 0.10, "&5Rare DROP! {item} &ex{amount} &f{chance}", "slayerdrop.occasional_drop", 1.0f, 1.0f, false),
            RarityTier("extraordinary", true, 0.001, 0.02, "&6Extraordinary DROP! {item} &ex{amount} &f{chance}", "slayerdrop.rare_drop", 1.0f, 1.0f, false),
            RarityTier("pray", true, 0.0002, 0.001, "&dPray RNGesus DROP! {item} &ex{amount} &f{chance} &f- &b{player}", "slayerdrop.pray_rngesus_drop", 1.0f, 1.0f, true),
            RarityTier("incarnate", true, 0.00005, 0.0002, "&cRNGesus Incarnate DROP! {item} &ex{amount} &f{chance} &f- &b{player}", "slayerdrop.pray_rngesus_drop", 1.0f, 1.0f, true),
            RarityTier("insane", true, 0.000005, 0.00005, "&4RNGesus Insane DROP! {item} &ex{amount} &f{chance} &f- &b{player}", "slayerdrop.rngesus_incarnate_drop", 1.0f, 1.0f, true),
            RarityTier("unleashed", true, 0.0, 0.000005, "&5&lRNGesus Unleashed DROP! &r{item} &ex{amount} &f{chance} &f- &b{player}", "slayerdrop.rngesus_incarnate_drop", 1.0f, 1.5f, true)
        )
    }

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
            rawDropLines.forEach { line -> processConfigLine(line, mfMultiplier, event, killer, magicFind) }
        } catch (e: Exception) {
            e.printStackTrace()
            println("!!! MagicFind Error: 오류 발생으로 기존 드롭 복구 시도 !!!")
        }
    }

    private fun processConfigLine(line: String, mfMultiplier: Double, event: MythicMobDeathEvent, killer: Player, magicFind: Double) {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return

        val parts = trimmed.split(Regex("\\s+"), limit = 3)
        val itemDef = parts[0]
        if (itemDef.equals("exp", ignoreCase = true) || itemDef.equals("experience", ignoreCase = true)) return

        val amountStr = parts.getOrNull(1) ?: "1"
        val chanceStr = parts.getOrNull(2) ?: "1.0"
        val isDropTable = !itemDef.contains("{") && Material.getMaterial(itemDef.uppercase()) == null

        if (isDropTable) {
            val dropTableOpt = MythicBukkit.inst().dropManager.getDropTable(itemDef)
            if (dropTableOpt.isPresent) {
                val isBlacklisted = blacklistedDropTables.contains(itemDef)
                val tableChance = chanceStr.toDoubleOrNull() ?: 1.0
                val tableRepeats = parseAmountRange(amountStr)

                if (ThreadLocalRandom.current().nextDouble() <= tableChance) {
                    if (DEBUG) println("DropTable '$itemDef' 진입 (반복: $tableRepeats)")
                    repeat(tableRepeats) {
                        processDropTableContent(dropTableOpt.get(), mfMultiplier, event, killer, magicFind, isBlacklisted)
                    }
                }
                return
            }
        }

        handleItemDrop(itemDef, parseRawAmount(amountStr), chanceStr.toDoubleOrNull() ?: 1.0, mfMultiplier, event, killer, magicFind)
    }

    private fun processDropTableContent(dropTable: Any, mfMultiplier: Double, event: MythicMobDeathEvent, killer: Player, magicFind: Double, isFromBlacklistedTable: Boolean = false) {
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
                    handleItemDrop(itemInternalName, baseAmountRaw, baseChance, mfMultiplier, event, killer, magicFind, itemField, isFromBlacklistedTable)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getChanceFromDrop(drop: Any): Double {
        getFieldDouble(drop, "weight")?.let { if (it < 1.0) return it }
        try {
            (drop.javaClass.getMethod("getWeight").invoke(drop) as? Number)?.toDouble()?.let { if (it < 1.0) return it }
        } catch (e: Exception) {}
        getFieldDouble(drop, "chance")?.let { if (it < 1.0) return it }
        return 1.0
    }

    private fun handleItemDrop(
        itemDef: String, baseAmountRaw: Any, baseChance: Double, mfMultiplier: Double,
        event: MythicMobDeathEvent, killer: Player, magicFind: Double,
        mythicItemObject: Any? = null, skipRareDropCheck: Boolean = false
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

        val rolled = ThreadLocalRandom.current().nextDouble()
        if (DEBUG) println("DEBUG: '$itemDef' 확률 체크: rolled=${"%.4f".format(rolled)} vs finalChance=${"%.4f".format(finalChance)}")

        if (rolled <= finalChance) {
            val rolledBaseAmount = when (baseAmountRaw) {
                is Int -> baseAmountRaw
                is IntRange -> baseAmountRaw.random()
                else -> 1
            }
            val finalAmount = rolledBaseAmount * amountMultiplier

            if (DEBUG) println("DEBUG: '$itemDef' 드롭 성공! finalAmount=$finalAmount")

            if (finalAmount > 0) {
                if (!skipRareDropCheck) {
                    checkAndAnnounceRareDrop(itemDef, baseChance, finalAmount, killer, magicFind, mythicItemObject)
                }

                val itemStack = generateItem(itemDef, 1, mythicItemObject)
                if (itemStack != null) {
                    if (DEBUG) println("DEBUG: 아이템 생성 성공: ${itemStack.type}")

                    val sackSlot = findSackSlot(killer, itemStack)
                    if (sackSlot != null) {
                        addToSack(killer, sackSlot, finalAmount)
                        if (DEBUG) println("[SackIntegration] ${killer.name}의 가방 슬롯 ${sackSlot}에 ${itemStack.type} x${finalAmount} 추가됨")
                    } else {
                        if (DEBUG) println("DEBUG: 가방 미등록 아이템 -> 월드 드롭")
                        val maxStackSize = 64
                        val fullStacks = finalAmount / maxStackSize
                        val remainder = finalAmount % maxStackSize

                        repeat(fullStacks) {
                            generateItem(itemDef, maxStackSize, mythicItemObject)?.let { event.drops.add(it) }
                        }
                        if (remainder > 0) {
                            generateItem(itemDef, remainder, mythicItemObject)?.let { event.drops.add(it) }
                        }
                    }
                } else {
                    if (DEBUG) println("DEBUG: 아이템 생성 실패: $itemDef")
                }
            }
        }
    }

    private fun findSackSlot(player: Player, itemStack: ItemStack): Int? {
        try {
            if (DEBUG) println("[SackIntegration] 가방 슬롯 검색 시작: ${player.name}, 아이템: ${itemStack.type}")
            val uuid = player.uniqueId.toString()

            for (slot in 1..27) {
                val varName = "sel_item.${uuid}::${slot}"
                if (DEBUG && slot <= 3) println("[SackIntegration] 슬롯 ${slot} 변수명: ${varName}")

                val rawValue = Variables.getVariable(varName, null, false)
                if (DEBUG && slot <= 3) {
                    if (rawValue != null) {
                        println("[SackIntegration] 슬롯 ${slot} 원본 값: ${rawValue}")
                        println("[SackIntegration] 슬롯 ${slot} 값 타입: ${rawValue.javaClass.name}")
                    } else {
                        println("[SackIntegration] 슬롯 ${slot} 값: null")
                    }
                }

                val registeredItem = when (rawValue) {
                    is ItemType -> rawValue.random
                    is ItemStack -> rawValue
                    else -> null
                }

                if (DEBUG && registeredItem != null && slot <= 3) {
                    println("[SackIntegration] 슬롯 ${slot}에 등록된 아이템: ${registeredItem.type}")
                }

                if (registeredItem != null && isSameItem(registeredItem, itemStack)) {
                    if (DEBUG) println("[SackIntegration] 매칭 성공! 슬롯 ${slot}")
                    return slot
                }
            }

            if (DEBUG) println("[SackIntegration] 매칭되는 슬롯 없음")
        } catch (e: Exception) {
            println("[SackIntegration] 가방 슬롯 검색 오류: ${e.message}")
            e.printStackTrace()
        }
        return null
    }

    private fun isSameItem(item1: ItemStack, item2: ItemStack): Boolean {
        if (item1.type != item2.type) return false
        val meta1 = item1.itemMeta
        val meta2 = item2.itemMeta

        if (meta1 == null && meta2 == null) return true
        if (meta1 == null || meta2 == null) return false

        if (meta1.hasCustomModelData() != meta2.hasCustomModelData()) return false
        if (meta1.hasCustomModelData() && meta1.customModelData != meta2.customModelData) return false
        if (meta1.hasDisplayName() != meta2.hasDisplayName()) return false
        if (meta1.hasDisplayName() && meta1.displayName != meta2.displayName) return false

        return true
    }

    private fun addToSack(player: Player, slot: Int, amount: Int) {
        try {
            val uuid = player.uniqueId.toString()
            val varName = "amount.sel_item.${uuid}::${slot}"
            val amountObj = Variables.getVariable(varName, null, false)
            val currentAmount = if (amountObj is Number) amountObj.toLong() else 0L
            val newAmount = currentAmount + amount

            Variables.setVariable(varName, newAmount, null, false)
            if (DEBUG) println("[SackIntegration] ${player.name}의 슬롯 ${slot}: ${currentAmount} -> ${newAmount}")
        } catch (e: Exception) {
            println("[SackIntegration] 가방 저장 오류: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun checkAndAnnounceRareDrop(itemDef: String, originalChance: Double, amount: Int, killer: Player, magicFind: Double, mythicItemObject: Any?) {
        val itemDefUpper = itemDef.split("{")[0].uppercase()
        if (blacklistedItems.contains(itemDefUpper) || blacklistedItems.contains(itemDef)) return

        val tier = rarityTiers.firstOrNull {
            it.enabled && originalChance < it.maxChance && originalChance >= it.minChance
        } ?: return

        val itemName = getItemDisplayName(itemDef, mythicItemObject)
        val mfAppliedChance = originalChance * (1.0 + (magicFind / 100.0))
        val displayChance = (mfAppliedChance.coerceAtMost(1.0) * 100).let {
            when {
                it >= 10.0 -> String.format("%.1f%%", it)
                it >= 1.0 -> String.format("%.2f%%", it)
                else -> String.format("%.3f%%", it)
            }
        }

        val message = ChatColor.translateAlternateColorCodes('&', tier.message)
            .replace("{item}", itemName)
            .replace("{amount}", amount.toString())
            .replace("{chance}", displayChance)
            .replace("{player}", killer.name)
            .replace("{magicfind}", String.format("%.0f%%", magicFind))

        if (tier.broadcast) {
            Bukkit.getOnlinePlayers().forEach { player ->
                player.sendMessage(message)
                playCustomSound(player, tier.sound, tier.volume, tier.pitch)
            }
        } else {
            killer.sendMessage(message)
            playCustomSound(killer, tier.sound, tier.volume, tier.pitch)
        }
    }

    private fun getItemDisplayName(itemDef: String, mythicItemObject: Any?): String {
        try {
            if (mythicItemObject != null) {
                val displayMethod = mythicItemObject.javaClass.getMethod("getDisplayName")
                val displayName = displayMethod.invoke(mythicItemObject) as? String
                if (!displayName.isNullOrEmpty()) {
                    return ChatColor.translateAlternateColorCodes('&', displayName)
                }
            }

            val itemOpt = MythicBukkit.inst().itemManager.getItem(itemDef)
            if (itemOpt.isPresent) {
                val item = itemOpt.get()
                val displayName = item.displayName
                if (!displayName.isNullOrEmpty()) {
                    return ChatColor.translateAlternateColorCodes('&', displayName)
                }
            }

            val mat = Material.getMaterial(itemDef.split("{")[0].uppercase())
            if (mat != null) {
                return mat.name.lowercase().split("_").joinToString(" ") {
                    it.replaceFirstChar { c -> c.uppercase() }
                }
            }
        } catch (e: Exception) {
            if (DEBUG) e.printStackTrace()
        }
        return itemDef
    }

    private fun playCustomSound(player: Player, soundName: String, volume: Float, pitch: Float) {
        try {
            player.playSound(player.location, soundName, volume, pitch)
        } catch (e: Exception) {
            try {
                val fallbackSound = when {
                    soundName.contains("incarnate") -> Sound.ENTITY_ENDER_DRAGON_GROWL
                    soundName.contains("rare") || soundName.contains("pray") -> Sound.ENTITY_PLAYER_LEVELUP
                    else -> Sound.ENTITY_EXPERIENCE_ORB_PICKUP
                }
                player.playSound(player.location, fallbackSound, volume, pitch)
            } catch (e2: Exception) {
                if (DEBUG) e2.printStackTrace()
            }
        }
    }

    private fun parseAmountRange(str: String): Int = try {
        if (str.contains("-")) {
            val s = str.split("-")
            ThreadLocalRandom.current().nextInt(s[0].toIntOrNull() ?: 1, (s[1].toIntOrNull() ?: 1) + 1)
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

    private fun parseAmountObject(obj: Any): Any = try {
        val minField = getFieldValue(obj, "min")
        val maxField = getFieldValue(obj, "max")
        if (minField is Number && maxField is Number) {
            minField.toInt()..maxField.toInt()
        } else (obj as? Number)?.toInt() ?: 1
    } catch (e: Exception) { 1 }

    private fun generateItem(itemDef: String, amount: Int, mythicItemObject: Any?): ItemStack? {
        return try {
            if (mythicItemObject != null) {
                val genMethod = mythicItemObject.javaClass.getMethod("generateItemStack", Int::class.javaPrimitiveType)
                convertToBukkitStack(genMethod.invoke(mythicItemObject, amount), amount)
            } else if (itemDef.contains("{") || MythicBukkit.inst().itemManager.getItem(itemDef).isPresent) {
                val itemOpt = MythicBukkit.inst().itemManager.getItem(itemDef)
                if (itemOpt.isPresent) convertToBukkitStack(itemOpt.get().generateItemStack(amount), amount) else null
            } else {
                val mat = Material.getMaterial(itemDef.split("{")[0].uppercase()) ?: return null
                ItemStack(mat, amount)
            }
        } catch (e: Exception) { null }
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

    private fun getFieldDouble(instance: Any, fieldName: String): Double? =
        (getFieldValue(instance, fieldName) as? Number)?.toDouble()
}