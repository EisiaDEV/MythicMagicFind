package com.eisiadev.enceladus.magicfind.util

import ch.njol.skript.variables.Variables
import org.bukkit.entity.Player

object SkriptVariableReader {
    fun getMagicFind(player: Player): Double {
        try {
            val variableName = "magic_find.${player.uniqueId}"
            val value = Variables.getVariable(variableName, null, false)

            return when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }
        } catch (e: Exception) {
            return 0.0
        }
    }
}