package com.extantape.portmanteau.trunk;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * A plain, readable copy of each player's trunk per scope, written after every save, for admins and backups. It's never
 * read back: the player's own data is the truth. Files are named by UUID and scope, never by player name, and written
 * to a temporary file first and then renamed, so a reader never sees half a file. A failure here never blocks or undoes
 * the real save; admins are told. The format is documented in docs/trunk-copies.md.
 */
final class AdminCopies {
    static final int FORMAT = 1;
    private final Plugin plugin;
    private final Path folder;
    private final String serverId;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Portmanteau trunk copies");
        t.setDaemon(true);
        return t;
    });
    private volatile long lastWarning;

    AdminCopies(Plugin plugin, String serverId) {
        this.plugin = plugin;
        this.folder = plugin.getDataFolder().toPath().resolve("trunk-copies");
        this.serverId = serverId;
    }

    /** Builds the text on the main thread, where items may be read, and writes it in the background, in order. */
    void write(UUID owner, String scope, ItemStack[] contents) {
        String text = render(owner, scope, contents);
        Path file = folder.resolve(owner.toString()).resolve(safe(scope) + ".yml");
        writer.execute(() -> {
            try {
                Files.createDirectories(file.getParent());
                Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
                Files.writeString(tmp, text, StandardCharsets.UTF_8);
                try {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException | RuntimeException e) {
                warn(file, e);
            }
        });
    }

    private void warn(Path file, Exception e) {
        long now = System.currentTimeMillis();
        if (now - lastWarning < 600_000) return;
        lastWarning = now;
        String msg = "Couldn't write the admin copy of a multiversal trunk to " + file + " (" + e.getMessage()
                + "). The trunk itself saved normally; only the readable backup copy is behind. Check the disk and folder permissions.";
        plugin.getLogger().warning(msg);
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("portmanteau.admin"))
                .forEach(p -> p.sendMessage(net.kyori.adventure.text.Component.text("[Portmanteau] " + msg,
                        net.kyori.adventure.text.format.NamedTextColor.YELLOW))));
    }

    String render(UUID owner, String scope, ItemStack[] contents) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Portmanteau multiversal trunk, admin copy. Format ").append(FORMAT).append(", described in docs/trunk-copies.md.\n");
        sb.append("# A backup for admins. Portmanteau never reads this file; the player's own data holds the real contents.\n");
        sb.append("format: ").append(FORMAT).append('\n');
        sb.append("server-id: ").append(quote(serverId)).append('\n');
        sb.append("owner: ").append(quote(owner.toString())).append('\n');
        sb.append("scope: ").append(quote(scope)).append('\n');
        sb.append("saved-at: ").append(quote(Instant.now().toString())).append('\n');
        sb.append("# Every slot at once, as Paper's item bytes (ItemStack.deserializeItemsFromBytes), for an exact restore.\n");
        sb.append("items-base64: ").append(quote(Base64.getEncoder().encodeToString(TrunkStore.serialize(contents)))).append('\n');
        int header = sb.length();
        sb.append("slots:\n");
        boolean any = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.isEmpty()) continue;
            any = true;
            sb.append("  ").append(i).append(":\n");
            sb.append("    id: ").append(quote(item.getType().getKey().toString())).append('\n');
            sb.append("    count: ").append(item.getAmount()).append('\n');
            String components = item.hasItemMeta() ? item.getItemMeta().getAsComponentString() : "";
            if (!components.isEmpty() && !components.equals("[]")) sb.append("    components: ").append(quote(components)).append('\n');
        }
        if (!any) {
            sb.setLength(header);
            sb.append("slots: {}\n");
        }
        return sb.toString();
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    static String safe(String scope) {
        return scope.replaceAll("[^A-Za-z0-9._+-]", "_");
    }

    void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(10, TimeUnit.SECONDS)) plugin.getLogger().warning("Some trunk admin copies were still being written at shutdown.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static String serverId(Plugin plugin) {
        Path file = plugin.getDataFolder().toPath().resolve("server-id.txt");
        try {
            if (Files.exists(file)) {
                String id = Files.readString(file, StandardCharsets.UTF_8).strip();
                if (!id.isEmpty()) return id;
            }
            String id = UUID.randomUUID().toString();
            Files.createDirectories(file.getParent());
            Files.writeString(file, id + "\n", StandardCharsets.UTF_8);
            return id;
        } catch (IOException e) {
            plugin.getLogger().warning("Couldn't read or write server-id.txt: " + e.getMessage());
            return "unknown";
        }
    }
}
