package com.mcdiscordbot.minecraft;

import com.mcdiscordbot.config.PluginConfig;
import com.mcdiscordbot.discord.DiscordService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ChatListener implements Listener {

    private final PluginConfig config;
    private final DiscordService discordService;

    public ChatListener(org.bukkit.plugin.java.JavaPlugin plugin, PluginConfig config, DiscordService discordService) {
        this.config = config;
        this.discordService = discordService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!config.isChatEnabled()) {
            return;
        }
        if (!event.getPlayer().hasPermission("mcdiscord.chat")) {
            return;
        }
        discordService.sendMinecraftChat(event.getPlayer(), event.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!config.isChatEnabled() || !config.isJoinLeaveMessages()) {
            return;
        }
        Player player = event.getPlayer();
        discordService.sendSystemMessage(player.getName() + " joined the server.");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (!config.isChatEnabled() || !config.isJoinLeaveMessages()) {
            return;
        }
        Player player = event.getPlayer();
        discordService.sendSystemMessage(player.getName() + " left the server.");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (!config.isChatEnabled() || !config.isDeathMessages()) {
            return;
        }
        String message = event.getDeathMessage();
        if (message != null && !message.isBlank()) {
            discordService.sendSystemMessage(message);
        }
    }
}
