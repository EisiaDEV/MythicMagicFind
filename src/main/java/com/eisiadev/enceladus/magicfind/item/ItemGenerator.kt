package com.eisiadev.enceladus.magicfind.item

import com.eisiadev.enceladus.magicfind.util.ReflectionCache
import io.lumine.mythic.bukkit.MythicBukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.lang.reflect.Method

class ItemGenerator(private val debug: Boolean = false) {
    
    private var mythicItemGenerateMethod: Method? = null
    private var bukkitItemBuildMethod: Method? = null
    
    fun generateItem(
        itemDef: String,
        amount: Int,
        mythicItemObject: Any? = null
    ): ItemStack? {
        return try {
            when {
                mythicItemObject != null -> generateFromMythicObject(mythicItemObject, amount)
                itemDef.contains("{") || isMythicItem(itemDef) -> generateFromMythicManager(itemDef, amount)
                else -> generateVanillaItem(itemDef, amount)
            }
        } catch (e: Exception) {
            if (debug) e.printStackTrace()
            null
        }
    }
    
    private fun generateFromMythicObject(mythicItemObject: Any, amount: Int): ItemStack? {
        if (mythicItemGenerateMethod == null) {
            mythicItemGenerateMethod = ReflectionCache.getMethod(
                mythicItemObject.javaClass,
                "generateItemStack",
                Int::class.javaPrimitiveType!!
            )
        }
        return convertToBukkitStack(
            mythicItemGenerateMethod?.invoke(mythicItemObject, amount),
            amount
        )
    }
    
    private fun generateFromMythicManager(itemDef: String, amount: Int): ItemStack? {
        val itemOpt = MythicBukkit.inst().itemManager.getItem(itemDef)
        return if (itemOpt.isPresent) {
            convertToBukkitStack(itemOpt.get().generateItemStack(amount), amount)
        } else null
    }
    
    private fun generateVanillaItem(itemDef: String, amount: Int): ItemStack? {
        val mat = Material.getMaterial(itemDef.split("{")[0].uppercase()) ?: return null
        return ItemStack(mat, amount)
    }
    
    private fun isMythicItem(itemDef: String): Boolean {
        return MythicBukkit.inst().itemManager.getItem(itemDef).isPresent
    }
    
    private fun convertToBukkitStack(abstractItem: Any?, amount: Int): ItemStack? {
        if (abstractItem == null) return null
        
        val stack = when {
            abstractItem is ItemStack -> abstractItem
            abstractItem.javaClass.simpleName == "BukkitItemStack" -> {
                if (bukkitItemBuildMethod == null) {
                    bukkitItemBuildMethod = ReflectionCache.getMethod(
                        abstractItem.javaClass,
                        "build"
                    )
                }
                bukkitItemBuildMethod?.invoke(abstractItem) as? ItemStack
            }
            else -> null
        }
        stack?.amount = amount
        return stack
    }
}