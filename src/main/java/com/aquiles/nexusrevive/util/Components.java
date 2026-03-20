package com.aquiles.nexusrevive.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Components {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private Components() {
    }

    public static Component colorize(String text) {
        return LEGACY.deserialize(expandHex(text == null ? "" : text));
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    public static String legacy(Component component) {
        return LEGACY.serialize(component == null ? Component.empty() : component);
    }

    private static String expandHex(String text) {
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            String replacement = "&x"
                    + "&" + hex.charAt(0)
                    + "&" + hex.charAt(1)
                    + "&" + hex.charAt(2)
                    + "&" + hex.charAt(3)
                    + "&" + hex.charAt(4)
                    + "&" + hex.charAt(5);
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }
}

