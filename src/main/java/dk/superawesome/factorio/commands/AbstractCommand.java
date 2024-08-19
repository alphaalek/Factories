package dk.superawesome.factorio.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public abstract class AbstractCommand implements CommandExecutor {

    private static final Pattern UUID_REGEX = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    protected Optional<OfflinePlayer> getTarget(String argument) {
        return Optional.of(
                // check for username
                Optional.of(Bukkit.getOfflinePlayer(argument))
                        // if not present, check for uuid
                        .orElse(
                            Optional.of(argument)
                                .flatMap(s ->
                                    UUID_REGEX.matcher(s).matches() ?
                                        Optional.of(Bukkit.getOfflinePlayer(UUID.fromString(s))) :
                                        Optional.empty()
                                )
                                // still not present, no valid target found
                                .orElse(null)
                        )
        );
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cKun spillere kan bruge denne kommando!");
            return true;
        }

        execute(player, args);
        return true;
    }

    public abstract void execute(Player player, String[] args);
}
