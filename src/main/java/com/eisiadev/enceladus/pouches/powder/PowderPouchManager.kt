package com.eisiadev.enceladus.pouches.powder

import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

object PowderPouchManager {
    
    private lateinit var dataManager: PowderDataManager
    private lateinit var inventoryManager: PowderInventoryManager
    private lateinit var eventHandler: PowderEventHandler

    fun initialize(plugin: JavaPlugin) {
        dataManager = PowderDataManager(plugin)
        inventoryManager = PowderInventoryManager(plugin, dataManager)
        eventHandler = PowderEventHandler(plugin, dataManager, inventoryManager)
        
        plugin.server.pluginManager.registerEvents(eventHandler, plugin)
        
        println("[PowderPouch] 초기화 완료")
    }
    
    fun addPowderToPouch(player: Player, itemInternalName: String, amount: Int): Boolean {
        return dataManager.addPowderToPouch(player, itemInternalName, amount)
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