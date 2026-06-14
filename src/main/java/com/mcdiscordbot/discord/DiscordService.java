package com.mcdiscordbot.discord;

import club.minnced.discord.jdave.interop.JDaveSessionFactory;
import com.mcdiscordbot.config.PluginConfig;
import com.mcdiscordbot.linking.LinkManager;
import com.mcdiscordbot.linking.LinkStorage;
import com.mcdiscordbot.scheduler.PluginScheduler;
import com.mcdiscordbot.voice.VoiceBridgeManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.audio.AudioModuleConfig;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class DiscordService extends ListenerAdapter {

    private final JavaPlugin plugin;
    private final PluginConfig config;
    private final LinkManager linkManager;
    private final VoiceBridgeManager voiceBridgeManager;
    private final PluginScheduler scheduler;
    private final AtomicReference<JDA> jda = new AtomicReference<>();

    public DiscordService(
        JavaPlugin plugin,
        PluginConfig config,
        LinkManager linkManager,
        VoiceBridgeManager voiceBridgeManager,
        PluginScheduler scheduler
    ) {
        this.plugin = plugin;
        this.config = config;
        this.linkManager = linkManager;
        this.voiceBridgeManager = voiceBridgeManager;
        this.scheduler = scheduler;
    }

    public boolean start() {
        if (config.getDiscordToken().isBlank() || config.getDiscordToken().equals("YOUR_BOT_TOKEN_HERE")) {
            plugin.getLogger().warning("Discord token is not configured.");
            return false;
        }
        if (config.getGuildId() == 0L) {
            plugin.getLogger().warning("Discord guild-id is not configured.");
            return false;
        }

        shutdown();

        try {
            JDA built = JDABuilder.createDefault(config.getDiscordToken(), getIntents())
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .setChunkingFilter(ChunkingFilter.ALL)
                .setAudioModuleConfig(new AudioModuleConfig().withDaveSessionFactory(new JDaveSessionFactory()))
                .addEventListeners(this)
                .build();
            built.awaitReady();
            jda.set(built);
            voiceBridgeManager.setJda(built);
            updateActivity();
            scheduler.runGlobalRepeating(this::updateActivity, 100L, 600L);
            plugin.getLogger().info("Connected to Discord as " + built.getSelfUser().getName());
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            plugin.getLogger().severe("Discord startup interrupted: " + exception.getMessage());
            return false;
        } catch (Exception exception) {
            plugin.getLogger().severe("Discord startup failed: " + exception.getMessage());
            return false;
        }
    }

    public void reload() {
        shutdown();
        start();
    }

    public void shutdown() {
        JDA current = jda.getAndSet(null);
        if (current != null) {
            voiceBridgeManager.setJda(null);
            current.shutdownNow();
        }
    }

    public Optional<JDA> getJda() {
        return Optional.ofNullable(jda.get());
    }

    public Optional<Guild> getGuild() {
        return getJda().flatMap(j -> Optional.ofNullable(j.getGuildById(config.getGuildId())));
    }

    public String getInviteUrl() {
        return getGuild()
            .map(guild -> "https://discord.gg/" + guild.getVanityCode())
            .filter(url -> !url.endsWith("null"))
            .orElse("your Discord server");
    }

    public void sendMinecraftChat(Player player, String message) {
        if (config.getChatChannelId() == 0L) {
            return;
        }
        getGuild().ifPresent(guild -> {
            TextChannel channel = guild.getTextChannelById(config.getChatChannelId());
            if (channel == null) {
                return;
            }
            String formatted = config.getMcToDiscordFormat()
                .replace("%player%", player.getName())
                .replace("%message%", message)
                .replace("%world%", player.getWorld().getName());
            channel.sendMessage(formatted).queue(null, error ->
                plugin.getLogger().warning("Failed to send chat to Discord: " + error.getMessage())
            );
        });
    }

    public void sendSystemMessage(String message) {
        if (config.getChatChannelId() == 0L) {
            return;
        }
        getGuild().ifPresent(guild -> {
            TextChannel channel = guild.getTextChannelById(config.getChatChannelId());
            if (channel != null) {
                channel.sendMessage("*" + message + "*").queue();
            }
        });
    }

    public void relayDiscordChatToMinecraft(String user, String message) {
        String formatted = config.getDiscordToMcFormat()
            .replace("%user%", user)
            .replace("%message%", message);
        String colored = config.colorize(formatted);
        scheduler.runGlobal(() -> Bukkit.broadcastMessage(colored));
    }

    public void assignVerifiedRole(long discordUserId) {
        if (config.getVerifiedRoleId() == 0L) {
            return;
        }
        getGuild().ifPresent(guild -> {
            Role role = guild.getRoleById(config.getVerifiedRoleId());
            Member member = guild.getMemberById(discordUserId);
            if (role != null && member != null) {
                guild.addRoleToMember(member, role).queue();
            }
        });
    }

    public void removeVerifiedRole(long discordUserId) {
        if (config.getVerifiedRoleId() == 0L) {
            return;
        }
        getGuild().ifPresent(guild -> {
            Role role = guild.getRoleById(config.getVerifiedRoleId());
            Member member = guild.getMemberById(discordUserId);
            if (role != null && member != null && member.getRoles().contains(role)) {
                guild.removeRoleFromMember(member, role).queue();
            }
        });
    }

    public Optional<VoiceChannel> createVoiceChannel(String name) {
        return getGuild().map(guild -> {
            if (config.getVoiceCategoryId() != 0L && guild.getCategoryById(config.getVoiceCategoryId()) != null) {
                return guild.getCategoryById(config.getVoiceCategoryId())
                    .createVoiceChannel(name)
                    .complete();
            }
            return guild.createVoiceChannel(name).complete();
        });
    }

    public void deleteVoiceChannel(long channelId) {
        getJda().ifPresent(jdaInstance -> {
            VoiceChannel channel = jdaInstance.getVoiceChannelById(channelId);
            if (channel != null) {
                channel.delete().queue(null, error ->
                    plugin.getLogger().warning("Failed to delete voice channel: " + error.getMessage())
                );
            }
        });
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        plugin.getLogger().info("Discord gateway ready.");
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }

        if (event.isFromGuild()) {
            if (!config.isChatEnabled() || config.getChatChannelId() == 0L) {
                return;
            }
            if (event.getChannel().getIdLong() != config.getChatChannelId()) {
                return;
            }
            relayDiscordChatToMinecraft(event.getMember() != null ? event.getMember().getEffectiveName() : event.getAuthor().getName(), event.getMessage().getContentStripped());
            return;
        }

        handleDirectMessage(event);
    }

    private void handleDirectMessage(MessageReceivedEvent event) {
        String content = event.getMessage().getContentStripped().replaceAll("\\s+", "");
        if (content.isEmpty()) {
            event.getChannel().sendMessage("Send your numeric link code to connect your Discord account to Minecraft.").queue();
            return;
        }

        Optional<LinkManager.PendingLink> pending = linkManager.peekCode(content);
        if (pending.isEmpty()) {
            event.getChannel().sendMessage("That link code is invalid or expired. Join the Minecraft server again to get a new code.").queue();
            return;
        }

        long discordId = event.getAuthor().getIdLong();

        if (linkManager.getMinecraftForDiscord(discordId).isPresent()) {
            event.getChannel().sendMessage("Your Discord account is already linked to a Minecraft account. Use `/mcdiscord unlink` in-game first.").queue();
            return;
        }

        LinkManager.PendingLink link = linkManager.consumeCode(content).orElse(pending.get());

        linkManager.completeLink(link, discordId, event.getAuthor().getName());
        assignVerifiedRole(discordId);

        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("Account Linked")
            .setColor(Color.GREEN)
            .setDescription("Your Discord account is now linked to **" + link.minecraftName() + "**. You can join the server!")
            .addField("Minecraft UUID", link.minecraftUuid().toString(), false);
        event.getChannel().sendMessageEmbeds(embed.build()).queue();

        scheduler.runGlobal(() -> {
            Player online = Bukkit.getPlayer(link.minecraftUuid());
            if (online != null) {
                online.sendMessage(config.colorize("&aYour Discord account was linked successfully!"));
            }
        });
    }

    private void updateActivity() {
        getJda().ifPresent(jdaInstance -> {
            int online = Bukkit.getOnlinePlayers().size();
            int max = Bukkit.getMaxPlayers();
            String activity = config.getActivity()
                .replace("%online%", String.valueOf(online))
                .replace("%max%", String.valueOf(max));
            jdaInstance.getPresence().setPresence(OnlineStatus.ONLINE, net.dv8tion.jda.api.entities.Activity.playing(activity));
        });
    }

    private static EnumSet<GatewayIntent> getIntents() {
        return EnumSet.of(
            GatewayIntent.GUILD_MESSAGES,
            GatewayIntent.GUILD_MEMBERS,
            GatewayIntent.GUILD_VOICE_STATES,
            GatewayIntent.MESSAGE_CONTENT,
            GatewayIntent.DIRECT_MESSAGES
        );
    }
}
