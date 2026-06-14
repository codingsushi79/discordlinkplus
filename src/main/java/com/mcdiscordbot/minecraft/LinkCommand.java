package com.mcdiscordbot.minecraft;

import com.mcdiscordbot.MCDiscordBot;
import com.mcdiscordbot.config.PluginConfig;
import com.mcdiscordbot.linking.LinkManager;
import com.mcdiscordbot.discord.DiscordService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class LinkCommand implements CommandExecutor, TabCompleter {

    private final MCDiscordBot plugin;
    private final PluginConfig config;
    private final LinkManager linkManager;
    private final DiscordService discordService;

    public LinkCommand(MCDiscordBot plugin, PluginConfig config, LinkManager linkManager, DiscordService discordService) {
        this.plugin = plugin;
        this.config = config;
        this.linkManager = linkManager;
        this.discordService = discordService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(config.colorize("&6DiscordLink+ &7- use &e/mcdiscord <reload|link|unlink|status>"));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                if (!sender.hasPermission("mcdiscord.admin")) {
                    sender.sendMessage(config.colorize("&cNo permission."));
                    return true;
                }
                plugin.reloadPlugin();
                sender.sendMessage(config.colorize("&aConfiguration reloaded."));
            }
            case "link" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(config.colorize("&cPlayers only."));
                    return true;
                }
                if (linkManager.isLinked(player.getUniqueId())) {
                    sender.sendMessage(config.colorize("&aYour account is already linked."));
                    return true;
                }
                Optional<String> code = linkManager.createPendingLink(player.getUniqueId(), player.getName());
                if (code.isEmpty()) {
                    sender.sendMessage(config.colorize("&cFailed to generate link code."));
                    return true;
                }
                sender.sendMessage(config.colorize("&7Your link code: &e" + code.get()));
                sender.sendMessage(config.colorize("&7DM this code to the Discord bot."));
            }
            case "unlink" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(config.colorize("&cPlayers only."));
                    return true;
                }
                Optional<com.mcdiscordbot.linking.LinkStorage.StoredLink> link = linkManager.getLink(player.getUniqueId());
                if (link.isEmpty()) {
                    sender.sendMessage(config.colorize("&cYou are not linked."));
                    return true;
                }
                discordService.removeVerifiedRole(link.get().discordId());
                linkManager.unlinkMinecraft(player.getUniqueId());
                sender.sendMessage(config.colorize("&aYour account has been unlinked."));
            }
            case "status" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(config.colorize("&cPlayers only."));
                    return true;
                }
                if (linkManager.isLinked(player.getUniqueId())) {
                    linkManager.getLink(player.getUniqueId()).ifPresent(stored ->
                        sender.sendMessage(config.colorize("&aLinked to Discord user &f" + stored.discordName()))
                    );
                } else {
                    sender.sendMessage(config.colorize("&cNot linked."));
                }
            }
            default -> sender.sendMessage(config.colorize("&cUnknown subcommand."));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            for (String option : List.of("reload", "link", "unlink", "status")) {
                if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    completions.add(option);
                }
            }
        }
        return completions;
    }
}
