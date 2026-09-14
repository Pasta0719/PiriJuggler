package jp.pirijuggler.paper.threading;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class PaperMainThread implements MainThread {
    private final JavaPlugin plugin;

    public PaperMainThread(JavaPlugin plugin) { this.plugin = plugin; }
    @Override public boolean isMainThread() { return Bukkit.isPrimaryThread(); }
    @Override public void execute(Runnable task) {
        if (isMainThread()) task.run();
        else Bukkit.getScheduler().runTask(plugin, task);
    }
}
