package com.eisiadev.enceladus.mythicalpowderpouch

import io.lumine.mythic.bukkit.MythicBukkit
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import kotlin.math.pow

object MythicalPowderPouch : Listener {

    private const val DEBUG = false
    private lateinit var pluginInstance: JavaPlugin
    private lateinit var dataFile: File
    private lateinit var dataConfig: FileConfiguration

    private const val POUCH_DISPLAY_NAME = "신비한 가루 파우치"
    private const val INVENTORY_TITLE = "신비한 가루 파우치"
    private const val PAGE_SIZE = 45

    private val TIER_VALUES = (1..15).associateWith { tier ->
        10.0.pow(tier - 1).toLong()
    }

    private val openPouches = mutableMapOf<Player, Int>()

    fun initialize(plugin: JavaPlugin) {
        pluginInstance = plugin

        dataFile = File(plugin.dataFolder, "powder_pouch_data.yml")
        if (!dataFile.exists()) {
            plugin.dataFolder.mkdirs()
            dataFile.createNewFile()
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile)

        plugin.server.pluginManager.registerEvents(this, plugin)
        if (DEBUG) println("[PowderPouch] 초기화 완료")
    }

    private fun saveData() {
        try {
            dataConfig.save(dataFile)
        } catch (e: Exception) {
            println("[PowderPouch] 데이터 저장 실패:")
            e.printStackTrace()
        }
    }

    fun hasPouch(player: Player): Boolean {
        return player.inventory.contents.any { item ->
            item != null && isPouchItem(item)
        }
    }

    private fun isPouchItem(item: ItemStack): Boolean {
        if (!item.hasItemMeta()) return false
        val meta = item.itemMeta ?: return false
        if (!meta.hasDisplayName()) return false
        return ChatColor.stripColor(meta.displayName) == POUCH_DISPLAY_NAME
    }

    fun addPowderToPouch(player: Player, itemInternalName: String, amount: Int): Boolean {
        val tierMatch = Regex("신비한가루_(\\d+)").find(itemInternalName) ?: return false
        val tier = tierMatch.groupValues[1].toIntOrNull() ?: return false

        if (tier !in 1..15) return false

        val pointsPerItem = TIER_VALUES[tier] ?: return false
        val totalPoints = pointsPerItem * amount

        addPoints(player, totalPoints)

        if (DEBUG) {
            println("[PowderPouch] ${player.name}에게 $itemInternalName x${amount} 추가 (${totalPoints} 포인트)")
        }

        return true
    }

    private fun getPoints(player: Player): Long {
        val uuid = player.uniqueId.toString()
        return dataConfig.getLong("players.${uuid}", 0L)
    }

    private fun setPoints(player: Player, points: Long) {
        val uuid = player.uniqueId.toString()
        dataConfig.set("players.${uuid}", points)
        saveData()

        if (DEBUG) {
            println("[PowderPouch] ${player.name}의 포인트 설정: $points")
        }
    }

    private fun addPoints(player: Player, points: Long) {
        val current = getPoints(player)
        setPoints(player, current + points)
    }

    private fun compressPoints(points: Long): Map<Int, Long> {
        var remaining = points
        val result = mutableMapOf<Int, Long>()

        for (tier in 15 downTo 1) {
            val tierValue = TIER_VALUES[tier] ?: continue
            if (remaining >= tierValue) {
                val count = remaining / tierValue
                result[tier] = count
                remaining %= tierValue
            }
        }

        return result
    }

