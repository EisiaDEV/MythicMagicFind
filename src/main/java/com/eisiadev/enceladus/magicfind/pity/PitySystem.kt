package com.eisiadev.enceladus.magicfind.pity

import com.eisiadev.enceladus.magicfind.item.ItemGenerator
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

class PitySystem(
    plugin: JavaPlugin,
    private val itemGenerator: ItemGenerator,
    private val debug: Boolean = false
) {

    private val pityStorage = PityStorage(plugin, debug)

    // 메모리 캐시: playerUUID -> (itemInternalName -> count)
    private val memoryCache = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()

    fun initialize() {
        pityStorage.initialize()
        pityStorage.setMemoryCache(memoryCache)
        if (debug) println("[PitySystem] 천장 시스템 초기화 완료")
    }

    fun shutdown() {
        pityStorage.saveAll(memoryCache)
        if (debug) println("[PitySystem] 천장 시스템 종료 - 데이터 저장 완료")
    }

    fun incrementPityCount(player: Player, itemInternalName: String, baseChance: Double, magicFind: Double) {
        if (baseChance >= 0.01) return // 1% 이상은 천장 미적용

        val uuid = player.uniqueId.toString()
        val playerData = memoryCache.getOrPut(uuid) { ConcurrentHashMap() }

        val currentCount = playerData.getOrDefault(itemInternalName, 0)
        val newCount = currentCount + 1
        playerData[itemInternalName] = newCount

        pityStorage.markDirty(uuid)

        val requiredKills = calculateRequiredKills(baseChance, magicFind)

        if (debug) {
            println("[PitySystem] ${player.name} - $itemInternalName: $newCount/$requiredKills")
        }
    }

    fun shouldGuaranteeDrop(player: Player, itemInternalName: String, baseChance: Double, magicFind: Double): Boolean {
        if (baseChance >= 0.01) return false

        val uuid = player.uniqueId.toString()
        val playerData = memoryCache.getOrPut(uuid) { ConcurrentHashMap() }

        val currentCount = playerData.getOrDefault(itemInternalName, 0)
        val requiredKills = calculateRequiredKills(baseChance, magicFind)

        return currentCount >= requiredKills
    }

    fun resetPityCount(player: Player, itemInternalName: String, guaranteed: Boolean = false) {
        val uuid = player.uniqueId.toString()
        val playerData = memoryCache.getOrPut(uuid) { ConcurrentHashMap() }

        if (playerData.containsKey(itemInternalName)) {
            playerData.remove(itemInternalName)
            pityStorage.markDirty(uuid)

            if (debug) {
                val reason = if (guaranteed) "[천장 확정]" else "[일반 드롭]"
                println("[PitySystem] ${player.name} - $itemInternalName 카운트 리셋 $reason")
            }
        }
    }

    private fun calculateRequiredKills(baseChance: Double, magicFind: Double): Int {
        val mfMultiplier = (magicFind + 100.0) / 100.0
        val effectiveChance = baseChance * mfMultiplier
        return floor(1.0 / effectiveChance).toInt().coerceAtLeast(1)
    }

    fun createPityDisplayItem(
        player: Player,
        itemInternalName: String,
        baseChance: Double,
        magicFind: Double
    ): ItemStack? {
        val uuid = player.uniqueId.toString()
        val playerData = memoryCache.getOrPut(uuid) { ConcurrentHashMap() }

        if (!playerData.containsKey(itemInternalName)) return null

        val currentKills = playerData.getOrDefault(itemInternalName, 0)
        val requiredKills = calculateRequiredKills(baseChance, magicFind)
        val baseItem = itemGenerator.generateItem(itemInternalName, 1) ?: return null
        val meta = baseItem.itemMeta ?: return baseItem
        val itemName = meta.displayName

        val newLore = mutableListOf<String>()
        newLore.add("${ChatColor.WHITE}")
        newLore.add("$itemName${ChatColor.WHITE}의 RNG 미터입니다.")

        val progressBar = createProgressBar(currentKills, requiredKills)
        newLore.add(progressBar)

        val killsDisplay = createKillsDisplay(currentKills, requiredKills)
        newLore.add(killsDisplay)

        meta.lore = newLore
        baseItem.itemMeta = meta

        return baseItem
    }

    private fun createProgressBar(current: Int, required: Int): String {
        val percentage = (current.toDouble() / required.toDouble() * 100.0).coerceIn(0.0, 100.0)
        val filledBars = (percentage / 5.0).toInt().coerceIn(0, 20)

        val filled = "${ChatColor.LIGHT_PURPLE}${"-".repeat(filledBars)}"
        val empty = "${ChatColor.WHITE}${"-".repeat(20 - filledBars)}"

        return filled + empty
    }

    private fun createKillsDisplay(current: Int, required: Int): String {
        val percentage = (current.toDouble() / required.toDouble() * 100.0)

        val currentColor = when {
            percentage < 33.33 -> ChatColor.RED
            percentage < 66.67 -> ChatColor.YELLOW
            else -> ChatColor.GREEN
        }

        val percentageColor = when {
            percentage < 33.33 -> ChatColor.RED
            percentage < 66.67 -> ChatColor.YELLOW
            else -> ChatColor.GREEN
        }

        val percentageText = String.format("%.1f%%", percentage)

        return "${ChatColor.WHITE}현재 킬 수 $currentColor${current}${ChatColor.WHITE}회 | 요구 킬 수 ${ChatColor.WHITE}${required}${ChatColor.WHITE}회 - $percentageColor$percentageText"
    }

    fun loadPlayerData(player: Player) {
        val uuid = player.uniqueId.toString()
        if (!memoryCache.containsKey(uuid)) {
            val loaded = pityStorage.loadPlayerData(uuid)
            if (loaded.isNotEmpty()) {
                memoryCache[uuid] = ConcurrentHashMap(loaded)
            }
        }
    }

    fun getAccessedItemNames(player: Player): Set<String> {
        val uuid = player.uniqueId.toString()
        val playerData = memoryCache[uuid] ?: return emptySet()
        return playerData.keys.toSet()
    }
}