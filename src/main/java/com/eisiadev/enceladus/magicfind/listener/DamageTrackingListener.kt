package com.eisiadev.enceladus.magicfind.listener

import com.eisiadev.enceladus.magicfind.contribution.ContributionConfig
import com.eisiadev.enceladus.magicfind.contribution.MobContributionTracker
import com.eisiadev.enceladus.magicfind.util.SkriptVariableReader
import io.lumine.mythic.bukkit.MythicBukkit
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.entity.Tameable
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntitySpawnEvent

class DamageTrackingListener(
    private val contributionConfig: ContributionConfig
) : Listener {

    companion object {
        private const val DEBUG = false
    }

    // 몹 스폰 시 추적 대상인지 확인
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    fun onMythicMobSpawn(event: io.lumine.mythic.bukkit.events.MythicMobSpawnEvent) {
        val mobInternalName = event.mobType.internalName

        if (DEBUG) {
            println("[DamageTracking] MythicMob 스폰 감지: $mobInternalName")
        }

        if (contributionConfig.shouldTrackContribution(mobInternalName)) {
            MobContributionTracker.startTracking(event.entity.uniqueId, mobInternalName)

            if (DEBUG) {
                println("[DamageTracking] ✓ 추적 시작: $mobInternalName (UUID: ${event.entity.uniqueId})")
            }

            event.entity.customName = "§c§l[월드 레이드] §6${event.mobType.displayName}"
            event.entity.isCustomNameVisible = true
        } else {
            if (DEBUG) {
                println("[DamageTracking] ✗ 추적 제외: $mobInternalName")
                println("[DamageTracking]   등록된 패턴: ${contributionConfig.getTrackedPatterns()}")
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        val mythicMob = MythicBukkit.inst().mobManager.getMythicMobInstance(event.entity)
        if (mythicMob != null && DEBUG) {
            val isTracking = MobContributionTracker.isTracking(event.entity.uniqueId)
            if (!isTracking) {
            }
        }

        if (!MobContributionTracker.isTracking(event.entity.uniqueId)) {
            return
        }

        val damager = when (val d = event.damager) {
            is Player -> d
            is Projectile -> d.shooter as? Player
            is Tameable -> d.owner as? Player
            else -> null
        } ?: return

        val damage = event.finalDamage
        val magicFind = SkriptVariableReader.getMagicFind(damager)

        if (DEBUG) {
            println("[DamageTracking] 데미지 기록: ${damager.name} -> ${damage} (MF: ${magicFind}%)")
        }

        MobContributionTracker.recordDamage(
            event.entity.uniqueId,
            damager,
            damage,
            magicFind
        )
    }
}