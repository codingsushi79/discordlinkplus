package com.mcdiscordbot.linking;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mcdiscordbot.config.PluginConfig;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LinkStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, StoredLink>>() {}.getType();

    private final JavaPlugin plugin;
    private final PluginConfig config;
    private final Map<UUID, StoredLink> linksByMinecraft = new ConcurrentHashMap<>();
    private final Map<Long, UUID> minecraftByDiscord = new ConcurrentHashMap<>();

    public LinkStorage(JavaPlugin plugin, PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void load() {
        linksByMinecraft.clear();
        minecraftByDiscord.clear();
        Path path = getFilePath();
        if (!Files.exists(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            Map<String, StoredLink> raw = GSON.fromJson(reader, MAP_TYPE);
            if (raw == null) {
                return;
            }
            for (Map.Entry<String, StoredLink> entry : raw.entrySet()) {
                UUID uuid = UUID.fromString(entry.getKey());
                StoredLink link = entry.getValue();
                linksByMinecraft.put(uuid, link);
                minecraftByDiscord.put(link.discordId(), uuid);
            }
        } catch (IOException | IllegalArgumentException exception) {
            plugin.getLogger().warning("Failed to load links: " + exception.getMessage());
        }
    }

    public void save() {
        Path path = getFilePath();
        try {
            Files.createDirectories(path.getParent());
            Map<String, StoredLink> raw = new ConcurrentHashMap<>();
            linksByMinecraft.forEach((uuid, link) -> raw.put(uuid.toString(), link));
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(raw, writer);
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save links: " + exception.getMessage());
        }
    }

    public Optional<StoredLink> getByMinecraft(UUID uuid) {
        return Optional.ofNullable(linksByMinecraft.get(uuid));
    }

    public Optional<UUID> getMinecraftByDiscord(long discordId) {
        return Optional.ofNullable(minecraftByDiscord.get(discordId));
    }

    public boolean isLinked(UUID minecraftUuid) {
        return linksByMinecraft.containsKey(minecraftUuid);
    }

    public void link(UUID minecraftUuid, String minecraftName, long discordId, String discordName) {
        StoredLink link = new StoredLink(minecraftUuid, minecraftName, discordId, discordName, System.currentTimeMillis());
        linksByMinecraft.put(minecraftUuid, link);
        minecraftByDiscord.put(discordId, minecraftUuid);
        save();
    }

    public void unlink(UUID minecraftUuid) {
        StoredLink removed = linksByMinecraft.remove(minecraftUuid);
        if (removed != null) {
            minecraftByDiscord.remove(removed.discordId());
            save();
        }
    }

    public void unlinkDiscord(long discordId) {
        UUID minecraftUuid = minecraftByDiscord.remove(discordId);
        if (minecraftUuid != null) {
            linksByMinecraft.remove(minecraftUuid);
            save();
        }
    }

    private Path getFilePath() {
        return plugin.getDataFolder().toPath().resolve(config.getLinksFileName());
    }

    public record StoredLink(UUID minecraftUuid, String minecraftName, long discordId, String discordName, long linkedAt) {
    }
}
