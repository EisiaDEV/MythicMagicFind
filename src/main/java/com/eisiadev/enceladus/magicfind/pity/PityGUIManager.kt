package com.eisiadev.enceladus.magicfind.pity

import com.eisiadev.enceladus.magicfind.MythicMagicFind
import com.eisiadev.enceladus.magicfind.util.ReflectionCache
import com.eisiadev.enceladus.magicfind.util.SkriptVariableReader
import io.lumine.mythic.bukkit.MythicBukkit
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

object PityGUIManager : Listener {

    private const val GUI_TITLE = "천장 시스템"
    private const val DEBUG = false
    private const val ITEMS_PER_PAGE = 45

    private var configGetStringListMethod: Method? = null

    private val playerPages = ConcurrentHashMap<String, Int>()

    @JvmStatic
    fun openGUI(player: Player, page: Int = 0) {
        val pitySystem = MythicMagicFind.instance.pitySystem
        val magicFind = SkriptVariableReader.getMagicFind(player)
        pitySystem.loadPlayerData(player)

        playerPages[player.uniqueId.toString()] = page
        val inv = createPityInventory(player, pitySystem, magicFind, page)
        player.openInventory(inv)
    }

    private fun createPityInventory(
        player: Player,
        pitySystem: PitySystem,
        magicFind: Double,
        page: Int
    ): Inventory {
        val inv = Bukkit.createInventory(
            null,
            54,
            "${ChatColor.LIGHT_PURPLE}$GUI_TITLE ${ChatColor.GRAY}(${page + 1}페이지)"
        )

        val accessedItemNames = pitySystem.getAccessedItemNames(player).toList()

        val validItems = accessedItemNames.mapNotNull { itemName ->
            val baseChance = findItemBaseChance(itemName)
            if (baseChance != null && baseChance < 0.2) {
                val originalItem = pitySystem.getOriginalItem(itemName)
                val rarity = extractRarityFromItem(originalItem)
                Triple(itemName, baseChance, rarity)
            } else null
        }.sortedBy { it.third }

        val totalPages = (validItems.size + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE
        val startIndex = page * ITEMS_PER_PAGE
        val endIndex = minOf(startIndex + ITEMS_PER_PAGE, validItems.size)

        var slot = 0
        for (i in startIndex until endIndex) {
            val (itemName, baseChance, _) = validItems[i]

            val displayItem = pitySystem.createPityDisplayItem(
                player,
                itemName,
                baseChance,
                magicFind
            )

            if (displayItem != null) {
                inv.setItem(slot, displayItem)
                slot++
            }
        }

        if (validItems.isEmpty()) {
            val infoItem = ItemStack(Material.PAPER).apply {
                val meta = itemMeta
                meta?.setDisplayName("${ChatColor.YELLOW}천장 시스템 안내")
                meta?.lore = listOf(
                    "${ChatColor.GRAY}1% 미만 확률의 아이템을",
                    "${ChatColor.GRAY}한 번이라도 시도하면",
                    "${ChatColor.GRAY}이곳에 표시됩니다."
                )
                itemMeta = meta
            }
            inv.setItem(22, infoItem)
        } else {
            addNavigationButtons(inv, page, totalPages, validItems.size)
        }

        return inv
    }

    private fun addNavigationButtons(inv: Inventory, currentPage: Int, totalPages: Int, totalItems: Int) {
        val navBarItem = ItemStack(Material.BRICK).apply {
            val meta = itemMeta
            meta?.setDisplayName(" ")
            meta?.setCustomModelData(1)
            itemMeta = meta
        }
        for (slot in 45..53) {
            inv.setItem(slot, navBarItem)
        }

        if (currentPage > 0) {
            val prevButton = ItemStack(Material.ARROW).apply {
                val meta = itemMeta
                meta?.setDisplayName("${ChatColor.YELLOW}◀ 이전 페이지")
                meta?.lore = listOf(
                    "${ChatColor.GRAY}${currentPage}페이지로 이동"
                )
                meta?.setCustomModelData(1)
                itemMeta = meta
            }
            inv.setItem(46, prevButton)
        }

        val infoItem = ItemStack(Material.COMPASS).apply {
            val meta = itemMeta
            meta?.setDisplayName("${ChatColor.GOLD}페이지 정보")
            meta?.lore = listOf(
                "${ChatColor.GRAY}현재: ${ChatColor.WHITE}${currentPage + 1}${ChatColor.GRAY}/${ChatColor.WHITE}$totalPages ${ChatColor.GRAY}페이지",
                "${ChatColor.GRAY}전체 아이템: ${ChatColor.WHITE}$totalItems${ChatColor.GRAY}개"
            )
            itemMeta = meta
        }
        inv.setItem(49, infoItem)

        if (currentPage < totalPages - 1) {
            val nextButton = ItemStack(Material.ARROW).apply {
                val meta = itemMeta
                meta?.setDisplayName("${ChatColor.YELLOW}다음 페이지 ▶")
                meta?.lore = listOf(
                    "${ChatColor.GRAY}${currentPage + 2}페이지로 이동"
                )
                meta?.setCustomModelData(2)
                itemMeta = meta
            }
            inv.setItem(52, nextButton)
        }
    }

    private fun findItemBaseChance(itemInternalName: String): Double? {
        var lowestChance: Double? = null

        try {
            val mobManager = MythicBukkit.inst().mobManager
            val allMobTypes = mobManager.mobTypes

            allMobTypes.forEach { mobType ->
                val configObj = ReflectionCache.getFieldValue(mobType, "config") ?: return@forEach

                if (configGetStringListMethod == null) {
                    configGetStringListMethod = ReflectionCache.getMethod(
                        configObj.javaClass,
                        "getStringList",
                        String::class.java
                    )
                }

                @Suppress("UNCHECKED_CAST")
                val rawDropLines = configGetStringListMethod?.invoke(configObj, "Drops") as? List<String>
                    ?: return@forEach

                rawDropLines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach

                    val parts = trimmed.split(Regex("\\s+"), limit = 3)
                    val itemDef = parts[0]

                    if (itemDef == itemInternalName || itemDef.startsWith("$itemInternalName{")) {
                        val chanceStr = parts.getOrNull(2) ?: "1.0"
                        val chance = chanceStr.toDoubleOrNull()

                        if (chance != null && chance < 0.2) {
                            lowestChance = if (lowestChance == null) {
                                chance
                            } else {
                                minOf(lowestChance!!, chance)
                            }

                            if (DEBUG) {
                                println("[PityGUI] Found $itemInternalName with chance $chance in ${mobType.internalName}")
                            }
                        }
                    }

                    val isDropTable = !itemDef.contains("{") &&
                            Material.getMaterial(itemDef.uppercase()) == null &&
                            MythicBukkit.inst().dropManager.getDropTable(itemDef).isPresent

                    if (isDropTable) {
                        val dropTableChance = findInDropTable(itemDef, itemInternalName)
                        if (dropTableChance != null) {
                            lowestChance = if (lowestChance == null) {
                                dropTableChance
                            } else {
                                minOf(lowestChance!!, dropTableChance)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (DEBUG) {
                println("[PityGUI] Error finding baseChance for $itemInternalName")
                e.printStackTrace()
            }
        }

        return lowestChance
    }

    private fun extractRarityFromItem(item: ItemStack?): Int {
        if (item == null) return 999

        val lore = item.itemMeta?.lore ?: return 999
        if (lore.isEmpty()) return 999
        val firstLore = ChatColor.stripColor(lore[0]) ?: return 999

        return when {
            firstLore.contains("헬릭스") -> 0
            firstLore.contains("제니스") -> 1
            firstLore.contains("이터널") -> 2
            firstLore.contains("디바인") -> 3
            firstLore.contains("신화") -> 4
            firstLore.contains("전설") -> 5
            firstLore.contains("에픽") -> 6
            firstLore.contains("레어") -> 7
            firstLore.contains("희귀") -> 8
            firstLore.contains("일반") -> 9
            else -> 999
        }
    }

    private fun findInDropTable(dropTableName: String, itemInternalName: String): Double? {
        var lowestChance: Double? = null

        try {
            val dropTableOpt = MythicBukkit.inst().dropManager.getDropTable(dropTableName)
            if (!dropTableOpt.isPresent) return null

            val dropTable = dropTableOpt.get()
            val dropsField = ReflectionCache.getFieldValue(dropTable, "drops") ?: return null

            val dropsGetViewMethod = ReflectionCache.getMethod(dropsField.javaClass, "getView")
            val dropsList = dropsGetViewMethod?.invoke(dropsField) as? Collection<*> ?: return null

            dropsList.forEach { drop ->
                if (drop == null) return@forEach

                val itemField = ReflectionCache.getFieldValue(drop, "item") ?: return@forEach
                val itemGetInternalNameMethod = ReflectionCache.getMethod(
                    itemField.javaClass,
                    "getInternalName"
                )
                val internalName = itemGetInternalNameMethod?.invoke(itemField) as? String

                if (internalName == itemInternalName) {
                    val weight = ReflectionCache.getFieldDouble(drop, "weight")
                    if (weight != null && weight < 0.2) {
                        lowestChance = if (lowestChance == null) {
                            weight
                        } else {
                            minOf(lowestChance!!, weight)
                        }
                    }

                    val chance = ReflectionCache.getFieldDouble(drop, "chance")
                    if (chance != null && chance < 0.2) {
                        lowestChance = if (lowestChance == null) {
                            chance
                        } else {
                            minOf(lowestChance!!, chance)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (DEBUG) e.printStackTrace()
        }

        return lowestChance
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val title = event.view.title
        if (!title.contains(GUI_TITLE)) return

        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val clickedItem = event.currentItem ?: return

        val currentPage = playerPages.getOrDefault(player.uniqueId.toString(), 0)

        when (event.slot) {
            46 -> {
                if (clickedItem.type == Material.ARROW && currentPage > 0) {
                    openGUI(player, currentPage - 1)
                }
            }
            52 -> {
                if (clickedItem.type == Material.ARROW) {
                    openGUI(player, currentPage + 1)
                }
            }
        }
    }

    fun register() {
        Bukkit.getPluginManager().registerEvents(this, MythicMagicFind.instance)
    }
}