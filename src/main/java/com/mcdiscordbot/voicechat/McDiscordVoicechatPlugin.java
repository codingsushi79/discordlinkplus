package com.mcdiscordbot.voicechat;

import com.mcdiscordbot.MCDiscordBot;
import com.mcdiscordbot.voice.VoiceBridgeManager;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PlayerStateChangedEvent;
import org.bukkit.entity.Player;

public final class McDiscordVoicechatPlugin implements VoicechatPlugin {

    private final MCDiscordBot plugin;
    private final VoiceBridgeManager voiceBridgeManager;

    public McDiscordVoicechatPlugin(MCDiscordBot plugin, VoiceBridgeManager voiceBridgeManager) {
        this.plugin = plugin;
        this.voiceBridgeManager = voiceBridgeManager;
    }

    @Override
    public String getPluginId() {
        return VoiceBridgeManager.VOICECHAT_PLUGIN_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        if (api instanceof VoicechatServerApi serverApi) {
            voiceBridgeManager.setVoicechatApi(serverApi);
            plugin.getLogger().info("Simple Voice Chat API initialized.");
        }
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophone);
        registration.registerEvent(PlayerStateChangedEvent.class, this::onPlayerStateChanged);
    }

    private void onMicrophone(MicrophonePacketEvent event) {
        VoicechatConnection sender = event.getSenderConnection();
        if (sender == null) {
            return;
        }
        if (!(sender.getPlayer().getPlayer() instanceof Player)) {
            return;
        }

        Group group = sender.getGroup();
        voiceBridgeManager.onMicrophonePacket(new VoiceBridgeManager.MicrophonePacketEventContext(event.getPacket(), group));
    }

    private void onPlayerStateChanged(PlayerStateChangedEvent event) {
        voiceBridgeManager.onPlayerStateChanged(event.getPlayerUuid());
    }
}
