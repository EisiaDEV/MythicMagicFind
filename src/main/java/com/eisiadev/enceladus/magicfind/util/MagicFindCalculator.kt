package com.eisiadev.enceladus.magicfind.util

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
        val id: String,
        val enabled: Boolean,
        val minChance: Double,
        val maxChance: Double,
        val message: String,
        val sound: String,
        val volume: Float,
        val pitch: Float,
        val broadcast: Boolean
    )

    private var rarityTiers = mutableListOf<RarityTier>()
    private var blacklistedItems = mutableSetOf<String>()
    private var blacklistedDropTables = mutableSetOf<String>()

    fun initialize(plugin: JavaPlugin) {
        configFile = File(plugin.dataFolder, "config.yml")

        if (!configFile!!.exists()) {
            plugin.saveResource("config.yml", false)
        }

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

            // 블랙리스트 로드
            val blacklistSection = config.getConfigurationSection("blacklist")
            if (blacklistSection != null) {
                blacklistedItems.addAll(
                    blacklistSection.getStringList("items").map { it.uppercase() }
                )
                blacklistedDropTables.addAll(
                    blacklistSection.getStringList("droptables")
                )
                println("[MagicFind] Loaded ${blacklistedItems.size} blacklisted items and ${blacklistedDropTables.size} blacklisted droptables")
            }

            val section = config.getConfigurationSection("rare_drops") ?: run {
                loadDefaultTiers()
                return
            }

            section.getKeys(false).forEach { key ->
                val tierSection = section.getConfigurationSection(key) ?: return@forEach

                rarityTiers.add(RarityTier(
                    id = key,
                    enabled = tierSection.getBoolean("enabled", true),
                    minChance = tierSection.getDouble("min_chance", 0.0),
                    maxChance = tierSection.getDouble("max_chance", 1.0),
                    message = tierSection.getString("message", "{item} x{amount}") ?: "{item} x{amount}",
                    sound = tierSection.getString("sound", "ENTITY_EXPERIENCE_ORB_PICKUP") ?: "ENTITY_EXPERIENCE_ORB_PICKUP",
                    volume = tierSection.getDouble("volume", 1.0).toFloat(),
                    pitch = tierSection.getDouble("pitch", 1.0).toFloat(),
                    broadcast = tierSection.getBoolean("broadcast", false)
                ))
            }

            // 확률 범위 순으로 정렬 (높은 확률 -> 낮은 확률)
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

            rawDropLines.forEach { line ->
                processConfigLine(line, mfMultiplier, event, killer, magicFind)
            }

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
            val dropManager = MythicBukkit.inst().dropManager
            val dropTableOpt = dropManager.getDropTable(itemDef)

            if (dropTableOpt.isPresent) {
                // 드롭테이블이 블랙리스트에 있는지 확인
                val isBlacklisted = blacklistedDropTables.contains(itemDef)

                val tableChance = chanceStr.toDoubleOrNull() ?: 1.0
                val tableRepeats = parseAmountRange(amountStr)

                if (ThreadLocalRandom.current().nextDouble() <= tableChance) {
                    if (DEBUG) println("DropTable '$itemDef' 진입 (반복: $tableRepeats)")
                    val dropTable = dropTableOpt.get()

                    repeat(tableRepeats) {
                        processDropTableContent(dropTable, mfMultiplier, event, killer, magicFind, isBlacklisted)
                    }
                }
                return
            }
        }

        val baseChance = chanceStr.toDoubleOrNull() ?: 1.0
        val baseAmountRaw = parseRawAmount(amountStr)

        handleItemDrop(itemDef, baseAmountRaw, baseChance, mfMultiplier, event, killer, magicFind)
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
                    handleItemDrop(
                        itemDef = itemInternalName,
                        baseAmountRaw = baseAmountRaw,
                        baseChance = baseChance,
                        mfMultiplier = mfMultiplier,
                        event = event,
                        killer = killer,
                        magicFind = magicFind,
                        mythicItemObject = itemField,
                        skipRareDropCheck = isFromBlacklistedTable
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
        } catch (e: Exception) {}

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
        killer: Player,
        magicFind: Double,
        mythicItemObject: Any? = null,
        skipRareDropCheck: Boolean = false
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
                // 블랙리스트 체크 및 희귀 드롭 알림
                if (!skipRareDropCheck) {
                    checkAndAnnounceRareDrop(itemDef, baseChance, finalAmount, killer, magicFind, mythicItemObject)
                }

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

    private fun checkAndAnnounceRareDrop(
        itemDef: String,
        originalChance: Double,
        amount: Int,
        killer: Player,
        magicFind: Double,
        mythicItemObject: Any?
    ) {
        // 아이템이 블랙리스트에 있는지 확인
        val itemDefUpper = itemDef.split("{")[0].uppercase()
        if (blacklistedItems.contains(itemDefUpper) || blacklistedItems.contains(itemDef)) {
            return
        }

        // 활성화된 티어 중 원본 확률이 범위에 해당하는 가장 흔한(높은 확률) 티어 찾기
        val tier = rarityTiers.firstOrNull {
            it.enabled && originalChance < it.maxChance && originalChance >= it.minChance
        } ?: return

        val itemName = getItemDisplayName(itemDef, mythicItemObject)

        // MF 적용 후 확률 계산 (표시용)
        val mfAppliedChance = originalChance * (1.0 + (magicFind / 100.0))
        val displayChance = (mfAppliedChance.coerceAtMost(1.0) * 100).let {
            when {
                it >= 10.0 -> String.format("%.1f%%", it)
                it >= 1.0 -> String.format("%.2f%%", it)
                else -> String.format("%.3f%%", it)
            }
        }

        // 플레이스홀더 치환
        val message = ChatColor.translateAlternateColorCodes('&', tier.message)
            .replace("{item}", itemName)
            .replace("{amount}", amount.toString())
            .replace("{chance}", displayChance)
            .replace("{player}", killer.name)
            .replace("{magicfind}", String.format("%.0f%%", magicFind))

        // 메시지 전송 및 사운드 재생
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

    // --- 유틸리티 ---

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