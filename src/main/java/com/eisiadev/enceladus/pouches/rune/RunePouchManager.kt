package com.eisiadev.enceladus.pouches.rune

import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

object RunePouchManager {
    
    private lateinit var dataManager: RuneDataManager
    private lateinit var inventoryManager: RuneInventoryManager
    private lateinit var eventHandler: RuneEventHandler
    
    fun initialize(plugin: JavaPlugin) {
        dataManager = RuneDataManager(plugin)
        inventoryManager = RuneInventoryManager(plugin, dataManager)
        eventHandler = RuneEventHandler(plugin, dataManager, inventoryManager)
        
        plugin.server.pluginManager.registerEvents(eventHandler, plugin)
        
        println("[RunePouch] 초기화 완료")
    }

    @JvmStatic
    fun addRuneToPouch(player: Player, itemInternalName: String, amount: Int): Boolean {
        return dataManager.addRuneToPouch(player, itemInternalName, amount)
    }

    @JvmStatic
    fun openPouch(player: Player) {
        inventoryManager.openPouch(player, 0)
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