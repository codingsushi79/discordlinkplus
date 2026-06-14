package com.mcdiscordbot.minecraft;

import com.mcdiscordbot.MCDiscordBot;
import com.mcdiscordbot.config.PluginConfig;
import com.mcdiscordbot.linking.LinkManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.Optional;

public final class LinkLoginListener implements Listener {

    private final MCDiscordBot plugin;
    private final PluginConfig config;
    private final LinkManager linkManager;

    public LinkLoginListener(MCDiscordBot plugin, PluginConfig config, LinkManager linkManager) {
        this.plugin = plugin;
        this.config = config;
        this.linkManager = linkManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!config.isLinkingEnabled()) {
            return;
        }
        if (plugin.getServer().getOfflinePlayer(event.getUniqueId()).isOp()) {
            return;
        }
        if (linkManager.isLinked(event.getUniqueId())) {
            return;
        }

        Optional<String> code = linkManager.createPendingLink(event.getUniqueId(), event.getName());
        if (code.isEmpty()) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, config.colorize("&cFailed to generate a link code. Try again."));
            return;
        }

        String invite = plugin.getDiscordService().getInviteUrl();
        String message = config.formatKickMessage(code.get(), invite);
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST, message);
    }
}