    fun openPouch(player: Player, page: Int = 0) {
        val points = getPoints(player)
        val compressed = compressPoints(points)

        val inv = Bukkit.createInventory(null, 54, INVENTORY_TITLE)

        var slot = 0
        val sortedTiers = compressed.keys.sortedDescending()

        val itemsToDisplay = mutableListOf<Pair<Int, Int>>()
        for (tier in sortedTiers) {
            val count = compressed[tier] ?: continue
            var remaining = count

            while (remaining > 0) {
                val stackSize = minOf(remaining, 64L).toInt()
                itemsToDisplay.add(tier to stackSize)
                remaining -= stackSize
            }
        }

        val startIndex = page * PAGE_SIZE
        val endIndex = minOf(startIndex + PAGE_SIZE, itemsToDisplay.size)
        val hasNextPage = endIndex < itemsToDisplay.size

        for (i in startIndex until endIndex) {
            if (slot >= PAGE_SIZE) break

            val (tier, stackSize) = itemsToDisplay[i]
            val item = generatePowderItem(tier, stackSize)

            if (item != null) {
                inv.setItem(slot, item)
                slot++
            }
        }

        setupNavigation(inv, page, compressed, player, hasNextPage)

        player.openInventory(inv)
        openPouches[player] = page

        if (DEBUG) {
            println("[PowderPouch] ${player.name}이(가) 파우치 열기 (페이지: $page, 총 포인트: $points, 다음 페이지: $hasNextPage)")
        }
    }

    private fun setupNavigation(inv: Inventory, currentPage: Int, compressed: Map<Int, Long>, player: Player, hasNextPage: Boolean) {
        val totalPages = calculateTotalPages(compressed)

        val background = ItemStack(Material.BRICK)
        val bgMeta = background.itemMeta
        bgMeta?.setCustomModelData(1)
        bgMeta?.setDisplayName(" ")
        background.itemMeta = bgMeta

        for (slot in 45..53) {
            inv.setItem(slot, background.clone())
        }

        if (currentPage > 0) {
            val prevButton = ItemStack(Material.ARROW)
            val meta = prevButton.itemMeta
            meta?.setCustomModelData(2)
            meta?.setDisplayName("${ChatColor.GRAY}현재 페이지 ${currentPage + 1}")
            prevButton.itemMeta = meta
            inv.setItem(46, prevButton)
        }

        if (hasNextPage) {
            val nextButton = ItemStack(Material.ARROW)
            val meta = nextButton.itemMeta
            meta?.setCustomModelData(3)
            meta?.setDisplayName("${ChatColor.GRAY}현재 페이지 ${currentPage + 1}")
            nextButton.itemMeta = meta
            inv.setItem(52, nextButton)
        }

        val infoButton = ItemStack(Material.EMERALD)
        val meta = infoButton.itemMeta
        meta?.setDisplayName("${ChatColor.GRAY}신비한 가루 파우치")
        meta?.lore = listOf(
            "${ChatColor.AQUA}총 파우더 수(1티어 기준): ${ChatColor.WHITE}${formatNumber(getPoints(player))}",
            "${ChatColor.AQUA}페이지: ${ChatColor.WHITE}${currentPage + 1} / $totalPages"
        )
        infoButton.itemMeta = meta
        inv.setItem(49, infoButton)
    }

