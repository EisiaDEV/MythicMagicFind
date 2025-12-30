package com.eisiadev.enceladus.pouches.base

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import org.bukkit.scheduler.BukkitTask

abstract class AbstractPouchDataManager(
    protected val plugin: JavaPlugin,
    dataFileName: String
) {
    protected val dataFile: File = File(plugin.dataFolder, dataFileName)
    protected val dataConfig: FileConfiguration

    private val pointsCache = ConcurrentHashMap<UUID, Long>()
    private val dirty = AtomicBoolean(false)

    private var autoSaveTask: BukkitTask? = null
    private val isShutdown = AtomicBoolean(false)  // 추가

    init {
        if (!dataFile.exists()) {
            plugin.dataFolder.mkdirs()
            dataFile.createNewFile()
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile)

        loadToCache()
        startAutoSaveTask()
    }

    private fun loadToCache() {
        val section = dataConfig.getConfigurationSection("players") ?: return
        for (key in section.getKeys(false)) {
            try {
                val uuid = UUID.fromString(key)
                val points = section.getLong(key)
                pointsCache[uuid] = points
            } catch (e: IllegalArgumentException) {
                plugin.logger.warning("[${getPouchName()}] 잘못된 UUID 형식: $key")
            }
        }
    }

    fun getPoints(player: Player): Long {
        return pointsCache.getOrDefault(player.uniqueId, 0L)
    }

    fun setPoints(player: Player, points: Long) {
        if (isShutdown.get()) return  // 종료 중이면 무시
        pointsCache[player.uniqueId] = points
        dirty.set(true)
    }

    fun addPoints(player: Player, points: Long) {
        val current = getPoints(player)
        setPoints(player, current + points)
    }

    private fun startAutoSaveTask() {
        autoSaveTask?.cancel()

        autoSaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable {
            if (dirty.get() && !isShutdown.get()) {
                saveToDisk()
            }
        }, 100L, 100L)
    }

    fun saveToDisk() {
        if (!dirty.get() || isShutdown.get()) return

        val snapshot = HashMap(pointsCache)
        dirty.set(false)

        try {
            synchronized(dataConfig) {
                dataConfig.set("players", null)
                snapshot.forEach { (uuid, points) ->
                    dataConfig.set("players.$uuid", points)
                }
                dataConfig.save(dataFile)
            }
        } catch (e: Exception) {
            dirty.set(true)
            plugin.logger.severe("[${getPouchName()}] 데이터 저장 중 오류 발생: ${e.message}")
            e.printStackTrace()
        }
    }

    fun saveDisable() {
        if (isShutdown.getAndSet(true)) {
            return
        }

        autoSaveTask?.cancel()
        autoSaveTask = null

        if (dirty.get()) {
            saveToDisk()
        }

        plugin.logger.info("[${getPouchName()}] 데이터 저장 완료")
    }

    abstract fun getPouchName(): String
    abstract fun getTierConfig(): TierConfig
    abstract fun compressPoints(points: Long): Map<Int, Long>
}