package com.eisiadev.enceladus.magicfind.commands

import com.eisiadev.enceladus.magicfind.config.MagicFindConfig
import com.eisiadev.enceladus.magicfind.notification.PlayerNotificationManager
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class NotificationToggleCommand(
    private val config: MagicFindConfig,
    private val notificationManager: PlayerNotificationManager
) : CommandExecutor, TabCompleter {
    
    private val forceBroadcastTiers = setOf("pray", "incarnate", "insane", "unleashed")
    
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (sender !is Player) {
            sender.sendMessage("${ChatColor.RED}This command can only be used by players.")
            return true
        }
        
        when (args.getOrNull(0)?.lowercase()) {
            "list" -> listNotifications(sender)
            "toggle" -> {
                if (args.size < 2) {
                    sender.sendMessage("${ChatColor.RED}Usage: /mfnotify toggle <tier>")
                    return true
                }
                toggleNotification(sender, args[1])
            }
            "enable" -> {
                if (args.size < 2) {
                    sender.sendMessage("${ChatColor.RED}Usage: /mfnotify enable <tier|all>")
                    return true
                }
                if (args[1].equals("all", ignoreCase = true)) {
                    enableAll(sender)
                } else {
                    enableNotification(sender, args[1])
                }
            }
            "disable" -> {
                if (args.size < 2) {
                    sender.sendMessage("${ChatColor.RED}Usage: /mfnotify disable <tier|all>")
                    return true
                }
                if (args[1].equals("all", ignoreCase = true)) {
                    disableAll(sender)
                } else {
                    disableNotification(sender, args[1])
                }
            }
            else -> showHelp(sender)
        }
        
        return true
    }
    
    private fun showHelp(player: Player) {
        player.sendMessage("""
            ${ChatColor.GOLD}${ChatColor.BOLD}Magic Find Notifications
            ${ChatColor.YELLOW}/mfnotify list ${ChatColor.GRAY}- Show all notification tiers and their status
            ${ChatColor.YELLOW}/mfnotify toggle <tier> ${ChatColor.GRAY}- Toggle a specific tier
            ${ChatColor.YELLOW}/mfnotify enable <tier|all> ${ChatColor.GRAY}- Enable a tier or all tiers
            ${ChatColor.YELLOW}/mfnotify disable <tier|all> ${ChatColor.GRAY}- Disable a tier or all tiers
            ${ChatColor.GRAY}Note: Broadcast tiers (pray and rarer) cannot be disabled
        """.trimIndent())
    }
    
    private fun listNotifications(player: Player) {
        val disabled = notificationManager.getDisabledTiers(player)
        
        player.sendMessage("${ChatColor.GOLD}${ChatColor.BOLD}Your Notification Settings:")
        
        config.rarityTiers
            .filter { it.enabled }
            .sortedByDescending { it.maxChance }
            .forEach { tier ->
                val isBroadcast = tier.broadcast || forceBroadcastTiers.contains(tier.id)
                val isEnabled = !disabled.contains(tier.id)
                
                val status = when {
                    isBroadcast -> "${ChatColor.GRAY}(Always On)"
                    isEnabled -> "${ChatColor.GREEN}✔ Enabled"
                    else -> "${ChatColor.RED}✘ Disabled"
                }
                
                val chanceRange = formatChanceRange(tier.minChance, tier.maxChance)
                player.sendMessage("  ${ChatColor.YELLOW}${tier.id} ${ChatColor.GRAY}[$chanceRange] $status")
            }
    }
    
    private fun toggleNotification(player: Player, tierId: String) {
        val tier = config.rarityTiers.find { it.id.equals(tierId, ignoreCase = true) }
        
        if (tier == null) {
            player.sendMessage("${ChatColor.RED}Unknown tier: $tierId")
            return
        }
        
        if (tier.broadcast || forceBroadcastTiers.contains(tier.id)) {
            player.sendMessage("${ChatColor.RED}Cannot disable broadcast tier: ${tier.id}")
            return
        }
        
        val isNowEnabled = notificationManager.toggleNotification(player, tier.id)
        val status = if (isNowEnabled) "${ChatColor.GREEN}enabled" else "${ChatColor.RED}disabled"
        player.sendMessage("${ChatColor.YELLOW}Notifications for ${tier.id} are now $status${ChatColor.YELLOW}.")
    }
    
    private fun enableNotification(player: Player, tierId: String) {
        val tier = config.rarityTiers.find { it.id.equals(tierId, ignoreCase = true) }
        
        if (tier == null) {
            player.sendMessage("${ChatColor.RED}Unknown tier: $tierId")
            return
        }
        
        if (notificationManager.isNotificationEnabled(player, tier.id)) {
            player.sendMessage("${ChatColor.YELLOW}Notifications for ${tier.id} are already enabled.")
            return
        }
        
        notificationManager.toggleNotification(player, tier.id)
        player.sendMessage("${ChatColor.GREEN}Enabled notifications for ${tier.id}.")
    }
    
    private fun disableNotification(player: Player, tierId: String) {
        val tier = config.rarityTiers.find { it.id.equals(tierId, ignoreCase = true) }
        
        if (tier == null) {
            player.sendMessage("${ChatColor.RED}Unknown tier: $tierId")
            return
        }
        
        if (tier.broadcast || forceBroadcastTiers.contains(tier.id)) {
            player.sendMessage("${ChatColor.RED}Cannot disable broadcast tier: ${tier.id}")
            return
        }
        
        if (!notificationManager.isNotificationEnabled(player, tier.id)) {
            player.sendMessage("${ChatColor.YELLOW}Notifications for ${tier.id} are already disabled.")
            return
        }
        
        notificationManager.toggleNotification(player, tier.id)
        player.sendMessage("${ChatColor.RED}Disabled notifications for ${tier.id}.")
    }
    
    private fun enableAll(player: Player) {
        notificationManager.enableAll(player)
        player.sendMessage("${ChatColor.GREEN}Enabled all notifications.")
    }
    
    private fun disableAll(player: Player) {
        val toggleableTiers = config.rarityTiers
            .filter { it.enabled && !it.broadcast && !forceBroadcastTiers.contains(it.id) }
            .map { it.id }
        
        notificationManager.disableAll(player, toggleableTiers)
        player.sendMessage("${ChatColor.RED}Disabled all toggleable notifications.")
        player.sendMessage("${ChatColor.GRAY}Broadcast tiers (pray and rarer) remain enabled.")
    }
    
    private fun formatChanceRange(min: Double, max: Double): String {
        val minPercent = min * 100
        val maxPercent = max * 100
        
        return when {
            maxPercent >= 10.0 -> String.format("%.1f%%-%.1f%%", minPercent, maxPercent)
            maxPercent >= 1.0 -> String.format("%.2f%%-%.2f%%", minPercent, maxPercent)
            else -> String.format("%.3f%%-%.3f%%", minPercent, maxPercent)
        }
    }
    
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (sender !is Player) return emptyList()
        
        return when (args.size) {
            1 -> listOf("list", "toggle", "enable", "disable").filter { 
                it.startsWith(args[0].lowercase()) 
            }
            2 -> {
                when (args[0].lowercase()) {
                    "toggle", "enable", "disable" -> {
                        val toggleable = config.rarityTiers
                            .filter { it.enabled && !it.broadcast && !forceBroadcastTiers.contains(it.id) }
                            .map { it.id }
                        
                        val options = toggleable + "all"
                        options.filter { it.startsWith(args[1].lowercase()) }
                    }
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
    }
}