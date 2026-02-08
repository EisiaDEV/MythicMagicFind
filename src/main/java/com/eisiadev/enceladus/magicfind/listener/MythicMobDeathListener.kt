package com.eisiadev.enceladus.magicfind.listener

import com.eisiadev.enceladus.magicfind.MythicMagicFind
import com.eisiadev.enceladus.magicfind.contribution.ContributionConfig
import com.eisiadev.enceladus.magicfind.contribution.ContributorData
import com.eisiadev.enceladus.magicfind.contribution.MobContributionTracker
import com.eisiadev.enceladus.magicfind.util.DamageFormatter
import com.eisiadev.enceladus.magicfind.util.MagicFindCalculator
import com.eisiadev.enceladus.magicfind.util.SkriptVariableReader
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import java.util.UUID

class MythicMobDeathListener(
    private val contributionConfig: ContributionConfig
) : Listener {

    companion object {
        private const val DEBUG = false
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onMythicMobDeath(event: MythicMobDeathEvent) {
        val mobUuid = event.entity.uniqueId
        val mobInternalName = event.mobType.internalName

        if (DEBUG) {
            println("[MobDeath] 몹 사망: $mobInternalName (UUID: $mobUuid)")
            println("[MobDeath] 추적 중? ${MobContributionTracker.isTracking(mobUuid)}")
        }

        if (!MobContributionTracker.isTracking(mobUuid)) {
            if (DEBUG) println("[MobDeath] → 솔로 킬 처리")
            handleSoloKill(event)
            return
        }

        if (DEBUG) println("[MobDeath] → 레이드 킬 처리")
        handleRaidKill(event, mobUuid)
    }

    private fun handleSoloKill(event: MythicMobDeathEvent) {
        val killer = event.killer
        if (killer !is Player) {
            if (DEBUG) println("[MobDeath] 킬러가 플레이어 아님")
            return
        }

        val magicFind = SkriptVariableReader.getMagicFind(killer)
        MythicMagicFind.instance.pitySystem.loadPlayerData(killer)

        if (DEBUG) println("[MobDeath] 솔로 킬: ${killer.name}, MF: $magicFind")

        try {
            MagicFindCalculator.modifyDrops(event, killer, magicFind)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleRaidKill(event: MythicMobDeathEvent, mobUuid: UUID) {
        val contributors = MobContributionTracker.getContributors(mobUuid)

        if (DEBUG) {
            println("[MobDeath] 기여자 수: ${contributors.size}")
            contributors.forEach { (_, data) ->
                println("  - ${data.player.name}: ${DamageFormatter.format(data.damage)} (MF: ${data.magicFind}%)")
            }
        }

        if (contributors.isEmpty()) {
            if (DEBUG) println("[MobDeath] 기여자 없음, 정리만 수행")
            MobContributionTracker.cleanup(mobUuid)
            return
        }

        val mobInternalName = MobContributionTracker.getMobInternalName(mobUuid) ?: "Unknown"
        val weightedAvgMagicFind = MobContributionTracker.getWeightedAverageMagicFind(mobUuid)
        val contributionRatios = MobContributionTracker.getContributionRatios(mobUuid)

        if (DEBUG) {
            println("[MobDeath] 평균 MF: $weightedAvgMagicFind")
        }

        val validContributors = contributors.filter { (uuid, data) ->
            val contributionPercent = (contributionRatios[uuid] ?: 0.0) * 100
            val multiplier = contributionConfig.getDropMultiplier(contributionPercent)
            multiplier > 0.0
        }

        if (DEBUG) {
            println("[MobDeath] 전체 기여자: ${contributors.size}, 유효 기여자: ${validContributors.size}")
            validContributors.forEach { (uuid, data) ->
                val percent = (contributionRatios[uuid] ?: 0.0) * 100
                val mult = contributionConfig.getDropMultiplier(percent)
                println("  ✓ ${data.player.name}: ${percent}% (배율: ${mult})")
            }
        }

        val primaryKiller = validContributors.values.maxByOrNull { it.damage }?.player
            ?: contributors.values.maxByOrNull { it.damage }?.player // fallback

        if (primaryKiller != null) {
            MythicMagicFind.instance.pitySystem.loadPlayerData(primaryKiller)
        }

        try {
            if (DEBUG) println("[MobDeath] 드롭 처리 시작...")

            if (validContributors.isNotEmpty()) {
                MagicFindCalculator.modifyDropsForParty(
                    event,
                    validContributors,
                    contributionRatios,
                    weightedAvgMagicFind,
                    contributionConfig
                )
                if (DEBUG) println("[MobDeath] 드롭 처리 완료 (${validContributors.size}명)")
            } else {
                if (DEBUG) println("[MobDeath] 유효 기여자 없음, 드롭 처리 스킵")
                event.drops.clear()
            }

            if (DEBUG) println("[MobDeath] 알림 시작...")

            if (contributionConfig.isNotificationEnabled()) {
                notifyContributors(contributors, contributionRatios, weightedAvgMagicFind)
            } else {
                if (DEBUG) println("[MobDeath] 알림 비활성화됨")
            }

            if (contributionConfig.shouldBroadcastRanking()) {
                broadcastRaidResults(
                    mobInternalName,
                    contributors,
                    contributionRatios,
                    primaryKiller?.location
                )
            } else {
                if (DEBUG) println("[MobDeath] 랭킹 브로드캐스트 비활성화됨")
            }

        } catch (e: Exception) {
            println("[MobDeath] ⚠️ 에러 발생!")
            e.printStackTrace()
        } finally {
            MobContributionTracker.cleanup(mobUuid)
            if (DEBUG) println("[MobDeath] 정리 완료")
        }
    }

    private fun notifyContributors(
        contributors: Map<UUID, ContributorData>,
        contributionRatios: Map<UUID, Double>,
        avgMagicFind: Double
    ) {
        if (DEBUG) println("[MobDeath] 개인 알림 전송 중... (${contributors.size}명)")

        val sortedContributors = contributors.entries
            .sortedByDescending { it.value.damage }

        sortedContributors.forEachIndexed { index, (uuid, data) ->
            val player = data.player
            if (!player.isOnline) {
                if (DEBUG) println("[MobDeath]   - ${data.player.name}: 오프라인, 스킵")
                return@forEachIndexed
            }

            val contribution = contributionRatios[uuid] ?: 0.0
            val contributionPercent = contribution * 100
            val rank = index + 1
            val multiplier = contributionConfig.getDropMultiplier(contributionPercent)

            if (DEBUG) println("[MobDeath]   - ${player.name}: 순위 #$rank, 기여도 ${contributionPercent}%")

            player.sendMessage("§8§m                                        ")
            player.sendMessage("§6§l월드 레이드 참여 결과")
            player.sendMessage("")

            val rankColor = when (rank) {
                1 -> "§e§l"
                2 -> "§7§l"
                3 -> "§c§l"
                else -> "§f"
            }
            player.sendMessage("  §7순위: ${rankColor}#${rank}")

            if (contributionConfig.shouldShowDamage()) {
                val damageFormatted = DamageFormatter.format(data.damage)
                player.sendMessage("  §7총 데미지: §c${damageFormatted}")
            }

            if (contributionConfig.shouldShowContribution()) {
                player.sendMessage("  §7기여도: §e${DamageFormatter.formatContribution(contribution)}")
            }

            if (contributionConfig.shouldShowMagicFind()) {
                player.sendMessage("  §7당신의 MF: §b${DamageFormatter.formatMagicFind(data.magicFind)}")
                player.sendMessage("  §7적용된 평균 MF: §b${DamageFormatter.formatMagicFind(avgMagicFind)}")
            }

            if (multiplier > 0) {
                val multiplierPercent = (multiplier * 100).toInt()
                player.sendMessage("  §7드롭 배율: §a${multiplierPercent}%")
            } else {
                player.sendMessage("  §c최소 기여도 미달 (보상 없음)")
            }

            player.sendMessage("§8§m                                        ")
        }

        if (DEBUG) println("[MobDeath] 개인 알림 전송 완료")
    }

    private fun broadcastRaidResults(
        mobInternalName: String,
        contributors: Map<UUID, ContributorData>,
        contributionRatios: Map<UUID, Double>,
        location: org.bukkit.Location?
    ) {
        if (DEBUG) println("[MobDeath] 랭킹 브로드캐스트 시작...")

        val sortedContributors = contributors.entries
            .sortedByDescending { it.value.damage }
            .take(5)

        val targetPlayers = if (contributionConfig.shouldBroadcastGlobally()) {
            if (DEBUG) println("[MobDeath] 전체 서버 브로드캐스트")
            org.bukkit.Bukkit.getOnlinePlayers().toList()
        } else {
            if (location == null) {
                if (DEBUG) println("[MobDeath] 위치 정보 없음, 브로드캐스트 취소")
                return
            }
            val radius = contributionConfig.getBroadcastRadius()
            val nearby = location.world?.getNearbyEntities(location, radius, radius, radius)
                ?.filterIsInstance<Player>() ?: emptyList()

            if (DEBUG) println("[MobDeath] 반경 ${radius}m 내 ${nearby.size}명에게 브로드캐스트")
            nearby
        }

        if (targetPlayers.isEmpty()) {
            if (DEBUG) println("[MobDeath] 대상 플레이어 없음")
            return
        }

        targetPlayers.forEach { player ->
            player.sendMessage("")
            player.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            player.sendMessage("  §6§l월드 레이드 종료!")
            player.sendMessage("  §7보스: §e${mobInternalName}")
            player.sendMessage("")
            player.sendMessage("  §6상위 기여자:")

            sortedContributors.forEachIndexed { index, (uuid, data) ->
                val rank = index + 1
                val rankIcon = when (rank) {
                    1 -> "§e★"
                    2 -> "§7★"
                    3 -> "§c★"
                    else -> "§f•"
                }

                val contribution = contributionRatios[uuid] ?: 0.0
                val damageFormatted = DamageFormatter.format(data.damage)
                val contributionFormatted = DamageFormatter.formatContribution(contribution)

                player.sendMessage("  $rankIcon §f${data.player.name} §8- §c${damageFormatted} §8(§e${contributionFormatted}§8)")
            }

            player.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            player.sendMessage("")
        }

        if (DEBUG) println("[MobDeath] 랭킹 브로드캐스트 완료 (${targetPlayers.size}명)")
    }
}