package com.jbac.cosmetics.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** MiniMessage helpers. */
public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {}

    public static Component mm(String s, TagResolver... resolvers) {
        return MM.deserialize(s, resolvers);
    }

    /** For item names and lore: Minecraft italicises custom item text by default, which config authors never want. */
    public static Component item(String s, TagResolver... resolvers) {
        return MM.deserialize(s, resolvers).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }
}
