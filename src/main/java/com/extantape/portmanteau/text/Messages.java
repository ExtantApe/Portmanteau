package com.extantape.portmanteau.text;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Player-facing text in translation files, {@code lang/<locale>.yml}, written in MiniMessage. A player gets their own
 * language's file when there is one, and English otherwise. Server owners edit the copies in the plugin folder.
 */
public final class Messages {
    private static final String FALLBACK = "en_us";
    private final JavaPlugin plugin;
    private final Map<String, YamlConfiguration> byLocale = new HashMap<>();
    private YamlConfiguration bundled;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        byLocale.clear();
        File folder = new File(plugin.getDataFolder(), "lang");
        if (!new File(folder, FALLBACK + ".yml").exists()) plugin.saveResource("lang/" + FALLBACK + ".yml", false);
        try (var in = plugin.getResource("lang/" + FALLBACK + ".yml")) {
            bundled = in == null ? new YamlConfiguration()
                    : YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            bundled = new YamlConfiguration();
        }
        File[] files = folder.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return;
        Logger log = plugin.getLogger();
        for (File f : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(f);
            byLocale.put(f.getName().replace(".yml", "").toLowerCase(Locale.ROOT), yaml);
            if (yaml.getKeys(true).isEmpty()) log.warning("lang/" + f.getName() + " is empty or couldn't be read; using English.");
        }
    }

    public Component get(Audience who, String key, TagResolver... tags) {
        String template = raw(who, key);
        return MiniMessage.miniMessage().deserialize(template, tags);
    }

    public String raw(Audience who, String key) {
        String locale = who instanceof Player p ? p.locale().toString().toLowerCase(Locale.ROOT) : FALLBACK;
        for (String tryLocale : new String[]{locale, locale.split("_")[0], FALLBACK}) {
            YamlConfiguration yaml = byLocale.get(tryLocale);
            if (yaml != null && yaml.isString(key)) return yaml.getString(key);
        }
        String text = bundled.getString(key);
        return text != null ? text : key;
    }

    public void send(Audience who, String key, TagResolver... tags) {
        who.sendMessage(get(who, key, tags));
    }

    public void actionBar(Audience who, String key, TagResolver... tags) {
        who.sendActionBar(get(who, key, tags));
    }
}
