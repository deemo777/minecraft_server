package com.htk.core.common;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.function.Consumer;

/** Private plugin chat notices, mirrored to the server log with their recipient. */
public final class Messages {
    private Messages() {}

    public static void send(CommandSender recipient, String message) {
        send(recipient, Component.text(message));
    }

    public static void send(CommandSender recipient, Component message) {
        send(recipient, message, line -> Bukkit.getLogger().info(line));
    }

    static void send(CommandSender recipient, Component message, Consumer<String> log) {
        recipient.sendMessage(message);
        if (recipient instanceof Player) {
            String plain = PlainTextComponentSerializer.plainText().serialize(message);
            for (String line : plain.split("\\R", -1))
                log.accept("[ServerCore → " + recipient.getName() + "] " + line);
        }
    }
}
