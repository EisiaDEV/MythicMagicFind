package com.eisiadev.enceladus.magicfind

import com.eisiadev.enceladus.magicfind.listener.MythicMobDeathListener
import com.eisiadev.enceladus.magicfind.pouch.PouchIntegration
import com.eisiadev.enceladus.magicfind.util.MagicFindCalculator
import org.bukkit.plugin.java.JavaPlugin
import com.eisiadev.enceladus.pouches.powder.MythicalPowderPouch
import com.eisiadev.enceladus.pouches.soul.SoulPouchManager
import com.eisiadev.enceladus.pouches.rune.RunePouchManager

class MagicFindPlugin : JavaPlugin() {

    val pouchIntegration = PouchIntegration(debug = false)

    companion object {
        lateinit var instance: MagicFindPlugin
            private set
    }

    override fun onEnable() {
        instance = this

        // Check dependencies
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

        // Register listeners
        MagicFindCalculator.initialize(this)
        server.pluginManager.registerEvents(MythicMobDeathListener(), this)

        // Register commands
        getCommand("magicfind")?.setExecutor(MagicFindCommand())

        MythicalPowderPouch.initialize(this)
        SoulPouchManager.initialize(this)
        RunePouchManager.initialize(this)

        logger.info("MagicFindDrops has been enabled!")
    }

    override fun onDisable() {
        logger.info("MagicFindDrops has been disabled!")
    }
}