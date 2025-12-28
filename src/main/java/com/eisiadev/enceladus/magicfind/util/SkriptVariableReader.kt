package com.eisiadev.enceladus.magicfind.util

import ch.njol.skript.variables.Variables
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.ConcurrentHashMap

object SkriptVariableReader {

    private const val DEBUG = false
    private const val CACHE_DURATION = 5000L
    private const val CLEANUP_INTERVAL = 60000L

    private val magicFindCache = ConcurrentHashMap<String, CachedMagicFind>()

    data class CachedMagicFind(
        val value: Double,
        val timestamp: Long
    )

    fun initialize(plugin: JavaPlugin) {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable {
            cleanupCache()
        }, 1200L, 1200L)

        Bukkit.getPluginManager().registerEvents(object : Listener {
            @EventHandler(priority = EventPriority.MONITOR)
            fun onPlayerQuit(event: PlayerQuitEvent) {
                invalidateCache(event.player)
            }
        }, plugin)

        if (DEBUG) println("[SkriptVariableReader] 초기화 완료 (캐시 유지: ${CACHE_DURATION}ms)")
    }

    fun getMagicFind(player: Player, forceRefresh: Boolean = false): Double {
        val uuid = player.uniqueId.toString()
        val now = System.currentTimeMillis()

        if (!forceRefresh) {
            magicFindCache[uuid]?.let { cached ->
                if (now - cached.timestamp < CACHE_DURATION) {
                    if (DEBUG) println("[SkriptVariableReader] 캐시 사용: ${player.name} = ${cached.value}%")
                    return cached.value
                }
            }
        }

        val magicFind = try {
            val variableName = "magic_find.${player.uniqueId}"
            val value = Variables.getVariable(variableName, null, false)

            when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }
        } catch (e: Exception) {
            if (DEBUG) {
                println("[SkriptVariableReader] 변수 조회 오류: ${e.message}")
                e.printStackTrace()
            }
            0.0
        }
        magicFindCache[uuid] = CachedMagicFind(magicFind, now)
        if (DEBUG) println("[SkriptVariableReader] 갱신 완료: ${player.name} = ${magicFind}%")

        return magicFind
    }

    private fun cleanupCache() {
        val now = System.currentTimeMillis()
        val iterator = magicFindCache.iterator()
        var removed = 0

        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.timestamp > CLEANUP_INTERVAL) {
                iterator.remove()
                removed++
            }
        }

        if (DEBUG && removed > 0) {
            println("[SkriptVariableReader] 캐시 정리: ${removed}개 항목 삭제 (현재: ${magicFindCache.size}개)")
        }
    }

    @JvmStatic
    fun invalidateCache(player: Player) {
        val removed = magicFindCache.remove(player.uniqueId.toString())
        if (DEBUG && removed != null) {
            println("[SkriptVariableReader] ${player.name}의 캐시 무효화 (이전 값: ${removed.value}%)")
        }
    }

    @JvmStatic
    fun refreshMagicFind(player: Player): Double {
        return getMagicFind(player, forceRefresh = true)
    }

    @JvmStatic
    fun clearCache() {
        val size = magicFindCache.size
        magicFindCache.clear()
        if (DEBUG) println("[SkriptVariableReader] 전체 캐시 초기화 (${size}개 항목 삭제)")
    }

    @JvmStatic
    fun getCacheSize(): Int = magicFindCache.size

    @JvmStatic
    fun getCachedValue(player: Player): Double? {
        return magicFindCache[player.uniqueId.toString()]?.value
    }
}