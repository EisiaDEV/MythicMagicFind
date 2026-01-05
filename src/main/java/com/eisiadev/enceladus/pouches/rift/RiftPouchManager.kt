package com.eisiadev.enceladus.pouches.rift

import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

object RiftPouchManager {
    
    private lateinit var dataManager: RiftDataManager
    private lateinit var inventoryManager: RiftInventoryManager
    private lateinit var eventHandler: RiftEventHandler

    fun initialize(plugin: JavaPlugin) {
        dataManager = RiftDataManager(plugin)
        inventoryManager = RiftInventoryManager(plugin, dataManager)
        eventHandler = RiftEventHandler(plugin, dataManager, inventoryManager)
        
        plugin.server.pluginManager.registerEvents(eventHandler, plugin)
        
        println("[RiftPouch] 초기화 완료")
    }
    
    fun addRiftToPouch(player: Player, itemInternalName: String, amount: Int): Boolean {
        return dataManager.addRiftToPouch(player, itemInternalName, amount)
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