    private fun calculateTotalPages(compressed: Map<Int, Long>): Long {
        val totalStacks = compressed.values.sumOf { count ->
            (count + 63) / 64
        }
        return ((totalStacks + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    }

    private fun formatNumber(number: Long): String {
        return String.format("%,d", number)
    }

    private fun generatePowderItem(tier: Int, count: Int): ItemStack? {
        try {
            val itemName = "신비한가루_${tier}"
            val itemOpt = MythicBukkit.inst().itemManager.getItem(itemName)

            if (itemOpt.isPresent) {
                val mythicItem = itemOpt.get()
                val itemStack = mythicItem.generateItemStack(count)

                return when {
                    itemStack is ItemStack -> itemStack
                    itemStack.javaClass.simpleName == "BukkitItemStack" -> {
                        itemStack.javaClass.getMethod("build").invoke(itemStack) as? ItemStack
                    }
                    else -> null
                }
            }
        } catch (e: Exception) {
            if (DEBUG) {
                println("[PowderPouch] 아이템 생성 실패: 신비한가루_${tier}")
                e.printStackTrace()
            }
        }
        return null
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item ?: return

        if (!isPouchItem(item)) return

        event.isCancelled = true
        openPouch(player, 0)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return

        if (!openPouches.containsKey(player)) return

        val clickedInv = event.clickedInventory ?: return
        val topInv = event.view.topInventory

        if (clickedInv != topInv) return

        val slot = event.slot

        if (slot >= PAGE_SIZE) {
            event.isCancelled = true

            val currentPage = openPouches[player] ?: 0

            when (slot) {
                46 -> {
                    if (currentPage > 0) {
                        player.playSound(player.location, "ui.button.click_1", 1.0f, 1.0f)
                        openPouch(player, currentPage - 1)
                    } else {
                        player.sendMessage("${ChatColor.RED}이전 페이지가 없습니다!")
                    }
                }
                52 -> {
                    val points = getPoints(player)
                    val compressed = compressPoints(points)
                    val itemsToDisplay = mutableListOf<Pair<Int, Int>>()

                    for (tier in compressed.keys.sortedDescending()) {
                        val count = compressed[tier] ?: continue
                        var remaining = count
                        while (remaining > 0) {
                            val stackSize = minOf(remaining, 64L).toInt()
                            itemsToDisplay.add(tier to stackSize)
                            remaining -= stackSize
                        }
                    }

                    val nextPageStartIndex = (currentPage + 1) * PAGE_SIZE
                    if (nextPageStartIndex < itemsToDisplay.size) {
                        player.playSound(player.location, "ui.button.click_1", 1.0f, 1.0f)
                        openPouch(player, currentPage + 1)
                    } else {
                        player.sendMessage("${ChatColor.RED}다음 페이지가 없습니다!")
                    }
                }
                49 -> {
                }
            }
            return
        }

        if (event.isShiftClick) {
            event.isCancelled = true

            val clickedItem = event.currentItem
            if (clickedItem != null && clickedItem.type != Material.AIR) {
                val remaining = player.inventory.addItem(clickedItem.clone())
                if (remaining.isEmpty()) {
                    event.currentItem = null
                    player.playSound(player.location, "ui.button.click_1", 0.5f, 1.2f)
                }
            }
        } else {
            val clickedItem = event.currentItem
            if (clickedItem != null && clickedItem.type != Material.AIR) {
                player.playSound(player.location, "ui.button.click_1", 0.5f, 1.2f)
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return

        if (!openPouches.containsKey(player)) return
        openPouches.remove(player)

        val inv = event.inventory

        if (ChatColor.stripColor(event.view.title) != INVENTORY_TITLE) return

        var totalPoints = 0L
        var invalidItemCount = 0

        for (slot in 0 until PAGE_SIZE) {
            val item = inv.getItem(slot) ?: continue

            val tier = getPowderTierFromItem(item)

            if (tier != null) {
                val amount = item.amount
                val tierValue = TIER_VALUES[tier] ?: continue
                totalPoints += tierValue * amount

                if (DEBUG) {
                    println("[PowderPouch] 스캔: 신비한가루_${tier} x${amount} = ${tierValue * amount} 포인트")
                }
            } else {
                val returned = player.inventory.addItem(item)
                if (returned.isNotEmpty()) {
                    returned.values.forEach { leftover ->
                        player.world.dropItemNaturally(player.location, leftover)
                    }
                }
                invalidItemCount++

                if (DEBUG) {
                    println("[PowderPouch] 잘못된 아이템 발견: ${item.type} - 플레이어에게 반환")
                }
            }
        }

        setPoints(player, totalPoints)

        if (invalidItemCount > 0) {
            player.sendMessage("${ChatColor.YELLOW}파우더가 아닌 아이템 ${invalidItemCount}개가 인벤토리로 반환되었습니다.")
        }

        if (DEBUG) {
            println("[PowderPouch] ${player.name} 파우치 닫기 완료. 총 포인트: ${totalPoints}, 잘못된 아이템: $invalidItemCount")
        }
    }

    private fun getPowderTierFromItem(item: ItemStack): Int? {
        try {
            val mythicItem = MythicBukkit.inst().itemManager.getMythicTypeFromItem(item)

            if (mythicItem != null) {
                val internalName = mythicItem

                val tierMatch = Regex("신비한가루_(\\d+)").find(internalName) ?: return null
                val tier = tierMatch.groupValues[1].toIntOrNull() ?: return null

                if (tier in 1..15) {
                    return tier
                }
            }
        } catch (e: Exception) {
            if (DEBUG) {
                println("[PowderPouch] 티어 추출 실패:")
                e.printStackTrace()
            }
        }
        return null
    }

    @JvmStatic
    fun openPouchStatic(player: Player) {
        openPouch(player, 0)
    }
}