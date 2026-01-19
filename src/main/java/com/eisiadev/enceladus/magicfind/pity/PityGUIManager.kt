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

object PityGUIManager : Listener {

    private const val GUI_TITLE = "천장 시스템"
    private const val DEBUG = false

    private var configGetStringListMethod: Method? = null

    @JvmStatic
    fun openGUI(player: Player) {
        val pitySystem = MythicMagicFind.instance.pitySystem
        val magicFind = SkriptVariableReader.getMagicFind(player)
        pitySystem.loadPlayerData(player)

        val inv = createPityInventory(player, pitySystem, magicFind)
        player.openInventory(inv)
    }

    private fun createPityInventory(
        player: Player,
        pitySystem: PitySystem,
        magicFind: Double
    ): Inventory {
        val inv = Bukkit.createInventory(
            null,
            54,
            "${ChatColor.LIGHT_PURPLE}$GUI_TITLE"
        )

        val accessedItemNames = pitySystem.getAccessedItemNames(player)

        var slot = 0
        accessedItemNames.forEach { itemName ->
            if (slot >= 54) return@forEach

            val baseChance = findItemBaseChance(itemName)

            if (baseChance != null && baseChance < 0.01) {
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
        }

        if (slot == 0) {
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
        }
        return inv
    }

    private fun findItemBaseChance(itemInternalName: String): Double? {
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

                        if (chance != null && chance < 0.01) {
                            if (DEBUG) {
                                println("[PityGUI] Found $itemInternalName with chance $chance in ${mobType.internalName}")
                            }
                            return chance
                        }
                    }

                    val isDropTable = !itemDef.contains("{") &&
                            Material.getMaterial(itemDef.uppercase()) == null &&
                            MythicBukkit.inst().dropManager.getDropTable(itemDef).isPresent

                    if (isDropTable) {
                        val dropTableChance = findInDropTable(itemDef, itemInternalName)
                        if (dropTableChance != null) {
                            return dropTableChance
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

        return null
    }

    private fun findInDropTable(dropTableName: String, itemInternalName: String): Double? {
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
                    // weight 또는 chance 필드 찾기
                    val weight = ReflectionCache.getFieldDouble(drop, "weight")
                    if (weight != null && weight < 0.01) {
                        return weight
                    }

                    val chance = ReflectionCache.getFieldDouble(drop, "chance")
                    if (chance != null && chance < 0.01) {
                        return chance
                    }
                }
            }
        } catch (e: Exception) {
            if (DEBUG) e.printStackTrace()
        }

        return null
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val title = event.view.title
        if (title.contains(GUI_TITLE)) {
            event.isCancelled = true
        }
    }

    fun register() {
        Bukkit.getPluginManager().registerEvents(this, MythicMagicFind.instance)
    }
}