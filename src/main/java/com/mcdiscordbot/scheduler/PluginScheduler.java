package com.mcdiscordbot.scheduler;

import com.cjcrafter.foliascheduler.FoliaCompatibility;
import com.cjcrafter.foliascheduler.ServerImplementation;
import com.cjcrafter.foliascheduler.TaskImplementation;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;

public final class PluginScheduler {

    private final ServerImplementation server;

    public PluginScheduler(JavaPlugin plugin) {
        this.server = new FoliaCompatibility(plugin).getServerImplementation();
    }

    public TaskImplementation<Void> runGlobal(Runnable task) {
        return server.global().run(task);
    }

    public TaskImplementation<Void> runGlobalLater(Runnable task, long delayTicks) {
        return server.global().runDelayed(task, delayTicks);
    }

    public TaskImplementation<Void> runGlobalRepeating(Runnable task, long delayTicks, long periodTicks) {
        return server.global().runAtFixedRate(task, delayTicks, periodTicks);
    }

    public TaskImplementation<Void> runAsync(Runnable task) {
        return server.async().runNow(task);
    }

    public TaskImplementation<Void> runAsyncLater(Runnable task, long delay, TimeUnit unit) {
        return server.async().runDelayed(task, delay, unit);
    }

    public TaskImplementation<Void> runAtLocation(Location location, Runnable task) {
        return server.region(location).run(task);
    }

    public TaskImplementation<Void> runAtEntity(Entity entity, Runnable task) {
        return server.entity(entity).run(task);
    }
}
