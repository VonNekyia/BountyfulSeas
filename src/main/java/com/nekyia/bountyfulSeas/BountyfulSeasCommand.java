package com.nekyia.bountyfulSeas;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
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
final class BountyfulSeasCommand implements BasicCommand {

    private static final String DEBUG = "debug";
    private static final String DEBUG_PERMISSION = "bountyfulseas.debug";

    private static final String REGENERATE = "watermapregenerate";
    private static final String REGENERATE_PERMISSION = "bountyfulseas.watermapregenerate";

    private static final String GUIDE = "guide";
    private static final String GUIDE_PERMISSION = "bountyfulseas.guide";

    private static final List<String> SUBCOMMANDS = List.of(GUIDE, DEBUG, REGENERATE);

    private final DebugCommand debug;
    private final Consumer<CommandSender> regenerate;
    private final GuideCommand guide;

    BountyfulSeasCommand(DebugCommand debug, Consumer<CommandSender> regenerate, GuideCommand guide) {
        this.debug = debug;
        this.regenerate = regenerate;
        this.guide = guide;
    }

    @Override
    public void execute(@NotNull CommandSourceStack source, @NotNull String[] args) {
        CommandSender sender = source.getSender();
        if (args.length == 0) {
            sender.sendMessage(usage());
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals(GUIDE)) {
            if (denied(sender, GUIDE_PERMISSION)) {
                return;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only a player has a guide.", NamedTextColor.RED));
                return;
            }
            guide.run(player, args.length > 1 ? args[1] : null);
            return;
        }

        if (sub.equals(DEBUG)) {
            if (denied(sender, DEBUG_PERMISSION)) {
                return;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only a player has a fishing line.", NamedTextColor.RED));
                return;
            }
            debug.run(player, java.util.Arrays.copyOfRange(args, 1, args.length));
            return;
        }

        if (sub.equals(REGENERATE)) {
            if (denied(sender, REGENERATE_PERMISSION)) {
                return;
            }
            // Runs the world scan off-thread and reports back when it lands.
            regenerate.accept(sender);
            return;
        }

        sender.sendMessage(Component.text("Unknown subcommand: " + args[0], NamedTextColor.RED));
        sender.sendMessage(usage());
    }

    private static boolean denied(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return false;
        }
        sender.sendMessage(Component.text("You may not use that.", NamedTextColor.RED));
        return true;
    }

    @Override
    public Collection<String> suggest(@NotNull CommandSourceStack source, @NotNull String[] args) {
        CommandSender sender = source.getSender();
        if (args.length == 1) {
            String typed = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream()
                    .filter(name -> name.startsWith(typed))
                    .filter(name -> sender.hasPermission(permissionFor(name)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase(GUIDE) && sender.hasPermission(GUIDE_PERMISSION)) {
            String typed = args[1].toLowerCase(Locale.ROOT);
            return guide.categories().stream().filter(name -> name.startsWith(typed)).toList();
        }
        if (args[0].equalsIgnoreCase(DEBUG) && sender.hasPermission(DEBUG_PERMISSION)) {
            return debug.suggest(java.util.Arrays.copyOfRange(args, 1, args.length));
        }
        return List.of();
    }

    private static String permissionFor(String subcommand) {
        return switch (subcommand) {
            case GUIDE -> GUIDE_PERMISSION;
            case DEBUG -> DEBUG_PERMISSION;
            default -> REGENERATE_PERMISSION;
        };
    }

    private static Component usage() {
        return line("/bs guide", "your fishing guide and records")
                .appendNewline()
                .append(line("/bs debug", "what can be caught where your bobber is"))
                .appendNewline()
                .append(line("/bs watermapregenerate", "rescan the world and redraw the map"));
    }

    private static Component line(String command, String description) {
        return Component.text(command, NamedTextColor.GRAY)
                .append(Component.text("  -  " + description, NamedTextColor.DARK_GRAY));
    }
}
