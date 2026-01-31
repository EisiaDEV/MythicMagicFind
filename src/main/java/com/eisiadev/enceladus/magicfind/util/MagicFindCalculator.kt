package com.eisiadev.enceladus.magicfind.util

import com.eisiadev.enceladus.magicfind.config.MagicFindConfig
import com.eisiadev.enceladus.magicfind.drop.DropProcessor
import com.eisiadev.enceladus.magicfind.item.ItemGenerator
import com.eisiadev.enceladus.magicfind.notification.PlayerNotificationManager
import com.eisiadev.enceladus.magicfind.notification.RareDropNotifier
import com.eisiadev.enceladus.magicfind.pity.PitySystem
import com.eisiadev.enceladus.magicfind.pouch.PouchIntegration
import com.eisiadev.enceladus.magicfind.sack.SackIntegration
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent
import org.bukkit.entity.Player
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.plugin.java.JavaPlugin

object MagicFindCalculator {

    private const val DEBUG = false
    private lateinit var pluginInstance: JavaPlugin

    private lateinit var config: MagicFindConfig
    private lateinit var itemGenerator: ItemGenerator
    private lateinit var pouchIntegration: PouchIntegration
    private lateinit var sackIntegration: SackIntegration
    private lateinit var notificationManager: PlayerNotificationManager
    private lateinit var rareDropNotifier: RareDropNotifier
    private lateinit var dropProcessor: DropProcessor
    private lateinit var pitySystem: PitySystem

    fun initialize(plugin: JavaPlugin, notificationManager: PlayerNotificationManager) {
        pluginInstance = plugin

        SkriptVariableReader.initialize(plugin)

        config = MagicFindConfig(plugin)
        itemGenerator = ItemGenerator(DEBUG)
        pouchIntegration = PouchIntegration(DEBUG)
        sackIntegration = SackIntegration(DEBUG)

        this.notificationManager = notificationManager

        rareDropNotifier = RareDropNotifier(config, notificationManager, DEBUG)
        dropProcessor = DropProcessor(
            config, itemGenerator, pouchIntegration, sackIntegration, rareDropNotifier, DEBUG
        )

        pitySystem = PitySystem(plugin, itemGenerator, DEBUG)
        pitySystem.initialize()

        println("[MagicFind] MagicFindCalculator 초기화 완료")
    }

    fun loadConfig() {
        config.loadConfig()
    }

    fun modifyDrops(event: MythicMobDeathEvent, killer: Player, magicFind: Double) {
        if (event.entity.hasMetadata("magicfind_processed")) {
            if (DEBUG) println("[MagicFind] ⚠️ 이미 처리된 몹, 무시")
            return
        }
        event.entity.setMetadata(
            "magicfind_processed",
            FixedMetadataValue(pluginInstance, true)
        )

        dropProcessor.processDrops(event, killer, magicFind)
    }

    fun getItemGenerator(): ItemGenerator = itemGenerator

    fun getNotificationManager(): PlayerNotificationManager = notificationManager
}