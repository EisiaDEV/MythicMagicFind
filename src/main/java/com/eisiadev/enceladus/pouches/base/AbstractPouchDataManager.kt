package com.eisiadev.enceladus.pouches.base

import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

abstract class AbstractPouchDataManager(
    plugin: JavaPlugin,
    dataFileName: String
) {
    protected val dataFile: File = File(plugin.dataFolder, dataFileName)
    protected val dataConfig: FileConfiguration
    
    init {
        if (!dataFile.exists()) {
            plugin.dataFolder.mkdirs()
            dataFile.createNewFile()
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile)
    }
    
    fun getPoints(player: Player): Long {
        return dataConfig.getLong("players.${player.uniqueId}", 0L)
    }
    
    fun setPoints(player: Player, points: Long) {
        dataConfig.set("players.${player.uniqueId}", points)
        saveData()
    }
    
    fun addPoints(player: Player, points: Long) {
        val current = getPoints(player)
        setPoints(player, current + points)
    }
    
    protected fun saveData() {
        try {
            dataConfig.save(dataFile)
        } catch (e: Exception) {
            println("[${getPouchName()}] 데이터 저장 실패:")
            e.printStackTrace()
        }
    }
    
    abstract fun getPouchName(): String
    abstract fun getTierConfig(): TierConfig
    abstract fun compressPoints(points: Long): Map<Int, Long>
}