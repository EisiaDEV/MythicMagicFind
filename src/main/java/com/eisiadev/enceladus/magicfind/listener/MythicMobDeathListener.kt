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
        if (killer !is Player) return
        val magicFind = SkriptVariableReader.getMagicFind(killer)
        if (magicFind <= 0.0) return
        try {
            MagicFindCalculator.modifyDrops(event, killer, magicFind)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}