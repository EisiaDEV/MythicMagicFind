package com.eisiadev.enceladus.magicfind.listener

import com.eisiadev.enceladus.magicfind.util.MagicFindCalculator
import com.eisiadev.enceladus.magicfind.util.SkriptVariableReader
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

class MythicMobDeathListener : Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onMythicMobDeath(event: MythicMobDeathEvent) {
        val killer = event.killer

        // Check if killer is a player
        if (killer !is Player) return

        // Get magic find value from Skript variable
        val magicFind = SkriptVariableReader.getMagicFind(killer)

        // If magic find is 0 or less, no modification needed
        if (magicFind <= 0.0) return

        // Calculate and modify drops
        try {
            MagicFindCalculator.modifyDrops(event, killer, magicFind)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}