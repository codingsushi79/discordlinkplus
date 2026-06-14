package com.mcdiscordbot;

import com.mcdiscordbot.config.PluginConfig;
import com.mcdiscordbot.discord.DiscordService;
import com.mcdiscordbot.linking.LinkManager;
import com.mcdiscordbot.linking.LinkStorage;
import com.mcdiscordbot.minecraft.ChatListener;
import com.mcdiscordbot.minecraft.LinkCommand;
import com.mcdiscordbot.minecraft.LinkLoginListener;
import com.mcdiscordbot.scheduler.PluginScheduler;
import com.mcdiscordbot.voice.VoiceBridgeManager;
import com.mcdiscordbot.voicechat.McDiscordVoicechatPlugin;
import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import org.bukkit.plugin.java.JavaPlugin;

public final class MCDiscordBot extends JavaPlugin {

    private PluginConfig pluginConfig;
    private LinkStorage linkStorage;
    private LinkManager linkManager;
    private PluginScheduler pluginScheduler;
    private DiscordService discordService;
    private VoiceBridgeManager voiceBridgeManager;
    private McDiscordVoicechatPlugin voicechatPlugin;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        pluginConfig = new PluginConfig(this);
        pluginConfig.load();

        pluginScheduler = new PluginScheduler(this);
        linkStorage = new LinkStorage(this, pluginConfig);
        linkStorage.load();
        linkManager = new LinkManager(this, pluginConfig, linkStorage);

        voiceBridgeManager = new VoiceBridgeManager(this, pluginConfig, pluginScheduler);
        discordService = new DiscordService(this, pluginConfig, linkManager, voiceBridgeManager, pluginScheduler);
        voiceBridgeManager.setDiscordService(discordService);

        getServer().getPluginManager().registerEvents(new LinkLoginListener(this, pluginConfig, linkManager), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this, pluginConfig, discordService), this);

        LinkCommand linkCommand = new LinkCommand(this, pluginConfig, linkManager, discordService);
        getCommand("mcdiscord").setExecutor(linkCommand);
        getCommand("mcdiscord").setTabCompleter(linkCommand);

        registerVoiceChatIntegration();

        if (!discordService.start()) {
            getLogger().severe("Discord bot failed to start. Check your token and guild ID in config.yml.");
        }

        voiceBridgeManager.start();
        getLogger().info("DiscordLink+ enabled.");
    }

    @Override
    public void onDisable() {
        if (voiceBridgeManager != null) {
            voiceBridgeManager.stop();
        }
        if (discordService != null) {
            discordService.shutdown();
        }
        if (voicechatPlugin != null) {
            getServer().getServicesManager().unregister(voicechatPlugin);
        }
        if (linkStorage != null) {
            linkStorage.save();
        }
        getLogger().info("DiscordLink+ disabled.");
    }

    public void reloadPlugin() {
        reloadConfig();
        pluginConfig.load();
        linkStorage.load();
        discordService.reload();
        voiceBridgeManager.reload();
        getLogger().info("Configuration reloaded.");
    }

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public LinkManager getLinkManager() {
        return linkManager;
    }

    public LinkStorage getLinkStorage() {
        return linkStorage;
    }

    public DiscordService getDiscordService() {
        return discordService;
    }

    public VoiceBridgeManager getVoiceBridgeManager() {
        return voiceBridgeManager;
    }

    public PluginScheduler getPluginScheduler() {
        return pluginScheduler;
    }

    private void registerVoiceChatIntegration() {
        BukkitVoicechatService service = getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) {
            getLogger().warning("Simple Voice Chat not found. Voice bridging will be unavailable.");
            return;
        }
        voicechatPlugin = new McDiscordVoicechatPlugin(this, voiceBridgeManager);
        service.registerPlugin(voicechatPlugin);
        getLogger().info("Registered Simple Voice Chat integration.");
    }
}
