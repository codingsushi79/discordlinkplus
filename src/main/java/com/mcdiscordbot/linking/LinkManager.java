package com.mcdiscordbot.linking;

import com.mcdiscordbot.config.PluginConfig;
import org.bukkit.plugin.java.JavaPlugin;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LinkManager {

    private final JavaPlugin plugin;
    private final PluginConfig config;
    private final LinkStorage storage;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, PendingLink> pendingByCode = new ConcurrentHashMap<>();
    private final Map<UUID, PendingLink> pendingByMinecraft = new ConcurrentHashMap<>();

    public LinkManager(JavaPlugin plugin, PluginConfig config, LinkStorage storage) {
        this.plugin = plugin;
        this.config = config;
        this.storage = storage;
    }

    public Optional<String> createPendingLink(UUID minecraftUuid, String minecraftName) {
        cleanupExpired();
        pendingByMinecraft.remove(minecraftUuid);

        String code = generateCode();
        PendingLink pending = new PendingLink(code, minecraftUuid, minecraftName, System.currentTimeMillis());
        pendingByCode.put(code, pending);
        pendingByMinecraft.put(minecraftUuid, pending);
        return Optional.of(code);
    }

    public Optional<PendingLink> peekCode(String rawCode) {
        cleanupExpired();
        return Optional.ofNullable(pendingByCode.get(normalizeCode(rawCode)));
    }

    public Optional<PendingLink> consumeCode(String rawCode) {
        cleanupExpired();
        String code = normalizeCode(rawCode);
        PendingLink pending = pendingByCode.remove(code);
        if (pending == null) {
            return Optional.empty();
        }
        pendingByMinecraft.remove(pending.minecraftUuid());
        if (pending.isExpired(config.getCodeExpirySeconds())) {
            return Optional.empty();
        }
        return Optional.of(pending);
    }

    public void completeLink(PendingLink pending, long discordId, String discordName) {
        storage.link(pending.minecraftUuid(), pending.minecraftName(), discordId, discordName);
    }

    public boolean isLinked(UUID minecraftUuid) {
        return storage.isLinked(minecraftUuid);
    }

    public Optional<LinkStorage.StoredLink> getLink(UUID minecraftUuid) {
        return storage.getByMinecraft(minecraftUuid);
    }

    public void unlinkMinecraft(UUID minecraftUuid) {
        storage.unlink(minecraftUuid);
    }

    public void unlinkDiscord(long discordId) {
        storage.unlinkDiscord(discordId);
    }

    public Optional<UUID> getMinecraftForDiscord(long discordId) {
        return storage.getMinecraftByDiscord(discordId);
    }

    private void cleanupExpired() {
        long expiryMillis = config.getCodeExpirySeconds() * 1000L;
        long now = System.currentTimeMillis();
        pendingByCode.entrySet().removeIf(entry -> {
            PendingLink pending = entry.getValue();
            if (now - pending.createdAt() > expiryMillis) {
                pendingByMinecraft.remove(pending.minecraftUuid());
                return true;
            }
            return false;
        });
    }

    private String generateCode() {
        int length = Math.max(4, config.getCodeLength());
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(random.nextInt(10));
        }
        String code = builder.toString();
        if (pendingByCode.containsKey(code)) {
            return generateCode();
        }
        return code;
    }

    private static String normalizeCode(String rawCode) {
        return rawCode == null ? "" : rawCode.replaceAll("\\s+", "");
    }

    public record PendingLink(String code, UUID minecraftUuid, String minecraftName, long createdAt) {
        public boolean isExpired(int expirySeconds) {
            return System.currentTimeMillis() - createdAt > expirySeconds * 1000L;
        }
    }
}
