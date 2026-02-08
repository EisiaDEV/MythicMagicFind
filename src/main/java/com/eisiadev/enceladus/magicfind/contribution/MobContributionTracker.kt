package com.eisiadev.enceladus.magicfind.contribution

import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ContributorData(
    val player: Player,
    val damage: Double,
    val magicFind: Double,
    val lastHitTime: Long = System.currentTimeMillis()
)

data class MobTrackingData(
    val mobInternalName: String,
    val contributors: MutableMap<UUID, ContributorData> = ConcurrentHashMap(),
    val spawnTime: Long = System.currentTimeMillis()
)

object MobContributionTracker {
    private val mobData = ConcurrentHashMap<UUID, MobTrackingData>()

    fun startTracking(mobUuid: UUID, mobInternalName: String) {
        mobData[mobUuid] = MobTrackingData(mobInternalName)
    }

    fun isTracking(mobUuid: UUID): Boolean {
        return mobData.containsKey(mobUuid)
    }

    fun recordDamage(mobUuid: UUID, player: Player, damage: Double, magicFind: Double) {
        val data = mobData[mobUuid] ?: return
        
        data.contributors.compute(player.uniqueId) { _, existing ->
            if (existing == null) {
                ContributorData(player, damage, magicFind)
            } else {
                ContributorData(
                    player,
                    existing.damage + damage,
                    magicFind,
                    System.currentTimeMillis()
                )
            }
        }
    }

    fun getContributionRatios(mobUuid: UUID): Map<UUID, Double> {
        val data = mobData[mobUuid] ?: return emptyMap()
        val totalDamage = data.contributors.values.sumOf { it.damage }
        
        if (totalDamage <= 0.0) return emptyMap()
        
        return data.contributors.mapValues { (_, contributorData) ->
            contributorData.damage / totalDamage
        }
    }

    fun getWeightedAverageMagicFind(mobUuid: UUID): Double {
        val data = mobData[mobUuid] ?: return 0.0
        val ratios = getContributionRatios(mobUuid)
        
        return data.contributors.entries.sumOf { (uuid, contributorData) ->
            contributorData.magicFind * (ratios[uuid] ?: 0.0)
        }
    }

    fun getContributors(mobUuid: UUID): Map<UUID, ContributorData> {
        return mobData[mobUuid]?.contributors?.toMap() ?: emptyMap()
    }

    fun getMobInternalName(mobUuid: UUID): String? {
        return mobData[mobUuid]?.mobInternalName
    }

    fun cleanup(mobUuid: UUID) {
        mobData.remove(mobUuid)
    }

    fun cleanupOldEntries(timeoutMs: Long) {
        val now = System.currentTimeMillis()
        val iterator = mobData.entries.iterator()
        
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.spawnTime > timeoutMs) {
                iterator.remove()
            }
        }
    }

    fun getTrackingCount(): Int = mobData.size
}
