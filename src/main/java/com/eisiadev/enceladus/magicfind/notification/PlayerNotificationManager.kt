package com.eisiadev.enceladus.magicfind.notification

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID

class PlayerNotificationManager(private val plugin: JavaPlugin) {
    
    private val dataFile: File = File(plugin.dataFolder, "player_notifications.yml")
    private lateinit var config: FileConfiguration

    private val disabledNotifications = mutableMapOf<UUID, MutableSet<String>>()
    
    init {
        loadData()
    }
    
    fun loadData() {
        if (!dataFile.exists()) {
            dataFile.parentFile.mkdirs()
            dataFile.createNewFile()
        }
        
        config = YamlConfiguration.loadConfiguration(dataFile)
        disabledNotifications.clear()
        
        config.getKeys(false).forEach { uuidStr ->
            try {
                val uuid = UUID.fromString(uuidStr)
                val disabled = config.getStringList(uuidStr).toMutableSet()
                disabledNotifications[uuid] = disabled
            } catch (e: Exception) {
                plugin.logger.warning("Invalid UUID in player_notifications.yml: $uuidStr")
            }
        }
    }
    
    fun saveData() {
        disabledNotifications.forEach { (uuid, tiers) ->
            config.set(uuid.toString(), tiers.toList())
        }
        config.save(dataFile)
    }
    
    fun isNotificationEnabled(player: Player, tierId: String): Boolean {
        val disabled = disabledNotifications[player.uniqueId] ?: return true
        return !disabled.contains(tierId)
    }
    
    fun toggleNotification(player: Player, tierId: String): Boolean {
        val disabled = disabledNotifications.getOrPut(player.uniqueId) { mutableSetOf() }
        
        val isNowEnabled = if (disabled.contains(tierId)) {
            disabled.remove(tierId)
            true
        } else {
            disabled.add(tierId)
            false
        }
        
        saveData()
        return isNowEnabled
    }
    
    fun getDisabledTiers(player: Player): Set<String> {
        return disabledNotifications[player.uniqueId]?.toSet() ?: emptySet()
    }
    
    fun enableAll(player: Player) {
        disabledNotifications[player.uniqueId]?.clear()
        saveData()
    }
    
    fun disableAll(player: Player, tiers: List<String>) {
        disabledNotifications[player.uniqueId] = tiers.toMutableSet()
        saveData()
    }
}