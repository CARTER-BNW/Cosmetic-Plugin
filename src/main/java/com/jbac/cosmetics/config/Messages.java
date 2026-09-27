package com.jbac.cosmetics.config;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.slf4j.Logger;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** messages.yml as MiniMessage components. Missing keys fall back to the bundled defaults, then to a visible marker. */
public final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Map<String, String> strings;
    private final String prefix;

    private Messages(Map<String, String> strings) {
        this.strings = strings;
        this.prefix = strings.getOrDefault("prefix", "");
    }

    public static Messages load(File file, InputStream bundledDefaults, Logger log) {
        Map<String, String> map = new HashMap<>();
        if (bundledDefaults != null) {
            YamlConfiguration def = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(bundledDefaults, StandardCharsets.UTF_8));
            copyStrings(def, map);
        }
        if (file.exists()) {
            copyStrings(YamlConfiguration.loadConfiguration(file), map);
        } else {
            log.warn("{} not found; using bundled messages", file);
        }
        return new Messages(map);
    }

    private static void copyStrings(YamlConfiguration yaml, Map<String, String> into) {
        for (String key : yaml.getKeys(true)) {
            if (yaml.isString(key)) {
                into.put(key, yaml.getString(key));
            }
        }
    }

    /** Body only, no prefix. For lore, GUI titles and item names. */
    public Component raw(String key, TagResolver... resolvers) {
        String s = strings.get(key);
        if (s == null) {
            return Component.text("<missing message: " + key + ">");
        }
        return MM.deserialize(s, resolvers);
    }

    /** Prefix + body. For chat messages. */
    public Component get(String key, TagResolver... resolvers) {
        return MM.deserialize(prefix, resolvers).append(raw(key, resolvers));
    }

    public void send(Audience to, String key, TagResolver... resolvers) {
        to.sendMessage(get(key, resolvers));
    }

    public String string(String key) {
        return strings.getOrDefault(key, key);
    }
}
