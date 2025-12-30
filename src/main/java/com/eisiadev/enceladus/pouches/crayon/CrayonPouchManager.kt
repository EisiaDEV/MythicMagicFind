package com.eisiadev.enceladus.pouches.crayon

import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

object CrayonPouchManager {

    private lateinit var dataManager: CrayonDataManager
    private lateinit var inventoryManager: CrayonInventoryManager
    private lateinit var eventHandler: CrayonEventHandler

    fun initialize(plugin: JavaPlugin) {
        dataManager = CrayonDataManager(plugin)
        inventoryManager = CrayonInventoryManager(plugin, dataManager)
        eventHandler = CrayonEventHandler(plugin, dataManager, inventoryManager)

        plugin.server.pluginManager.registerEvents(eventHandler, plugin)

        println("[CrayonPouch] 초기화 완료")
    }

    fun addCrayonToPouch(player: Player, itemInternalName: String, amount: Int): Boolean {
        return dataManager.addCrayonToPouch(player, itemInternalName, amount)
    }

    @JvmStatic
    fun getPoints(player: Player): Long {
        return dataManager.getPoints(player)
    }

    @JvmStatic
    fun setPoints(player: Player, points: Long) {
        dataManager.setPoints(player, points)
    }

    @JvmStatic
    fun openPouch(player: Player) {
        inventoryManager.openPouch(player, 0)
    }

    @JvmStatic
    fun consumePoints(player: Player, amount: Long): Boolean {
        val current = dataManager.getPoints(player)
        if (current >= amount) {
            dataManager.setPoints(player, current - amount)
            return true
        }
        return false
    }

    fun shutdown() {
        if (::dataManager.isInitialized) {
            dataManager.saveDisable()
        }
    }
}