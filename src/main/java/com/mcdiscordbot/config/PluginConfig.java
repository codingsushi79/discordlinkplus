package com.mcdiscordbot.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class PluginConfig {

    private final JavaPlugin plugin;

    private String discordToken;
    private long guildId;
    private long chatChannelId;
    private long voiceCategoryId;
    private long verifiedRoleId;
    private String activity;

    private boolean linkingEnabled;
    private int codeExpirySeconds;
    private int codeLength;
    private List<String> kickMessageLines;

    private boolean chatEnabled;
    private String mcToDiscordFormat;
    private String discordToMcFormat;
    private boolean joinLeaveMessages;
    private boolean deathMessages;

    private boolean voiceBridgeEnabled;
    private String voiceChannelPrefix;
    private int voiceSyncIntervalTicks;
    private boolean deleteEmptyVoiceChannels;
    private String voiceServerHost;
    private int voiceServerPort;

    private String linksFileName;

    public PluginConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        discordToken = config.getString("discord.token", "");
        guildId = parseLong(config.getString("discord.guild-id", "0"));
        chatChannelId = parseLong(config.getString("discord.chat-channel-id", "0"));
        voiceCategoryId = parseLong(config.getString("discord.voice-category-id", "0"));
        verifiedRoleId = parseLong(config.getString("discord.verified-role-id", "0"));
        activity = config.getString("discord.activity", "Minecraft");

        linkingEnabled = config.getBoolean("linking.enabled", true);
        codeExpirySeconds = config.getInt("linking.code-expiry-seconds", 600);
        codeLength = config.getInt("linking.code-length", 6);
        kickMessageLines = config.getStringList("linking.kick-message");

        chatEnabled = config.getBoolean("chat.enabled", true);
        mcToDiscordFormat = config.getString("chat.minecraft-to-discord-format", "**%player%**: %message%");
        discordToMcFormat = config.getString("chat.discord-to-minecraft-format", "&9[Discord] &b%user%&7: &f%message%");
        joinLeaveMessages = config.getBoolean("chat.join-leave-messages", true);
        deathMessages = config.getBoolean("chat.death-messages", true);

        voiceBridgeEnabled = config.getBoolean("voice-bridge.enabled", true);
        voiceChannelPrefix = config.getString("voice-bridge.channel-prefix", "VC ");
        voiceSyncIntervalTicks = config.getInt("voice-bridge.sync-interval-ticks", 40);
        deleteEmptyVoiceChannels = config.getBoolean("voice-bridge.delete-empty-channels", true);
        voiceServerHost = config.getString("voice-bridge.server-host", "127.0.0.1");
        voiceServerPort = config.getInt("voice-bridge.server-port", 24454);

        linksFileName = config.getString("storage.links-file", "links.json");
    }

    public String colorize(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    public String formatKickMessage(String code, String discordInvite) {
        int minutes = Math.max(1, codeExpirySeconds / 60);
        StringBuilder builder = new StringBuilder();
        for (String line : kickMessageLines) {
            builder.append(line
                    .replace("%code%", code)
                    .replace("%minutes%", String.valueOf(minutes))
                    .replace("%discord-invite%", discordInvite))
                .append('\n');
        }
        return colorize(builder.toString().trim());
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    public String getDiscordToken() {
        return discordToken;
    }

    public long getGuildId() {
        return guildId;
    }

    public long getChatChannelId() {
        return chatChannelId;
    }

    public long getVoiceCategoryId() {
        return voiceCategoryId;
    }

    public long getVerifiedRoleId() {
        return verifiedRoleId;
    }

    public String getActivity() {
        return activity;
    }

    public boolean isLinkingEnabled() {
        return linkingEnabled;
    }

    public int getCodeExpirySeconds() {
        return codeExpirySeconds;
    }

    public int getCodeLength() {
        return codeLength;
    }

    public boolean isChatEnabled() {
        return chatEnabled;
    }

    public String getMcToDiscordFormat() {
        return mcToDiscordFormat;
    }

    public String getDiscordToMcFormat() {
        return discordToMcFormat;
    }

    public boolean isJoinLeaveMessages() {
        return joinLeaveMessages;
    }

    public boolean isDeathMessages() {
        return deathMessages;
    }

    public boolean isVoiceBridgeEnabled() {
        return voiceBridgeEnabled;
    }

    public String getVoiceChannelPrefix() {
        return voiceChannelPrefix;
    }

    public int getVoiceSyncIntervalTicks() {
        return voiceSyncIntervalTicks;
    }

    public boolean isDeleteEmptyVoiceChannels() {
        return deleteEmptyVoiceChannels;
    }

    public String getVoiceServerHost() {
        return voiceServerHost;
    }

    public int getVoiceServerPort() {
        return voiceServerPort;
    }

    public String getLinksFileName() {
        return linksFileName;
    }
}
