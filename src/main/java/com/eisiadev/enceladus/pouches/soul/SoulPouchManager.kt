package com.eisiadev.enceladus.pouches.soul

import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

object SoulPouchManager {
    
    private lateinit var dataManager: SoulDataManager
    private lateinit var inventoryManager: SoulInventoryManager
    private lateinit var eventHandler: SoulEventHandler
    
    fun initialize(plugin: JavaPlugin) {
        dataManager = SoulDataManager(plugin)
        inventoryManager = SoulInventoryManager(plugin, dataManager)
        eventHandler = SoulEventHandler(plugin, dataManager, inventoryManager)
        
        plugin.server.pluginManager.registerEvents(eventHandler, plugin)
        
        println("[SoulPouch] 초기화 완료")
    }

    @JvmStatic
    fun addSoulToPouch(player: Player, itemInternalName: String, amount: Int): Boolean {
        return dataManager.addSoulToPouch(player, itemInternalName, amount)
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