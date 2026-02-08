package com.eisiadev.enceladus.magicfind.contribution

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class ContributionConfig(private val plugin: JavaPlugin) {
    
    private val configFile = File(plugin.dataFolder, "contribution_preset.yml")
    private var config: YamlConfiguration? = null

    private val trackedMobPatterns = mutableListOf<String>()

    var minContributionPercent: Double = 5.0
        private set

    private val contributionTiers = mutableMapOf<Double, Double>()

    fun loadConfig() {
        if (!configFile.exists()) {
            createDefaultConfig()
        }

        config = YamlConfiguration.loadConfiguration(configFile)

        trackedMobPatterns.clear()
        trackedMobPatterns.addAll(
            config?.getStringList("tracked-mobs") ?: listOf()
        )

        minContributionPercent = config?.getDouble("min-contribution-percent", 5.0) ?: 5.0

        contributionTiers.clear()
        config?.getConfigurationSection("contribution-tiers")?.let { section ->
            section.getKeys(false).forEach { tierKey ->
                val threshold = section.getDouble("$tierKey.threshold")
                val multiplier = section.getDouble("$tierKey.multiplier")
                contributionTiers[threshold] = multiplier
                println("[ContributionConfig] 티어 로드: $threshold -> $multiplier")
            }
        }
    }
    
    private fun createDefaultConfig() {
        configFile.parentFile.mkdirs()
        configFile.writeText("""
            # 기여도를 추적할 몬스터 이름 패턴
            # 몬스터의 내부 이름(MythicMobs ID)에 이 패턴이 포함되면 기여도를 추적합니다
            tracked-mobs:
              - "이그니스리드"
            
            # 최소 기여도 (이 퍼센트 미만은 보상 없음)
            min-contribution-percent: 5.0
            
            # 기여도별 드롭 배율
            # 기여도 X% 이상일 때 드롭 배율
            contribution-tiers:
              tier1:
                threshold: 30.0
                multiplier: 1.0
              tier2:
                threshold: 20.0
                multiplier: 0.8
              tier3:
                threshold: 10.0
                multiplier: 0.6
              tier4:
                threshold: 5.0
                multiplier: 0.4
            
            # 기여도 알림 설정
            notifications:
              enabled: true
              show-damage: true
              show-contribution: true
              show-magic-find: true
              broadcast-ranking: true
              broadcast-radius: 100.0
              broadcast-global: true
              
            # 데이터 정리 설정
            cleanup:
              # 몹이 죽지 않고 X초 후 데이터 자동 정리
              timeout-seconds: 300
              # 정리 작업 주기 (초)
              interval-seconds: 60
        """.trimIndent())
        
        plugin.logger.info("[ContributionConfig] 기본 설정 파일 생성됨")
    }

    fun shouldTrackContribution(mobInternalName: String): Boolean {
        return trackedMobPatterns.any { pattern ->
            mobInternalName.contains(pattern, ignoreCase = true)
        }
    }

    fun getDropMultiplier(contributionPercent: Double): Double {

        if (contributionPercent < minContributionPercent) {
            return 0.0
        }

        val sortedTiers = contributionTiers.keys.sortedDescending()
        for (threshold in sortedTiers) {
            if (contributionPercent >= threshold) {
                val result = contributionTiers[threshold] ?: 1.0
                return result
            }
        }
        return 0.0
    }
    
    fun isNotificationEnabled(): Boolean = 
        config?.getBoolean("notifications.enabled", true) ?: true
    
    fun shouldShowDamage(): Boolean =
        config?.getBoolean("notifications.show-damage", true) ?: true
        
    fun shouldShowContribution(): Boolean =
        config?.getBoolean("notifications.show-contribution", true) ?: true
        
    fun shouldShowMagicFind(): Boolean =
        config?.getBoolean("notifications.show-magic-find", true) ?: true
    
    fun shouldBroadcastRanking(): Boolean =
        config?.getBoolean("notifications.broadcast-ranking", true) ?: true
    
    fun getBroadcastRadius(): Double =
        config?.getDouble("notifications.broadcast-radius", 100.0) ?: 100.0
    
    fun getCleanupTimeoutSeconds(): Int =
        config?.getInt("cleanup.timeout-seconds", 300) ?: 300
        
    fun getCleanupIntervalSeconds(): Int =
        config?.getInt("cleanup.interval-seconds", 60) ?: 60

    fun shouldBroadcastGlobally(): Boolean =
        config?.getBoolean("notifications.broadcast-global", false) ?: false

    fun getTrackedPatterns(): List<String> {
        return trackedMobPatterns.toList()
    }
}
