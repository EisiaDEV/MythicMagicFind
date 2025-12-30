package com.eisiadev.enceladus.magicfind

import com.eisiadev.enceladus.magicfind.listener.MythicMobDeathListener
import com.eisiadev.enceladus.magicfind.pouch.PouchIntegration
import com.eisiadev.enceladus.magicfind.util.MagicFindCalculator
import com.eisiadev.enceladus.pouches.crayon.CrayonPouchManager
import com.eisiadev.enceladus.pouches.powder.PowderPouchManager
import com.eisiadev.enceladus.pouches.soul.SoulPouchManager
import com.eisiadev.enceladus.pouches.rune.RunePouchManager
import org.bukkit.plugin.java.JavaPlugin

class MagicFindPlugin : JavaPlugin() {

    val pouchIntegration = PouchIntegration(debug = false)

    companion object {
        private var _instance: MagicFindPlugin? = null
        val instance: MagicFindPlugin
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

        MagicFindCalculator.initialize(this)
        server.pluginManager.registerEvents(MythicMobDeathListener(), this)

        getCommand("magicfind")?.setExecutor(MagicFindCommand())

        PowderPouchManager.initialize(this)
        SoulPouchManager.initialize(this)
        RunePouchManager.initialize(this)
        CrayonPouchManager.initialize(this)

        logger.info("MagicFindDrops has been enabled!")
    }

    override fun onDisable() {
        PowderPouchManager.shutdown()
        SoulPouchManager.shutdown()
        RunePouchManager.shutdown()
        CrayonPouchManager.shutdown()

        _instance = null
        logger.info("MagicFindDrops has been disabled!")
    }
}