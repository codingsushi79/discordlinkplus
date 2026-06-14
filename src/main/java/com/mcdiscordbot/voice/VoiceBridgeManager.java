package com.mcdiscordbot.voice;

import com.mcdiscordbot.config.PluginConfig;
import com.mcdiscordbot.discord.DiscordService;
import com.mcdiscordbot.scheduler.PluginScheduler;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiosender.AudioSender;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;
import de.maxhenkel.voicechat.api.packets.StaticSoundPacket;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.audio.AudioReceiveHandler;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.audio.CombinedAudio;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.managers.AudioManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class VoiceBridgeManager {

    public static final String VOICECHAT_PLUGIN_ID = "discordlinkplus";

    private final JavaPlugin plugin;
    private final PluginConfig config;
    private final PluginScheduler scheduler;
    private final Map<UUID, BridgedGroup> bridgedGroups = new ConcurrentHashMap<>();
    private volatile VoicechatServerApi voicechatApi;
    private volatile JDA jda;
    private volatile DiscordService discordService;

    public VoiceBridgeManager(JavaPlugin plugin, PluginConfig config, PluginScheduler scheduler) {
        this.plugin = plugin;
        this.config = config;
        this.scheduler = scheduler;
    }

    public void setDiscordService(DiscordService discordService) {
        this.discordService = discordService;
    }

    public void start() {
        if (!config.isVoiceBridgeEnabled()) {
            return;
        }
        scheduler.runGlobalRepeating(this::syncGroups, 40L, config.getVoiceSyncIntervalTicks());
        plugin.getLogger().info("Voice bridge started (SVC " + config.getVoiceServerHost() + ":" + config.getVoiceServerPort() + ").");
    }

    public void stop() {
        bridgedGroups.values().forEach(this::teardownBridge);
        bridgedGroups.clear();
    }

    public void reload() {
        stop();
        start();
    }

    public void setVoicechatApi(VoicechatServerApi voicechatApi) {
        this.voicechatApi = voicechatApi;
    }

    public void setJda(JDA jda) {
        this.jda = jda;
    }

    public void onMicrophonePacket(MicrophonePacketEventContext context) {
        if (!config.isVoiceBridgeEnabled()) {
            return;
        }

        Group group = context.group();
        if (group == null || group.hasPassword()) {
            return;
        }

        BridgedGroup bridge = bridgedGroups.get(group.getId());
        if (bridge == null) {
            return;
        }

        byte[] opus = context.packet().getOpusEncodedData();
        if (opus == null || opus.length == 0) {
            return;
        }

        bridge.setPacketTemplate(context.packet());
        bridge.sendHandler().enqueue(opus);
    }

    public void onPlayerStateChanged(UUID playerUuid) {
        scheduler.runGlobal(this::syncGroups);
    }

    private void syncGroups() {
        if (!config.isVoiceBridgeEnabled() || voicechatApi == null || jda == null) {
            return;
        }

        Collection<Group> groups = voicechatApi.getGroups();
        Set<UUID> activePublicGroups = ConcurrentHashMap.newKeySet();

        for (Group group : groups) {
            if (group.hasPassword()) {
                continue;
            }
            if (countMembers(group) == 0) {
                continue;
            }
            activePublicGroups.add(group.getId());
            bridgedGroups.computeIfAbsent(group.getId(), this::createBridge);
        }

        bridgedGroups.entrySet().removeIf(entry -> {
            UUID groupId = entry.getKey();
            if (activePublicGroups.contains(groupId)) {
                entry.getValue().refreshAnchor(voicechatApi);
                return false;
            }
            if (config.isDeleteEmptyVoiceChannels()) {
                teardownBridge(entry.getValue());
            }
            return true;
        });
    }

    private BridgedGroup createBridge(UUID groupId) {
        Group group = voicechatApi.getGroup(groupId);
        String groupName = group != null ? group.getName() : groupId.toString().substring(0, 8);
        String channelName = config.getVoiceChannelPrefix() + groupName;

        McToDiscordSendHandler sendHandler = new McToDiscordSendHandler();
        DiscordToMcReceiveHandler receiveHandler = new DiscordToMcReceiveHandler(groupId);

        if (discordService == null) {
            return new BridgedGroup(groupId, groupName, -1L, sendHandler, receiveHandler, voicechatApi.createEncoder());
        }

        Optional<VoiceChannel> created = discordService.createVoiceChannel(channelName);
        if (created.isEmpty()) {
            plugin.getLogger().warning("Could not create Discord voice channel for group " + groupName);
            return new BridgedGroup(groupId, groupName, -1L, sendHandler, receiveHandler, voicechatApi.createEncoder());
        }

        VoiceChannel channel = created.get();
        AudioManager audioManager = channel.getGuild().getAudioManager();
        audioManager.setSendingHandler(sendHandler);
        audioManager.setReceivingHandler(receiveHandler);
        audioManager.openAudioConnection(channel);
        plugin.getLogger().info("Bridged SVC group '" + groupName + "' to Discord VC '" + channel.getName() + "'");

        BridgedGroup bridge = new BridgedGroup(
            groupId,
            groupName,
            channel.getIdLong(),
            sendHandler,
            receiveHandler,
            voicechatApi.createEncoder()
        );
        bridge.refreshAnchor(voicechatApi);
        return bridge;
    }

    private void teardownBridge(BridgedGroup bridge) {
        bridge.close(voicechatApi);
        if (bridge.discordChannelId() > 0 && jda != null) {
            VoiceChannel channel = jda.getVoiceChannelById(bridge.discordChannelId());
            if (channel != null) {
                AudioManager manager = channel.getGuild().getAudioManager();
                manager.closeAudioConnection();
                channel.delete().queue(null, error ->
                    plugin.getLogger().warning("Failed to delete bridged channel: " + error.getMessage())
                );
            }
        }
    }

    private int countMembers(Group group) {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            VoicechatConnection connection = voicechatApi.getConnectionOf(player.getUniqueId());
            if (connection == null) {
                continue;
            }
            Group playerGroup = connection.getGroup();
            if (playerGroup != null && playerGroup.getId().equals(group.getId())) {
                count++;
            }
        }
        return count;
    }

    private void relayDiscordAudioToGroup(UUID groupId, byte[] pcmStereo48k) {
        if (voicechatApi == null || pcmStereo48k == null || pcmStereo48k.length == 0) {
            return;
        }

        BridgedGroup bridge = bridgedGroups.get(groupId);
        if (bridge == null) {
            return;
        }

        byte[] opus = bridge.encodePcm(pcmStereo48k);
        if (opus == null || opus.length == 0) {
            return;
        }

        MicrophonePacket template = bridge.packetTemplate();
        if (template != null) {
            StaticSoundPacket packet = template.staticSoundPacketBuilder().opusEncodedData(opus).build();
            for (Player player : Bukkit.getOnlinePlayers()) {
                VoicechatConnection connection = voicechatApi.getConnectionOf(player.getUniqueId());
                if (connection == null) {
                    continue;
                }
                Group group = connection.getGroup();
                if (group != null && group.getId().equals(groupId)) {
                    voicechatApi.sendStaticSoundPacketTo(connection, packet);
                }
            }
            return;
        }

        AudioSender sender = bridge.audioSender();
        if (sender != null && sender.canSend()) {
            sender.send(opus);
        }
    }

    private static short[] bytesToShorts(byte[] bytes) {
        short[] samples = new short[bytes.length / 2];
        for (int i = 0; i < samples.length; i++) {
            int index = i * 2;
            samples[i] = (short) ((bytes[index + 1] << 8) | (bytes[index] & 0xFF));
        }
        return samples;
    }

    public record MicrophonePacketEventContext(MicrophonePacket packet, Group group) {
    }

    private static final class BridgedGroup {
        private final UUID groupId;
        private final String groupName;
        private final long discordChannelId;
        private final McToDiscordSendHandler sendHandler;
        private final DiscordToMcReceiveHandler receiveHandler;
        private final OpusEncoder encoder;
        private volatile MicrophonePacket packetTemplate;
        private volatile AudioSender audioSender;

        BridgedGroup(
            UUID groupId,
            String groupName,
            long discordChannelId,
            McToDiscordSendHandler sendHandler,
            DiscordToMcReceiveHandler receiveHandler,
            OpusEncoder encoder
        ) {
            this.groupId = groupId;
            this.groupName = groupName;
            this.discordChannelId = discordChannelId;
            this.sendHandler = sendHandler;
            this.receiveHandler = receiveHandler;
            this.encoder = encoder;
        }

        UUID groupId() {
            return groupId;
        }

        String groupName() {
            return groupName;
        }

        long discordChannelId() {
            return discordChannelId;
        }

        McToDiscordSendHandler sendHandler() {
            return sendHandler;
        }

        DiscordToMcReceiveHandler receiveHandler() {
            return receiveHandler;
        }

        MicrophonePacket packetTemplate() {
            return packetTemplate;
        }

        AudioSender audioSender() {
            return audioSender;
        }

        void setPacketTemplate(MicrophonePacket packetTemplate) {
            this.packetTemplate = packetTemplate;
        }

        byte[] encodePcm(byte[] pcm) {
            if (encoder == null) {
                return null;
            }
            return encoder.encode(bytesToShorts(pcm));
        }

        void refreshAnchor(VoicechatServerApi api) {
            if (api == null) {
                return;
            }
            if (audioSender != null) {
                api.unregisterAudioSender(audioSender);
                audioSender = null;
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                VoicechatConnection connection = api.getConnectionOf(player.getUniqueId());
                if (connection == null) {
                    continue;
                }
                Group group = connection.getGroup();
                if (group != null && group.getId().equals(groupId)) {
                    AudioSender sender = api.createAudioSender(connection);
                    if (api.registerAudioSender(sender)) {
                        audioSender = sender;
                    }
                    return;
                }
            }
        }

        void close(VoicechatServerApi api) {
            if (api != null && audioSender != null) {
                api.unregisterAudioSender(audioSender);
                audioSender = null;
            }
            if (encoder != null && !encoder.isClosed()) {
                encoder.close();
            }
        }
    }

    private final class McToDiscordSendHandler implements AudioSendHandler {
        private final ConcurrentLinkedQueue<byte[]> queue = new ConcurrentLinkedQueue<>();

        void enqueue(byte[] opus) {
            queue.offer(opus);
        }

        @Override
        public boolean canProvide() {
            return !queue.isEmpty();
        }

        @Override
        public ByteBuffer provide20MsAudio() {
            byte[] data = queue.poll();
            return data == null ? null : ByteBuffer.wrap(data);
        }

        @Override
        public boolean isOpus() {
            return true;
        }
    }

    private final class DiscordToMcReceiveHandler implements AudioReceiveHandler {
        private final UUID groupId;

        DiscordToMcReceiveHandler(UUID groupId) {
            this.groupId = groupId;
        }

        @Override
        public boolean canReceiveCombined() {
            return true;
        }

        @Override
        public void handleCombinedAudio(CombinedAudio combinedAudio) {
            byte[] pcm = combinedAudio.getAudioData(1.0);
            if (pcm == null || pcm.length == 0) {
                return;
            }
            scheduler.runGlobal(() -> relayDiscordAudioToGroup(groupId, pcm));
        }
    }
}
