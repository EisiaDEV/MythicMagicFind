package com.eisiadev.enceladus.mythicalpowderpouch

import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

object MythicalPowderPouch {

    private lateinit var pluginInstance: JavaPlugin
    private lateinit var dataManager: PowderDataManager
    private lateinit var inventoryManager: PowderInventoryManager
    private lateinit var eventHandler: PowderPouchEventHandler

    fun initialize(plugin: JavaPlugin) {
        pluginInstance = plugin

        dataManager = PowderDataManager(plugin)
        inventoryManager = PowderInventoryManager(dataManager)
        eventHandler = PowderPouchEventHandler(plugin, dataManager, inventoryManager)

        plugin.server.pluginManager.registerEvents(eventHandler, plugin)

        println("[PowderPouch] 초기화 완료")
    }

    fun addPowderToPouch(player: Player, itemInternalName: String, amount: Int): Boolean {
        return dataManager.addPowderToPouch(player, itemInternalName, amount)
    }

    @JvmStatic
    fun openPouchStatic(player: Player) {
        inventoryManager.openPouch(player, 0)
    }
}