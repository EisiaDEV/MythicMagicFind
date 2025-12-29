package com.eisiadev.enceladus.magicfind.util

import com.eisiadev.enceladus.magicfind.config.MagicFindConfig
import com.eisiadev.enceladus.magicfind.drop.DropProcessor
import com.eisiadev.enceladus.magicfind.item.ItemGenerator
import com.eisiadev.enceladus.magicfind.notification.RareDropNotifier
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
    private lateinit var rareDropNotifier: RareDropNotifier
    private lateinit var dropProcessor: DropProcessor

    fun initialize(plugin: JavaPlugin) {
        pluginInstance = plugin

        SkriptVariableReader.initialize(plugin)

        config = MagicFindConfig(plugin)
        itemGenerator = ItemGenerator(DEBUG)
        pouchIntegration = PouchIntegration(DEBUG)
        sackIntegration = SackIntegration(DEBUG)
        rareDropNotifier = RareDropNotifier(config, DEBUG)
        dropProcessor = DropProcessor(
            config, itemGenerator, pouchIntegration, sackIntegration, rareDropNotifier, DEBUG
        )
        println("[MagicFind] MagicFindCalculator 초기화 완료")
    }

    fun loadConfig() {
        config.loadConfig()
    }

    fun modifyDrops(event: MythicMobDeathEvent, killer: Player, magicFind: Double) {
        // Prevent duplicate processing
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
}

// NOTE: MythicMobs does not expose drop chance API.
// Reflection is intentional and required for MagicFind logic.

// Tested with:
// - MythicMobs 5.6.2
// - Skript 2.9.5
// - Purpur 1.20.2 latest build