package com.eisiadev.enceladus.magicfind.pouch

import com.eisiadev.enceladus.pouches.powder.MythicalPowderPouch
import com.eisiadev.enceladus.pouches.rune.RunePouchManager
import com.eisiadev.enceladus.pouches.soul.SoulPouchManager
import org.bukkit.entity.Player

class PouchIntegration(private val debug: Boolean = false) {

    fun tryAddToPouch(player: Player, itemInternalName: String, amount: Int): Boolean {
        if (tryAddToPowderPouch(player, itemInternalName, amount)) {
            return true
        }

        if (tryAddToRunePouch(player, itemInternalName, amount)) {
            return true
        }

        if (tryAddToSoulPouch(player, itemInternalName, amount)) {
            return true
        }
        return false
    }

    private fun tryAddToPowderPouch(
        player: Player,
        itemInternalName: String,
        amount: Int
    ): Boolean {
        return try {
            val result = MythicalPowderPouch.addPowderToPouch(player, itemInternalName, amount)
            if (result && debug) {
                println("[PouchIntegration] Powder Pouch에 추가: $itemInternalName x$amount")
            }
            result
        } catch (e: Exception) {
            if (debug) {
                println("[PouchIntegration] Powder Pouch 추가 중 오류 (무시): ${e.message}")
            }
            false  // ⚠️ 예외 발생 시 false 반환
        }
    }

    private fun tryAddToRunePouch(player: Player, itemInternalName: String, amount: Int): Boolean {
        return try {
            val result = RunePouchManager.addRuneToPouch(player, itemInternalName, amount)
            if (result && debug) {
                println("[PouchIntegration] Rune Pouch에 추가: $itemInternalName x$amount")
            }
            result
        } catch (e: Exception) {
            if (debug) {
                println("[PouchIntegration] Rune Pouch 추가 중 오류 (무시): ${e.message}")
            }
            false  // ⚠️ 예외 발생 시 false 반환
        }
    }

    private fun tryAddToSoulPouch(player: Player, itemInternalName: String, amount: Int): Boolean {
        return try {
            val result = SoulPouchManager.addSoulToPouch(player, itemInternalName, amount)
            if (result && debug) {
                println("[PouchIntegration] Soul Pouch에 추가: $itemInternalName x$amount")
            }
            result
        } catch (e: Exception) {
            if (debug) {
                println("[PouchIntegration] Soul Pouch 추가 중 오류 (무시): ${e.message}")
            }
            false  // ⚠️ 예외 발생 시 false 반환
        }
    }
}