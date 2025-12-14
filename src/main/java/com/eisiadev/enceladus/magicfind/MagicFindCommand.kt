package com.eisiadev.enceladus.magicfind

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class MagicFindCommand : CommandExecutor {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (!sender.hasPermission("magicfind.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command!")
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage("§eUsage: /magicfind reload")
            return true
        }

        when (args[0].lowercase()) {
            "reload" -> {
                sender.sendMessage("§aMagicFindDrops reloaded!")
            }
            else -> {
                sender.sendMessage("§cUnknown subcommand!")
            }
        }

        return true
    }
}