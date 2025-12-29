package com.eisiadev.enceladus.magicfind.config

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class MagicFindConfig(plugin: JavaPlugin) {
    
    private val configFile: File = File(plugin.dataFolder, "config.yml")
    private lateinit var config: FileConfiguration
    
    val rarityTiers = mutableListOf<RarityTier>()
    val blacklistedItems = mutableSetOf<String>()
    val blacklistedDropTables = mutableSetOf<String>()
    
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
    
    init {
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false)
        }
        loadConfig()
    }
    
    fun loadConfig() {
        if (!configFile.exists()) {
            loadDefaultTiers()
            return
        }
        
        try {
            config = YamlConfiguration.loadConfiguration(configFile)
            rarityTiers.clear()
            blacklistedItems.clear()
            blacklistedDropTables.clear()
            
            loadBlacklists()
            loadRarityTiers()
            
            rarityTiers.sortByDescending { it.maxChance }
            println("[MagicFind] Loaded ${rarityTiers.size} rarity tiers from config")
        } catch (e: Exception) {
            e.printStackTrace()
            println("[MagicFind] Failed to load config, using default settings")
            loadDefaultTiers()
        }
    }
    
    private fun loadBlacklists() {
        config.getConfigurationSection("blacklist")?.let { section ->
            blacklistedItems.addAll(section.getStringList("items").map { it.uppercase() })
            blacklistedDropTables.addAll(section.getStringList("droptables"))
            println("[MagicFind] Loaded ${blacklistedItems.size} blacklisted items and ${blacklistedDropTables.size} blacklisted droptables")
        }
    }
    
    private fun loadRarityTiers() {
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
    }
    
    private fun loadDefaultTiers() {
        rarityTiers.clear()
        rarityTiers.addAll(listOf(
            RarityTier("occasional", true, 0.10, 0.20, "&9Occasional DROP! {item} &ex{amount} &f{chance}", "slayerdrop.occasional_drop", 1.0f, 1.0f, false),
            RarityTier("rare", true, 0.02, 0.10, "&5Rare DROP! {item} &ex{amount} &f{chance}", "slayerdrop.occasional_drop", 1.0f, 1.0f, false),
            RarityTier("extraordinary", true, 0.001, 0.02, "&6Extraordinary DROP! {item} &ex{amount} &f{chance}", "slayerdrop.rare_drop", 1.0f, 1.0f, false),
            RarityTier("pray", true, 0.0002, 0.001, "&dPray RNGesus DROP! {item} &ex{amount} &f{chance} &f- &b{player}", "slayerdrop.pray_rngesus_drop", 1.0f, 1.0f, true),
            RarityTier("incarnate", true, 0.00005, 0.0002, "&cRNGesus Incarnate DROP! {item} &ex{amount} &f{chance} &f- &b{player}", "slayerdrop.pray_rngesus_drop", 1.0f, 1.0f, true),
            RarityTier("insane", true, 0.000005, 0.00005, "&4RNGesus Insane DROP! {item} &ex{amount} &f{chance} &f- &b{player}", "slayerdrop.rngesus_incarnate_drop", 1.0f, 1.0f, true),
            RarityTier("unleashed", true, 0.0, 0.000005, "&5&lRNGesus Unleashed DROP! &r{item} &ex{amount} &f{chance} &f- &b{player}", "slayerdrop.rngesus_incarnate_drop", 1.0f, 1.5f, true)
        ))
    }
}