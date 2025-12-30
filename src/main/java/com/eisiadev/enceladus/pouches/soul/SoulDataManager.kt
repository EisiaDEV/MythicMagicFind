package com.eisiadev.enceladus.pouches.soul

import com.eisiadev.enceladus.pouches.base.AbstractPouchDataManager
import com.eisiadev.enceladus.pouches.base.TierConfig
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class SoulDataManager(plugin: JavaPlugin) : 
    AbstractPouchDataManager(plugin, "soul_pouch_data.yml") {
    
    companion object {
        private const val DEBUG = false
    }
    
    override fun getPouchName() = "SoulPouch"
    
    override fun getTierConfig(): TierConfig.Custom {
        val rates = mutableListOf<Long>()
        rates.add(1L)                           // 티어 1: 혼파편 (기준)
        rates.add(rates[0] * 64)                // 티어 2: 불순한혼 = 64개
        rates.add(rates[1] * 13)                // 티어 3: 가공한혼 = 64 * 13
        rates.add(rates[2] * 20)                // 티어 4: 정제된혼 = 64 * 13 * 20
        rates.add(rates[3] * 17)                // 티어 5: 정화의혼 = 64 * 13 * 20 * 17
        rates.add(rates[4] * 20)                // 티어 6: 격세의혼
        rates.add(rates[5] * 20)                // 티어 7: 천명의혼
        rates.add(rates[6] * 15)                // 티어 8: 현극의혼
        rates.add(rates[7] * 10)                // 티어 9: 공명의혼
        rates.add(rates[8] * 10)                // 티어 10: 영속의혼
        
        return TierConfig.Custom(
            tierMapping = mapOf(
                "혼파편" to 1,
                "불순한혼" to 2,
                "가공한혼" to 3,
                "정제된혼" to 4,
                "정화의혼" to 5,
                "격세의혼" to 6,
                "천명의혼" to 7,
                "현극의혼" to 8,
                "공명의혼" to 9,
                "영속의혼" to 10
            ),
            conversionRates = rates
        )
    }
    
    override fun compressPoints(points: Long): Map<Int, Long> {
        var remaining = points
        val result = mutableMapOf<Int, Long>()
        val config = getTierConfig()
        
        for (tier in config.conversionRates.size downTo 1) {
            val tierValue = config.getTierValue(tier)
            if (remaining >= tierValue) {
                val count = remaining / tierValue
                result[tier] = count
                remaining %= tierValue
            }
        }
        return result
    }
    
    fun addSoulToPouch(player: Player, itemInternalName: String, amount: Int): Boolean {
        val config = getTierConfig()
        val tier = config.getTierByItemName(itemInternalName) ?: return false
        
        val pointsPerItem = config.getTierValue(tier)
        val totalPoints = pointsPerItem * amount
        
        addPoints(player, totalPoints)
        
        if (DEBUG) {
            println("[SoulPouch] ${player.name}에게 $itemInternalName(티어$tier) x${amount} 추가 (${totalPoints} 포인트)")
        }
        
        return true
    }
}