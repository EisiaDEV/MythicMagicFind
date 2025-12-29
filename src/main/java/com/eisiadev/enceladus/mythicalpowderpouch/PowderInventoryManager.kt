package com.eisiadev.enceladus.mythicalpowderpouch

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

class PowderInventoryManager(
    private val dataManager: PowderDataManager
) {
    
    companion object {
        private const val DEBUG = false
        private const val INVENTORY_TITLE = "신비한 가루 파우치"
        private const val PAGE_SIZE = 45
    }
    
    data class PouchState(val page: Int, val initialPoints: Long)
    private val openPouches = mutableMapOf<Player, PouchState>()
    
    fun openPouch(player: Player, page: Int = 0) {
        val points = dataManager.getPoints(player)
        val compressed = dataManager.compressPoints(points)
        
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
        val hasPrevPage = page > 0
        
        for (i in startIndex until endIndex) {
            if (slot >= PAGE_SIZE) break
            
            val (tier, stackSize) = itemsToDisplay[i]
            val item = PowderItemHelper.generatePowderItem(tier, stackSize)
            
            if (item != null) {
                inv.setItem(slot, item)
                slot++
            }
        }
        
        setupNavigation(inv, page, compressed, player, hasNextPage, hasPrevPage)
        
        player.openInventory(inv)
        openPouches[player] = PouchState(page, points)
        
        if (DEBUG) {
            println("[PowderPouch] ${player.name}이(가) 파우치 열기 (페이지: $page, 총 포인트: $points)")
        }
    }
    
    fun getPouchState(player: Player): PouchState? = openPouches[player]
    
    fun removePouchState(player: Player) {
        openPouches.remove(player)
    }
    
    fun updateNavigation(player: Player, inv: Inventory) {
        val pouchState = openPouches[player] ?: return
        val currentPage = pouchState.page
        
        var currentInventoryPoints = 0L
        for (slot in 0 until PAGE_SIZE) {
            val item = inv.getItem(slot) ?: continue
            val tier = PowderItemHelper.getPowderTierFromItem(item)
            if (tier != null) {
                val tierValue = PowderDataManager.TIER_VALUES[tier] ?: continue
                currentInventoryPoints += tierValue * item.amount
            }
        }
        
        val originalPagePoints = calculatePagePoints(currentPage, pouchState.initialPoints)
        val totalPoints = pouchState.initialPoints - originalPagePoints + currentInventoryPoints
        
        val compressed = dataManager.compressPoints(totalPoints)
        val totalItemStacks = mutableListOf<Pair<Int, Int>>()
        
        for (tier in compressed.keys.sortedDescending()) {
            val count = compressed[tier] ?: continue
            var remaining = count
            while (remaining > 0) {
                val stackSize = minOf(remaining, 64L).toInt()
                totalItemStacks.add(tier to stackSize)
                remaining -= stackSize
            }
        }
        
        val hasNextPage = (currentPage + 1) * PAGE_SIZE < totalItemStacks.size
        val hasPrevPage = currentPage > 0
        
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
        
        val totalPages = calculateTotalPages(compressed)
        inv.setItem(49, createInfoButton(totalPoints, currentPage, totalPages))
        
        if (DEBUG) {
            println("[PowderPouch] 네비게이션 업데이트: 페이지=$currentPage, 총 포인트=$totalPoints")
        }
    }
    
    fun saveCurrentInventoryState(player: Player, inv: Inventory) {
        val pouchState = openPouches[player] ?: return
        
        var currentInventoryPoints = 0L
        for (slot in 0 until PAGE_SIZE) {
            val item = inv.getItem(slot) ?: continue
            val tier = PowderItemHelper.getPowderTierFromItem(item)
            if (tier != null) {
                val tierValue = PowderDataManager.TIER_VALUES[tier] ?: continue
                currentInventoryPoints += tierValue * item.amount
            }
        }
        
        val originalPagePoints = calculatePagePoints(pouchState.page, pouchState.initialPoints)
        val newTotalPoints = pouchState.initialPoints - originalPagePoints + currentInventoryPoints
        
        dataManager.setPoints(player, newTotalPoints)
        
        if (DEBUG) {
            println("[PowderPouch] 인벤토리 상태 저장: 새 총합=$newTotalPoints")
        }
    }
    
    fun calculatePagePoints(page: Int, totalPoints: Long): Long {
        val compressed = dataManager.compressPoints(totalPoints)
        val itemsToDisplay = mutableListOf<Pair<Int, Long>>()
        
        for (tier in compressed.keys.sortedDescending()) {
            val count = compressed[tier] ?: continue
            val tierValue = PowderDataManager.TIER_VALUES[tier] ?: continue
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
    
    private fun setupNavigation(
        inv: Inventory,
        currentPage: Int,
        compressed: Map<Int, Long>,
        player: Player,
        hasNextPage: Boolean,
        hasPrevPage: Boolean
    ) {
        val totalPages = calculateTotalPages(compressed)
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
    
    private fun calculateTotalPages(compressed: Map<Int, Long>): Long {
        val totalStacks = compressed.values.sumOf { count ->
            (count + 63) / 64
        }
        return ((totalStacks + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    }
    
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
        meta?.setDisplayName("${ChatColor.GRAY}신비한 가루 파우치")
        meta?.lore = listOf(
            "${ChatColor.AQUA}총 파우더 수(1티어 기준): ${ChatColor.WHITE}${PowderItemHelper.formatNumber(points)}",
            "${ChatColor.AQUA}페이지: ${ChatColor.WHITE}${currentPage + 1} / $totalPages"
        )
        infoButton.itemMeta = meta
        return infoButton
    }
}