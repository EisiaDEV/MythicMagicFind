package com.eisiadev.enceladus.pouches.powder

import com.eisiadev.enceladus.pouches.base.AbstractPouchDataManager
import com.eisiadev.enceladus.pouches.base.TierConfig
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class PowderDataManager(plugin: JavaPlugin) :
    AbstractPouchDataManager(plugin, "powder_pouch_data.yml") {

    companion object {
        private const val DEBUG = false
    }

    override fun getPouchName() = "PowderPouch"

    override fun getTierConfig() = TierConfig.Exponential(
        minTier = 1,
        maxTier = 15,
        base = 10.0
    )

    override fun compressPoints(points: Long): Map<Int, Long> {
        var remaining = points
        val result = mutableMapOf<Int, Long>()
        val config = getTierConfig()

        for (tier in config.maxTier downTo config.minTier) {
            val tierValue = config.getTierValue(tier)
            if (remaining >= tierValue) {
                val count = remaining / tierValue
                result[tier] = count
                remaining %= tierValue
            }
        }
        return result
    }

    fun addPowderToPouch(player: Player, itemInternalName: String, amount: Int): Boolean {
        val tierMatch = Regex("신비한가루_(\\d+)").find(itemInternalName) ?: return false
        val tier = tierMatch.groupValues[1].toIntOrNull() ?: return false

        val config = getTierConfig()
        if (tier !in config.minTier..config.maxTier) return false

        val pointsPerItem = config.getTierValue(tier)
        val totalPoints = pointsPerItem * amount

        addPoints(player, totalPoints)

        if (DEBUG) {
            println("[PowderPouch] ${player.name}에게 $itemInternalName x${amount} 추가 (${totalPoints} 포인트)")
        }

        return true
    }
}