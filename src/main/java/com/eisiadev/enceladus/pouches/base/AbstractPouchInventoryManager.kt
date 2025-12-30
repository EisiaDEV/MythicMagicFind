package com.eisiadev.enceladus.pouches.base

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

abstract class AbstractPouchInventoryManager(
    protected val plugin: JavaPlugin,
    protected val dataManager: AbstractPouchDataManager
) {

    data class PouchState(val page: Int, val initialPoints: Long)
    protected val openPouches = mutableMapOf<Player, PouchState>()

    companion object {
        const val PAGE_SIZE = 45
        const val DEBUG = false
    }

    fun getPouchState(player: Player): PouchState? = openPouches[player]

    fun removePouchState(player: Player) {
        openPouches.remove(player)
    }

    fun openPouch(player: Player, page: Int = 0) {
        val points = dataManager.getPoints(player)
        val compressed = dataManager.compressPoints(points)
        val inv = Bukkit.createInventory(null, 54, getInventoryTitle())
        val itemsToDisplay = buildItemsToDisplay(compressed)
        val startIndex = page * PAGE_SIZE
        val endIndex = minOf(startIndex + PAGE_SIZE, itemsToDisplay.size)
        val hasNextPage = true
        val hasPrevPage = page > 0
        var slot = 0

        for (i in startIndex until endIndex) {
            if (slot >= PAGE_SIZE) break
            val (tier, stackSize) = itemsToDisplay[i]
            val item = generateItemForTier(tier, stackSize)
            if (item != null) {
                inv.setItem(slot, item)
                slot++
            }
        }

        setupNavigation(inv, page, compressed, player, hasNextPage, hasPrevPage)
        player.openInventory(inv)
        openPouches[player] = PouchState(page, points)
    }

    fun updateNavigation(player: Player, inv: Inventory) {
        val pouchState = openPouches[player] ?: return
        val currentPage = pouchState.page

        var currentInventoryPoints = 0L
        for (slot in 0 until PAGE_SIZE) {
            val item = inv.getItem(slot) ?: continue
            val tier = getTierFromItem(item)
            if (tier != null) {
                val tierValue = getTierValue(tier)
                currentInventoryPoints += tierValue * item.amount
            }
        }

        val originalPagePoints = calculatePagePoints(currentPage, pouchState.initialPoints)
        val totalPoints = pouchState.initialPoints - originalPagePoints + currentInventoryPoints
        val hasPrevPage = currentPage > 0

        val background = createBackgroundItem()
        for (slot in 45..53) {
            inv.setItem(slot, background.clone())
        }

        if (hasPrevPage) {
            inv.setItem(46, createPrevButton(currentPage))
        }

        inv.setItem(52, createNextButton(currentPage))
        inv.setItem(49, createInfoButton(totalPoints, currentPage, -1))
    }

    fun saveCurrentInventoryState(player: Player, inv: Inventory) {
        val pouchState = openPouches[player] ?: return

        var currentInventoryPoints = 0L
        for (slot in 0 until PAGE_SIZE) {
            val item = inv.getItem(slot) ?: continue
            val tier = getTierFromItem(item)
            if (tier != null) {
                val tierValue = getTierValue(tier)
                currentInventoryPoints += tierValue * item.amount
            }
        }

        val originalPagePoints = calculatePagePoints(pouchState.page, pouchState.initialPoints)
        val newTotalPoints = pouchState.initialPoints - originalPagePoints + currentInventoryPoints

        dataManager.setPoints(player, newTotalPoints)
    }

    fun calculatePagePoints(page: Int, totalPoints: Long): Long {
        val compressed = dataManager.compressPoints(totalPoints)
        val itemsToDisplay = mutableListOf<Pair<Int, Long>>()

        val sortedTiers = compressed.keys.sortedDescending()
        for (tier in sortedTiers) {
            val count = compressed[tier] ?: continue
            val tierValue = getTierValue(tier)
            var remaining = count

            while (remaining > 0) {
                val stackSize = minOf(remaining, 64L)
                itemsToDisplay.add(tier to (tierValue * stackSize))
                remaining -= stackSize
            }
        }

        val startIndex = page * PAGE_SIZE
        val endIndex = minOf(startIndex + PAGE_SIZE, itemsToDisplay.size)

        var pagePoints = 0L
        for (i in startIndex until endIndex) {
            pagePoints += itemsToDisplay[i].second
        }

        return pagePoints
    }

    fun buildItemsToDisplay(compressed: Map<Int, Long>): List<Pair<Int, Int>> {
        val itemsToDisplay = mutableListOf<Pair<Int, Int>>()
        val sortedTiers = compressed.keys.sortedDescending()

        for (tier in sortedTiers) {
            val count = compressed[tier] ?: continue
            var remaining = count

            while (remaining > 0) {
                val stackSize = minOf(remaining, 64L).toInt()
                itemsToDisplay.add(tier to stackSize)
                remaining -= stackSize
            }
        }

        return itemsToDisplay
    }

    private fun setupNavigation(
        inv: Inventory,
        currentPage: Int,
        compressed: Map<Int, Long>,
        player: Player,
        hasNextPage: Boolean,
        hasPrevPage: Boolean
    ) {
        val totalPages = calculateTotalPages()
        val background = createBackgroundItem()

        for (slot in 45..53) {
            inv.setItem(slot, background.clone())
        }

        if (hasPrevPage) {
            inv.setItem(46, createPrevButton(currentPage))
        }

        if (hasNextPage) {
            inv.setItem(52, createNextButton(currentPage))
        }

        inv.setItem(49, createInfoButton(dataManager.getPoints(player), currentPage, totalPages))
    }

    private fun calculateTotalPages(): Long = -1

    private fun createBackgroundItem(): ItemStack {
        val background = ItemStack(Material.BRICK)
        val bgMeta = background.itemMeta
        bgMeta?.setCustomModelData(1)
        bgMeta?.setDisplayName(" ")
        background.itemMeta = bgMeta
        return background
    }

    private fun createPrevButton(currentPage: Int): ItemStack {
        val prevButton = ItemStack(Material.ARROW)
        val meta = prevButton.itemMeta
        meta?.setCustomModelData(2)
        meta?.setDisplayName("${ChatColor.GRAY}현재 페이지 ${currentPage + 1}")
        prevButton.itemMeta = meta
        return prevButton
    }

    private fun createNextButton(currentPage: Int): ItemStack {
        val nextButton = ItemStack(Material.ARROW)
        val meta = nextButton.itemMeta
        meta?.setCustomModelData(3)
        meta?.setDisplayName("${ChatColor.GRAY}현재 페이지 ${currentPage + 1}")
        nextButton.itemMeta = meta
        return nextButton
    }

    private fun createInfoButton(points: Long, currentPage: Int, totalPages: Long): ItemStack {
        val infoButton = ItemStack(Material.EMERALD)
        val meta = infoButton.itemMeta
        meta?.setDisplayName("${ChatColor.GRAY}${getPouchDisplayName()}")
        val pageInfo = if (totalPages > 0) "${currentPage + 1} / $totalPages" else "${currentPage + 1}"

        meta?.lore = listOf(
            "${ChatColor.AQUA}총 ${getItemUnitName()} 수: ${ChatColor.WHITE}${formatNumber(points)}",
            "${ChatColor.AQUA}현재 페이지: ${ChatColor.WHITE}$pageInfo"
        )
        infoButton.itemMeta = meta
        return infoButton
    }

    protected fun formatNumber(number: Long): String {
        return String.format("%,d", number)
    }

    abstract fun getInventoryTitle(): String
    abstract fun getPouchDisplayName(): String
    abstract fun getItemUnitName(): String
    abstract fun generateItemForTier(tier: Int, count: Int): ItemStack?
    abstract fun getTierFromItem(item: ItemStack): Int?
    abstract fun getTierValue(tier: Int): Long
}