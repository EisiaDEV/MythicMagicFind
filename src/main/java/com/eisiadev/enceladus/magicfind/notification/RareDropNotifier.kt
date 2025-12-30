package com.eisiadev.enceladus.magicfind.notification

import com.eisiadev.enceladus.magicfind.config.MagicFindConfig
import com.eisiadev.enceladus.magicfind.util.ReflectionCache
import io.lumine.mythic.bukkit.MythicBukkit
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import java.lang.reflect.Method

class RareDropNotifier(
    private val config: MagicFindConfig,
    private val debug: Boolean = false
) {
    
    private var mythicItemGetDisplayMethod: Method? = null
    
    fun checkAndAnnounceRareDrop(
        itemDef: String,
        originalChance: Double,
        amount: Int,
        killer: Player,
        magicFind: Double,
        mythicItemObject: Any?
    ) {
        val itemDefUpper = itemDef.split("{")[0].uppercase()
        if (config.blacklistedItems.contains(itemDefUpper) || 
            config.blacklistedItems.contains(itemDef)) {
            return
        }
        
        val tier = config.rarityTiers.firstOrNull {
            it.enabled && originalChance < it.maxChance && originalChance >= it.minChance
        } ?: return
        
        val itemName = getItemDisplayName(itemDef, mythicItemObject)
        val displayChance = formatChance(originalChance, magicFind)
        val message = formatMessage(tier, itemName, amount, displayChance, killer.name, magicFind)
        
        if (tier.broadcast) {
            broadcastToAll(message, tier)
        } else {
            notifyPlayer(killer, message, tier)
        }
    }
    
    private fun formatChance(originalChance: Double, magicFind: Double): String {
        val mfAppliedChance = originalChance * (1.0 + (magicFind / 100.0))
        val percentChance = (mfAppliedChance.coerceAtMost(1.0) * 100)
        
        return when {
            percentChance >= 10.0 -> String.format("%.1f%%", percentChance)
            percentChance >= 1.0 -> String.format("%.2f%%", percentChance)
            else -> String.format("%.3f%%", percentChance)
        }
    }
    
    private fun formatMessage(
        tier: MagicFindConfig.RarityTier,
        itemName: String,
        amount: Int,
        displayChance: String,
        playerName: String,
        magicFind: Double
    ): String {
        return ChatColor.translateAlternateColorCodes('&', tier.message)
            .replace("{item}", itemName)
            .replace("{amount}", amount.toString())
            .replace("{chance}", displayChance)
            .replace("{player}", playerName)
            .replace("{magicfind}", String.format("%.0f%%", magicFind))
    }
    
    private fun broadcastToAll(
        message: String,
        tier: MagicFindConfig.RarityTier
    ) {
        Bukkit.getOnlinePlayers().forEach { player ->
            player.sendMessage(message)
            playCustomSound(player, tier.sound, tier.volume, tier.pitch)
        }
    }
    
    private fun notifyPlayer(
        player: Player,
        message: String,
        tier: MagicFindConfig.RarityTier
    ) {
        player.sendMessage(message)
        playCustomSound(player, tier.sound, tier.volume, tier.pitch)
    }
    
    private fun getItemDisplayName(itemDef: String, mythicItemObject: Any?): String {
        try {
            // Try from mythic object first
            if (mythicItemObject != null) {
                val displayName = getDisplayNameFromObject(mythicItemObject)
                if (!displayName.isNullOrEmpty()) {
                    return ChatColor.translateAlternateColorCodes('&', displayName)
                }
            }
            
            // Try from MythicBukkit item manager
            val itemOpt = MythicBukkit.inst().itemManager.getItem(itemDef)
            if (itemOpt.isPresent) {
                val displayName = itemOpt.get().displayName
                if (!displayName.isNullOrEmpty()) {
                    return ChatColor.translateAlternateColorCodes('&', displayName)
                }
            }
            
            // Fallback to vanilla material name
            val mat = Material.getMaterial(itemDef.split("{")[0].uppercase())
            if (mat != null) {
                return mat.name.lowercase().split("_").joinToString(" ") {
                    it.replaceFirstChar { c -> c.uppercase() }
                }
            }
        } catch (e: Exception) {
            if (debug) e.printStackTrace()
        }
        return itemDef
    }
    
    private fun getDisplayNameFromObject(mythicItemObject: Any): String? {
        if (mythicItemGetDisplayMethod == null) {
            mythicItemGetDisplayMethod = ReflectionCache.getMethod(
                mythicItemObject.javaClass,
                "getDisplayName"
            )
        }
        return mythicItemGetDisplayMethod?.invoke(mythicItemObject) as? String
    }
    
    private fun playCustomSound(
        player: Player,
        soundName: String,
        volume: Float,
        pitch: Float
    ) {
        try {
            player.playSound(player.location, soundName, volume, pitch)
        } catch (e: Exception) {
            try {
                val fallbackSound = getFallbackSound(soundName)
                player.playSound(player.location, fallbackSound, volume, pitch)
            } catch (e2: Exception) {
                if (debug) e2.printStackTrace()
            }
        }
    }
    
    private fun getFallbackSound(soundName: String): Sound {
        return when {
            soundName.contains("incarnate") -> Sound.ENTITY_ENDER_DRAGON_GROWL
            soundName.contains("rare") || soundName.contains("pray") -> Sound.ENTITY_PLAYER_LEVELUP
            else -> Sound.ENTITY_EXPERIENCE_ORB_PICKUP
        }
    }
}