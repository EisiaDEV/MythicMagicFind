package com.eisiadev.enceladus.pouches.rune

import com.eisiadev.enceladus.pouches.base.AbstractPouchDataManager
import com.eisiadev.enceladus.pouches.base.TierConfig
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class RuneDataManager(plugin: JavaPlugin) : 
    AbstractPouchDataManager(plugin, "rune_pouch_data.yml") {
    
    companion object {
        private const val DEBUG = false
    }
    
    override fun getPouchName() = "RunePouch"
    
    override fun getTierConfig(): TierConfig.Custom {
        return TierConfig.Custom(
            tierMapping = mapOf(
                "빈룬" to 1,
                "룬가방" to 2,
                "거대한룬가방" to 3,
                "룬정수" to 4
            ),
            conversionRates = listOf(
                1L,
                100L,
                10000L,
                1000000L
            )
        )
    }
    
    override fun compressPoints(points: Long): Map<Int, Long> {
        var remaining = points
        val result = mutableMapOf<Int, Long>()
        val config = getTierConfig()
        
        for (tier in 4 downTo 1) {
            val tierValue = config.getTierValue(tier)
            if (remaining >= tierValue) {
                val count = remaining / tierValue
                result[tier] = count
                remaining %= tierValue
            }
        }
        return result
    }
    
    fun addRuneToPouch(player: Player, itemInternalName: String, amount: Int): Boolean {
        val config = getTierConfig()
        val tier = config.getTierByItemName(itemInternalName) ?: return false
        
        val pointsPerItem = config.getTierValue(tier)
        val totalPoints = pointsPerItem * amount
        
        addPoints(player, totalPoints)
        
        if (DEBUG) {
            println("[RunePouch] ${player.name}에게 $itemInternalName(티어$tier) x${amount} 추가 (${totalPoints} 포인트)")
        }
        
        return true
    }
}