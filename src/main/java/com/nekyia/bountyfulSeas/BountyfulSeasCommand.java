package com.nekyia.bountyfulSeas;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The {@code /bs} command, and the only one this plugin registers.
 *
 * <p>Everything lives under one name on purpose. A plugin that claims {@code /debug}
 * is claiming a word half the server's plugins also want, and whoever loads first
 * wins it.
 */
final class BountyfulSeasCommand implements CommandExecutor, TabCompleter {

    private static final String DEBUG = "debug";
    private static final String DEBUG_PERMISSION = "bountyfulseas.debug";

    private static final String REGENERATE = "watermapregenerate";
    private static final String REGENERATE_PERMISSION = "bountyfulseas.watermapregenerate";

    private static final List<String> SUBCOMMANDS = List.of(DEBUG, REGENERATE);

    private final DebugCommand debug;
    private final Consumer<CommandSender> regenerate;

    BountyfulSeasCommand(DebugCommand debug, Consumer<CommandSender> regenerate) {
        this.debug = debug;
        this.regenerate = regenerate;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(usage());
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals(DEBUG)) {
            if (denied(sender, DEBUG_PERMISSION)) {
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only a player has a fishing line.", NamedTextColor.RED));
                return true;
            }
            debug.run(player);
            return true;
        }

        if (sub.equals(REGENERATE)) {
            if (denied(sender, REGENERATE_PERMISSION)) {
                return true;
            }
            // Runs the world scan off-thread and reports back when it lands.
            regenerate.accept(sender);
            return true;
        }

        sender.sendMessage(Component.text("Unknown subcommand: " + args[0], NamedTextColor.RED));
        sender.sendMessage(usage());
        return true;
    }

    private static boolean denied(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return false;
        }
        sender.sendMessage(Component.text("You may not use that.", NamedTextColor.RED));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String typed = args[0].toLowerCase(Locale.ROOT);
        return SUBCOMMANDS.stream()
                .filter(name -> name.startsWith(typed))
                .filter(name -> sender.hasPermission(name.equals(DEBUG)
                        ? DEBUG_PERMISSION : REGENERATE_PERMISSION))
                .toList();
    }

    private static Component usage() {
        return Component.text("/bs debug", NamedTextColor.GRAY)
                .append(Component.text("  -  what can be caught where your bobber is",
                        NamedTextColor.DARK_GRAY))
                .appendNewline()
                .append(Component.text("/bs watermapregenerate", NamedTextColor.GRAY))
                .append(Component.text("  -  rescan the world and redraw the map",
                        NamedTextColor.DARK_GRAY));
    }
}
