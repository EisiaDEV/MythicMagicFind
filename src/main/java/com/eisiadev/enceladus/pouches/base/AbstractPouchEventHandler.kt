package com.eisiadev.enceladus.pouches.base

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

abstract class AbstractPouchEventHandler(
    protected val plugin: JavaPlugin,
    protected val dataManager: AbstractPouchDataManager,
    protected val inventoryManager: AbstractPouchInventoryManager
) : Listener {

    companion object {
        const val DEBUG = false
        const val PAGE_SIZE = 45
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item ?: return

        if (!isPouchItem(item)) return

        event.isCancelled = true
        inventoryManager.openPouch(player, 0)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return

        if (inventoryManager.getPouchState(player) == null) return

        val topInv = event.view.topInventory

        // 네비게이션 업데이트는 항상 스케줄러로 처리
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (player.openInventory.topInventory == topInv) {
                inventoryManager.updateNavigation(player, topInv)
            }
        })
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return

        if (inventoryManager.getPouchState(player) == null) return

        val topInv = event.view.topInventory

        // 드래그가 파우치 인벤토리 영역에 영향을 주는지 확인
        val affectsPouchInventory = event.rawSlots.any { it < topInv.size }

        if (affectsPouchInventory) {
            // 네비게이션 업데이트
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (player.openInventory.topInventory == topInv) {
                    inventoryManager.updateNavigation(player, topInv)
                }
            })
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onInventoryClickNavigation(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return

        val pouchState = inventoryManager.getPouchState(player) ?: return

        val clickedInv = event.clickedInventory ?: return
        val topInv = event.view.topInventory

        val slot = event.slot

        // 네비게이션 버튼 클릭 처리 (슬롯 45~53)
        if (clickedInv == topInv && slot >= PAGE_SIZE) {
            event.isCancelled = true

            val currentPage = pouchState.page

            when (slot) {
                46 -> handlePrevPage(player, currentPage, topInv)
                52 -> handleNextPage(player, currentPage, topInv)
                49 -> {} // Info button
            }
            return
        }

        // 파우치 인벤토리 영역 (슬롯 0~44)에서 Shift+클릭 처리
        if (clickedInv == topInv && slot < PAGE_SIZE && event.isShiftClick) {
            handleShiftClick(event, player)
            return
        }

        // 일반 클릭 시 사운드 재생
        if (clickedInv == topInv && slot < PAGE_SIZE) {
            val clickedItem = event.currentItem
            if (clickedItem != null && clickedItem.type != Material.AIR) {
                player.playSound(player.location, "ui.button.click_1", 0.5f, 1.2f)
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return

        val pouchState = inventoryManager.getPouchState(player) ?: return
        inventoryManager.removePouchState(player)

        val inv = event.inventory
        if (ChatColor.stripColor(event.view.title) != getInventoryTitle()) return

        val currentPagePoints = inventoryManager.calculatePagePoints(pouchState.page, pouchState.initialPoints)

        var remainingPagePoints = 0L
        var invalidItemCount = 0

        for (slot in 0 until PAGE_SIZE) {
            val item = inv.getItem(slot) ?: continue

            val tier = getTierFromItem(item)

            if (tier != null) {
                val amount = item.amount
                val tierValue = getTierValue(tier)
                remainingPagePoints += tierValue * amount

                if (DEBUG) {
                    println("[${dataManager.getPouchName()}] 스캔: 티어$tier x${amount} = ${tierValue * amount} 포인트")
                }
            } else {
                returnItemToPlayer(player, item)
                invalidItemCount++

                if (DEBUG) {
                    println("[${dataManager.getPouchName()}] 잘못된 아이템 발견: ${item.type}")
                }
            }
        }

        val pointsDifference = currentPagePoints - remainingPagePoints
        val newTotalPoints = pouchState.initialPoints - pointsDifference

        dataManager.setPoints(player, newTotalPoints)

        if (invalidItemCount > 0) {
            player.sendMessage("${ChatColor.YELLOW}${getPouchDisplayName()}에 속하지 않는 아이템 ${invalidItemCount}개가 인벤토리로 반환되었습니다.")
        }

        if (DEBUG) {
            println("[${dataManager.getPouchName()}] ${player.name} 파우치 닫기 완료. 최종 포인트: $newTotalPoints")
        }
    }

    private fun handlePrevPage(player: Player, currentPage: Int, topInv: org.bukkit.inventory.Inventory) {
        if (currentPage > 0) {
            player.playSound(player.location, "ui.button.click_1", 1.0f, 1.0f)
            inventoryManager.saveCurrentInventoryState(player, topInv)
            inventoryManager.openPouch(player, currentPage - 1)
        } else {
            player.sendMessage("${ChatColor.RED}이전 페이지가 없습니다!")
        }
    }

    private fun handleNextPage(player: Player, currentPage: Int, topInv: org.bukkit.inventory.Inventory) {
        // 현재 페이지 상태 저장
        inventoryManager.saveCurrentInventoryState(player, topInv)

        // 다음 페이지로 무조건 이동 (빈 페이지 허용)
        player.playSound(player.location, "ui.button.click_1", 1.0f, 1.0f)
        inventoryManager.openPouch(player, currentPage + 1)
    }

    private fun handleShiftClick(event: InventoryClickEvent, player: Player) {
        event.isCancelled = true

        val clickedItem = event.currentItem
        if (clickedItem == null || clickedItem.type == Material.AIR) return

        // 플레이어 인벤토리로 아이템 추가 시도
        val remaining = player.inventory.addItem(clickedItem.clone())

        if (remaining.isEmpty()) {
            // 모든 아이템이 성공적으로 이동됨
            event.currentItem = null
            player.playSound(player.location, "ui.button.click_1", 0.5f, 1.2f)
        } else {
            // 일부 또는 전체 아이템이 이동 실패 (인벤토리 가득 찬 경우)
            // 이동 가능한 만큼만 처리
            val movedAmount = clickedItem.amount - (remaining.values.firstOrNull()?.amount ?: 0)

            if (movedAmount > 0) {
                clickedItem.amount -= movedAmount
                event.currentItem = clickedItem
                player.playSound(player.location, "ui.button.click_1", 0.5f, 1.2f)
            }
        }
    }

    private fun returnItemToPlayer(player: Player, item: ItemStack) {
        val returned = player.inventory.addItem(item)
        if (returned.isNotEmpty()) {
            returned.values.forEach { leftover ->
                player.world.dropItemNaturally(player.location, leftover)
            }
        }
    }

    abstract fun isPouchItem(item: ItemStack): Boolean
    abstract fun getTierFromItem(item: ItemStack): Int?
    abstract fun getTierValue(tier: Int): Long
    abstract fun getInventoryTitle(): String
    abstract fun getPouchDisplayName(): String
}