package com.eisiadev.enceladus.mythicalpowderpouch

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import kotlin.math.pow

class PowderDataManager(plugin: JavaPlugin) {
    
    private val dataFile: File = File(plugin.dataFolder, "powder_pouch_data.yml")
    private val dataConfig: FileConfiguration
    
    companion object {
        private const val DEBUG = false
        
        val TIER_VALUES = (1..15).associateWith { tier ->
            10.0.pow(tier - 1).toLong()
        }
    }
    
    init {
        if (!dataFile.exists()) {
            plugin.dataFolder.mkdirs()
            dataFile.createNewFile()
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile)
        
        if (DEBUG) println("[PowderPouch] 데이터 매니저 초기화 완료")
    }
    
    fun getPoints(player: Player): Long {
        return dataConfig.getLong("players.${player.uniqueId}", 0L)
    }
    
    fun setPoints(player: Player, points: Long) {
        val uuid = player.uniqueId.toString()
        dataConfig.set("players.$uuid", points)
        saveData()
        
        if (DEBUG) {
            println("[PowderPouch] ${player.name}의 포인트 설정: $points")
        }
    }
    
    fun addPoints(player: Player, points: Long) {
        val current = getPoints(player)
        setPoints(player, current + points)
    }
    
    fun addPowderToPouch(player: Player, itemInternalName: String, amount: Int): Boolean {
        val tierMatch = Regex("신비한가루_(\\d+)").find(itemInternalName) ?: return false
        val tier = tierMatch.groupValues[1].toIntOrNull() ?: return false
        
        if (tier !in 1..15) return false
        
        val pointsPerItem = TIER_VALUES[tier] ?: return false
        val totalPoints = pointsPerItem * amount
        
        addPoints(player, totalPoints)
        
        if (DEBUG) {
            println("[PowderPouch] ${player.name}에게 $itemInternalName x${amount} 추가 (${totalPoints} 포인트)")
        }
        
        return true
    }
    
    fun compressPoints(points: Long): Map<Int, Long> {
        var remaining = points
        val result = mutableMapOf<Int, Long>()
        
        for (tier in 15 downTo 1) {
            val tierValue = TIER_VALUES[tier] ?: continue
            if (remaining >= tierValue) {
                val count = remaining / tierValue
                result[tier] = count
                remaining %= tierValue
            }
        }
        
        return result
    }
    
    private fun saveData() {
        try {
            dataConfig.save(dataFile)
        } catch (e: Exception) {
            println("[PowderPouch] 데이터 저장 실패:")
            e.printStackTrace()
        }
    }
}