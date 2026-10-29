package com.htk.core.common;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** Exercise the actual delivery boundary without starting a Minecraft server. */
public final class MessagesTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    private static <T extends CommandSender> T recipient(Class<T> type, String name, List<Component> received) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getName().equals("getName")) return name;
            if (method.getName().equals("sendMessage")) { received.add((Component)args[0]); return null; }
            if (method.getName().equals("toString")) return name;
            throw new AssertionError("Unexpected recipient call: " + method.getName());
        }));
    }
    public static void main(String[] args) {
        List<Component> aliceInbox=new ArrayList<>(), bobInbox=new ArrayList<>(), consoleInbox=new ArrayList<>();
        List<String> log=new ArrayList<>();
        Player alice=recipient(Player.class,"Alice",aliceInbox), bob=recipient(Player.class,"Bob",bobInbox);
        Component dialogue=Component.text("잊혀진 명장 » 반갑네.", NamedTextColor.GOLD)
                .clickEvent(ClickEvent.runCommand("/menu"));
        Messages.send(alice,dialogue,log::add);
        check(aliceInbox.equals(List.of(dialogue)),"original styled component reaches Alice exactly once");
        check(bobInbox.isEmpty(),"a private NPC message does not reach another player");
        check(log.equals(List.of("[ServerCore → Alice] 잊혀진 명장 » 반갑네.")),"log has recipient and plain text without click metadata");
        Messages.send(bob,Component.text("첫 줄\r\n둘째 줄\n셋째 줄"),log::add);
        check(bobInbox.size()==1 && aliceInbox.size()==1,"each player's notice stays in their own inbox");
        check(log.subList(1,4).equals(List.of("[ServerCore → Bob] 첫 줄","[ServerCore → Bob] 둘째 줄","[ServerCore → Bob] 셋째 줄")),
                "every multiline log entry retains its recipient");
        CommandSender console=recipient(CommandSender.class,"CONSOLE",consoleInbox);
        Messages.send(console,dialogue,log::add);
        check(consoleInbox.equals(List.of(dialogue)) && log.size()==4,"console response is sent once without a second log copy");
        System.out.println("Private message regression checks passed: "+checks);
    }
}
