package com.eisiadev.enceladus.magicfind

import com.eisiadev.enceladus.magicfind.listener.MythicMobDeathListener
import com.eisiadev.enceladus.magicfind.pity.PityGUIManager
import com.eisiadev.enceladus.magicfind.pouch.PouchIntegration
import com.eisiadev.enceladus.magicfind.util.MagicFindCalculator
import com.eisiadev.enceladus.magicfind.pity.PitySystem
import com.eisiadev.enceladus.pouches.crayon.CrayonPouchManager
import com.eisiadev.enceladus.pouches.powder.PowderPouchManager
import com.eisiadev.enceladus.pouches.rift.RiftPouchManager
import com.eisiadev.enceladus.pouches.soul.SoulPouchManager
import com.eisiadev.enceladus.pouches.rune.RunePouchManager
import org.bukkit.plugin.java.JavaPlugin

class MythicMagicFind : JavaPlugin() {

    val pouchIntegration = PouchIntegration(debug = false)

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

        MagicFindCalculator.initialize(this)

        pitySystem = PitySystem(this, MagicFindCalculator.getItemGenerator(), debug = false)
        pitySystem.initialize()
        PityGUIManager.register()

        server.pluginManager.registerEvents(MythicMobDeathListener(), this)

        getCommand("magicfind")?.setExecutor(MagicFindCommand())

        PowderPouchManager.initialize(this)
        SoulPouchManager.initialize(this)
        RunePouchManager.initialize(this)
        CrayonPouchManager.initialize(this)
        RiftPouchManager.initialize(this)

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

        _instance = null
        logger.info("MythicMagicFind has been disabled!")
    }
}

// NOTE: MythicMobs does not expose drop chance API.
// Reflection is intentional and required for MagicFind logic.

// Tested with:
// - MythicMobs 5.6.2
// - Skript 2.9.5
// - Purpur 1.20.2 latest build
