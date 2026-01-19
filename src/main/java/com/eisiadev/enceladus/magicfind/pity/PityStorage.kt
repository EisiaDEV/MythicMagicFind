package com.eisiadev.enceladus.magicfind.pity

import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class PityStorage(
    private val plugin: JavaPlugin,
    private val debug: Boolean = false
) {

    private val pityDataFolder = File(plugin.dataFolder, "pity")
    private val dirtyFlags = ConcurrentHashMap.newKeySet<String>() // UUID set
    private var memoryCache: ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>? = null

    private var saveTaskId: Int = -1

    fun initialize() {
        if (!pityDataFolder.exists()) {
            pityDataFolder.mkdirs()
        }

        saveTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable {
            saveDirtyDataBatch()
        }, 200L, 200L).taskId

        if (debug) println("[PityStorage] 자동 저장 태스크 시작 (10초 간격)")
    }

    fun shutdown() {
        if (saveTaskId != -1) {
            Bukkit.getScheduler().cancelTask(saveTaskId)
        }
    }

    fun setMemoryCache(cache: ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>) {
        this.memoryCache = cache
    }

    fun markDirty(uuid: String) {
        dirtyFlags.add(uuid)
    }

    private fun saveDirtyDataBatch() {
        val cache = memoryCache ?: return
        if (dirtyFlags.isEmpty()) return

        val toSave = dirtyFlags.toList()
        dirtyFlags.removeAll(toSave.toSet())

        if (debug && toSave.isNotEmpty()) {
            println("[PityStorage] ${toSave.size}명의 플레이어 데이터 저장 중...")
        }

        toSave.forEach { uuid ->
            val playerData = cache[uuid]
            if (playerData != null) {
                savePlayerDataSync(uuid, playerData)
            }
        }
    }

    fun saveAll(memoryCache: ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>) {
        memoryCache.forEach { (uuid, data) ->
            savePlayerDataSync(uuid, data)
        }
        if (debug) println("[PityStorage] 전체 데이터 저장 완료")
    }

    private fun savePlayerDataSync(uuid: String, data: ConcurrentHashMap<String, Int>) {
        try {
            val file = File(pityDataFolder, "$uuid.yml")
            val config = YamlConfiguration()

            data.forEach { (itemName, count) ->
                config.set("$uuid:$itemName:count", count)
            }

            config.save(file)

            if (debug) {
                println("[PityStorage] $uuid 저장 완료 (${data.size}개 항목)")
            }
        } catch (e: Exception) {
            println("[PityStorage] $uuid 저장 실패: ${e.message}")
            e.printStackTrace()
        }
    }

    fun loadPlayerData(uuid: String): Map<String, Int> {
        val file = File(pityDataFolder, "$uuid.yml")
        if (!file.exists()) return emptyMap()

        try {
            val config = YamlConfiguration.loadConfiguration(file)
            val result = mutableMapOf<String, Int>()

            config.getKeys(false).forEach { key ->
                // 형식: "uuid:itemName:count"
                val parts = key.split(":")
                if (parts.size == 3 && parts[0] == uuid && parts[2] == "count") {
                    val itemName = parts[1]
                    val count = config.getInt(key, 0)
                    if (count > 0) {
                        result[itemName] = count
                    }
                }
            }

            if (debug && result.isNotEmpty()) {
                println("[PityStorage] $uuid 로드 완료 (${result.size}개 항목)")
            }

            return result
        } catch (e: Exception) {
            println("[PityStorage] $uuid 로드 실패: ${e.message}")
            e.printStackTrace()
            return emptyMap()
        }
    }
}