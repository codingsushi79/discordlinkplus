# DiscordLink+

A **Paper/Bukkit/Folia-compatible** Minecraft plugin that bridges your server with Discord — similar to [DiscordSRV](https://github.com/DiscordSRV/DiscordSRV) — with account linking and **Simple Voice Chat** group bridging.

## Features

### Discord chat bridge
- Minecraft chat → Discord text channel
- Discord messages → Minecraft chat
- Optional join/leave and death notifications

### Discord account linking (optional gate)
1. Player tries to join the server
2. If linking is enabled and they are not linked, they are blocked with a **numeric code**
3. They DM that code to the Discord bot
4. The bot links their Discord account to their Minecraft UUID, saves it, and assigns the configurable **Verified** role

### Simple Voice Chat ↔ Discord voice bridge
- Any **public** Simple Voice Chat group (no password) gets a matching Discord voice channel
- In-game players in that group are heard on Discord
- Discord users in the bridged channel are heard in-game by group members
- Empty groups can auto-delete their Discord channels (configurable)

## Requirements

- **Paper**, **Spigot**, or **Folia** server on **Minecraft 26.1.2** (Java 25 recommended for Discord voice/DAVE)
- [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) plugin (for voice bridging)
- A Discord bot with these intents enabled in the [Developer Portal](https://discord.com/developers/applications):
  - Message Content
  - Server Members
  - Direct Messages

## Build

```bash
./gradlew shadowJar
```

Output: `build/libs/DiscordLinkPlus-1.0.0.jar`

## CI/CD (GitHub Actions)

### Pull requests and pushes

The **Build** workflow (`.github/workflows/build.yml`) runs on pushes and PRs to `main`/`master`, compiles the plugin, and uploads the JAR as a workflow artifact.

### Releases and Modrinth publishing

The **Release** workflow (`.github/workflows/release.yml`) runs when `pluginVersion` in `gradle.properties` is pushed to `main`/`master`, or when triggered manually from **Actions → Release → Run workflow**. It will:

1. Read the version from `gradle.properties` (or use the manual override)
2. Create and push git tag `v<version>` (e.g. `v1.0.1`)
3. Build the shadow JAR
4. Create a GitHub Release with auto-generated notes and attach the JAR
5. Publish to Modrinth

If tag `v<version>` already exists, the workflow skips (safe for re-pushes).

#### One-time setup

1. Create a Modrinth project for this plugin
2. Generate a [Modrinth PAT](https://modrinth.com/settings/pats) with **Create versions** scope
3. Add GitHub repository secret **`MODRINTH_TOKEN`**

The release workflow publishes to the Modrinth project slug [`discordlink+`](https://modrinth.com/project/discordlink+).

#### Cutting a release

1. Bump the version in `gradle.properties`:
   ```properties
   pluginVersion=1.0.1
   ```
2. Commit and push to `main`:
   ```bash
   git add gradle.properties
   git commit -m "Release 1.0.1"
   git push origin main
   ```

GitHub Actions creates `v1.0.1`, the GitHub Release, and the Modrinth version automatically.

You can also run the workflow manually from the Actions tab if you need to re-trigger without changing `gradle.properties`.

## Setup

1. Create a Discord application/bot and copy the token
2. Invite the bot to your guild with permissions to:
   - Read/send messages in your chat channel
   - Manage roles (for Verified)
   - Manage channels + Connect/Speak (for voice bridging)
3. Copy IDs (enable Developer Mode in Discord → right-click → Copy ID):
   - Guild ID
   - Chat channel ID
   - Verified role ID
   - Voice category ID (recommended)
4. Place the JAR in `plugins/` and start the server
5. Edit `plugins/DiscordLink+/config.yml`
6. Run `/mcdiscord reload`

### Linking flow (players)
1. Join the server → receive kick message with code (if not linked)
2. DM the code to the bot on Discord
3. Rejoin the server

In-game commands:
- `/mcdiscord link` — generate a link code without reconnecting
- `/mcdiscord unlink` — remove your link
- `/mcdiscord status` — check link status

Ops bypass the link requirement by default.

### Voice bridge
1. Install **Simple Voice Chat** on the server (and clients)
2. Open UDP port **24454** (or your configured SVC port)
3. Set `voice-bridge.server-host` / `server-port` in config for reference
4. Create or join a **public** SVC group in-game — a Discord VC named `VC <group name>` appears

## Folia support

This plugin sets `folia-supported: true` and uses [FoliaScheduler](https://github.com/CJCrafter/FoliaScheduler) for region-safe task scheduling.

## License

MIT
