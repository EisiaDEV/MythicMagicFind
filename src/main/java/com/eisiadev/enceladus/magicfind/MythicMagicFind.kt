package com.eisiadev.enceladus.magicfind

import com.eisiadev.enceladus.magicfind.commands.NotificationToggleCommand
import com.eisiadev.enceladus.magicfind.config.MagicFindConfig
import com.eisiadev.enceladus.magicfind.contribution.ContributionConfig
import com.eisiadev.enceladus.magicfind.contribution.MobContributionTracker
import com.eisiadev.enceladus.magicfind.listener.DamageTrackingListener
import com.eisiadev.enceladus.magicfind.listener.MythicMobDeathListener
import com.eisiadev.enceladus.magicfind.notification.PlayerNotificationManager
import com.eisiadev.enceladus.magicfind.notification.RareDropNotifier
import com.eisiadev.enceladus.magicfind.pity.PityGUIManager
import com.eisiadev.enceladus.magicfind.pouch.PouchIntegration
import com.eisiadev.enceladus.magicfind.util.MagicFindCalculator
import com.eisiadev.enceladus.magicfind.pity.PitySystem
import com.eisiadev.enceladus.pouches.crayon.CrayonPouchManager
import com.eisiadev.enceladus.pouches.powder.PowderPouchManager
import com.eisiadev.enceladus.pouches.rift.RiftPouchManager
import com.eisiadev.enceladus.pouches.soul.SoulPouchManager
import com.eisiadev.enceladus.pouches.rune.RunePouchManager
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

class MythicMagicFind : JavaPlugin() {

    val pouchIntegration = PouchIntegration(debug = false)

    private lateinit var magicFindConfig: MagicFindConfig
    private lateinit var notificationManager: PlayerNotificationManager
    private lateinit var rareDropNotifier: RareDropNotifier
    private lateinit var contributionConfig: ContributionConfig

    lateinit var pitySystem: PitySystem
        private set

    companion object {
        private var _instance: MythicMagicFind? = null
        val instance: MythicMagicFind
            get() = _instance ?: throw IllegalStateException("Plugin not initialized")

        fun isInitialized() = _instance != null
    }

    override fun onEnable() {
        _instance = this

        if (!server.pluginManager.isPluginEnabled("MythicMobs")) {
            logger.severe("MythicMobs not found! Disabling plugin...")
            server.pluginManager.disablePlugin(this)
            return
        }

        if (!server.pluginManager.isPluginEnabled("Skript")) {
            logger.severe("Skript not found! Disabling plugin...")
            server.pluginManager.disablePlugin(this)
            return
        }

        magicFindConfig = MagicFindConfig(this)
        notificationManager = PlayerNotificationManager(this)

        MagicFindCalculator.initialize(this, notificationManager)

        pitySystem = PitySystem(this, MagicFindCalculator.getItemGenerator(), debug = false)
        pitySystem.initialize()
        PityGUIManager.register()

        setupContributionSystem()

        getCommand("magicfind")?.setExecutor(MagicFindCommand())

        PowderPouchManager.initialize(this)
        SoulPouchManager.initialize(this)
        RunePouchManager.initialize(this)
        CrayonPouchManager.initialize(this)
        RiftPouchManager.initialize(this)

        rareDropNotifier = RareDropNotifier(
            config = magicFindConfig,
            notificationManager = notificationManager,
            debug = config.getBoolean("debug", false)
        )

        getCommand("mfnotify")?.let { cmd ->
            val commandHandler = NotificationToggleCommand(magicFindConfig, notificationManager)
            cmd.setExecutor(commandHandler)
            cmd.tabCompleter = commandHandler
        }

        logger.info("MythicMagicFind has been enabled!")
    }

    override fun onDisable() {
        if (::pitySystem.isInitialized) {
            pitySystem.shutdown()
        }

        PowderPouchManager.shutdown()
        SoulPouchManager.shutdown()
        RunePouchManager.shutdown()
        CrayonPouchManager.shutdown()
        RiftPouchManager.shutdown()
        notificationManager.saveData()

        _instance = null
        logger.info("MythicMagicFind has been disabled!")
    }

    private fun setupContributionSystem() {

        contributionConfig = ContributionConfig(this)
        contributionConfig.loadConfig()

        val damageTrackingListener = DamageTrackingListener(contributionConfig)
        Bukkit.getPluginManager().registerEvents(damageTrackingListener, this)

        val mythicMobDeathListener = MythicMobDeathListener(contributionConfig)
        Bukkit.getPluginManager().registerEvents(mythicMobDeathListener, this)

        val cleanupInterval = contributionConfig.getCleanupIntervalSeconds() * 20L
        val cleanupTimeout = contributionConfig.getCleanupTimeoutSeconds() * 1000L

        Bukkit.getScheduler().runTaskTimerAsynchronously(this, Runnable {
            MobContributionTracker.cleanupOldEntries(cleanupTimeout)
        }, cleanupInterval, cleanupInterval)

        logger.info("[ContributionSystem] 기여도 시스템 초기화 완료")
    }

    fun getRareDropNotifier(): RareDropNotifier {
        return rareDropNotifier
    }

    fun getNotificationManager(): PlayerNotificationManager {
        return notificationManager
    }

    fun getMagicFindConfig(): MagicFindConfig {
        return magicFindConfig
    }

    fun getContributionConfig(): ContributionConfig {
        return contributionConfig
    }
}

// NOTE: MythicMobs does not expose drop chance API.
// Reflection is intentional and required for MagicFind logic.

// Tested with:
// - MythicMobs 5.6.2
// - Skript 2.9.5
// - Purpur 1.20.2 latest build