/*
 * This file is part of Nucleus, licensed under the MIT License (MIT). See the LICENSE.txt file
 * at the root of this project for more details.
 */
package io.github.nucleuspowered.nucleus.modules.world.commands;

import io.github.nucleuspowered.nucleus.argumentparsers.NucleusWorldPropertiesArgument;
import io.github.nucleuspowered.nucleus.internal.annotations.command.NoModifiers;
import io.github.nucleuspowered.nucleus.internal.annotations.command.Permissions;
import io.github.nucleuspowered.nucleus.internal.annotations.command.RegisterCommand;
import io.github.nucleuspowered.nucleus.internal.command.AbstractCommand;
import io.github.nucleuspowered.nucleus.internal.command.ReturnMessageException;
import io.github.nucleuspowered.nucleus.internal.messages.MessageProvider;
import io.github.nucleuspowered.nucleus.internal.permissions.SuggestedLevel;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.CommandElement;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.annotation.NonnullByDefault;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.storage.WorldProperties;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@NoModifiers
@NonnullByDefault
@Permissions(prefix = "world", suggestedLevel = SuggestedLevel.ADMIN)
@RegisterCommand(value = {"unload"}, subcommandOf = WorldCommand.class)
public class UnloadWorldCommand extends AbstractCommand<CommandSource> {

    private final String transferWorldKey = "transferWorld";
    private final String worldKey = "world";

    @Override
    public CommandElement[] getArguments() {
        return new CommandElement[] {
            GenericArguments.flags()
                .permissionFlag(plugin.getPermissionRegistry().getPermissionsForNucleusCommand(DisableWorldCommand.class).getBase(), "d", "-disable")
                .valueFlag(new NucleusWorldPropertiesArgument(Text.of(transferWorldKey), NucleusWorldPropertiesArgument.Type.ENABLED_ONLY), "t", "-transfer")
                .buildWith(GenericArguments.onlyOne(new NucleusWorldPropertiesArgument(Text.of(worldKey), NucleusWorldPropertiesArgument.Type.ENABLED_ONLY)))
        };
    }

    @Override
    public CommandResult executeCommand(CommandSource src, CommandContext args) throws Exception {
        WorldProperties worldProperties = args.<WorldProperties>getOne(worldKey).get();
        Optional<WorldProperties> transferWorld = args.getOne(transferWorldKey);
        boolean disable = args.hasAny("d");

        Optional<World> worldOptional = Sponge.getServer().getWorld(worldProperties.getUniqueId());
        if (!worldOptional.isPresent()) {
            // Not loaded.
            if (disable) {
                disable(worldProperties, src, plugin.getMessageProvider(), false);
            }

            throw new ReturnMessageException(plugin.getMessageProvider().getTextMessageWithFormat("command.world.unload.alreadyunloaded", worldProperties.getWorldName()));
        }

        World world = worldOptional.get();
        List<Player> playerCollection = Sponge.getServer().getOnlinePlayers().stream().filter(x -> x.getWorld().equals(world)).collect(Collectors.toList());

        if (playerCollection.isEmpty() || (transferWorld.isPresent() && transferWorld.get().isEnabled())) {
            if (!playerCollection.isEmpty()) {
                // Transfer World is present and enabled.
                playerCollection.forEach(x -> x.transferToWorld(transferWorld.get().getUniqueId(), transferWorld.get().getSpawnPosition().toDouble()));
                Sponge.getScheduler().createSyncExecutor(plugin).schedule(() -> unloadWorld(src, world, plugin.getMessageProvider(), disable), 40, TimeUnit.MILLISECONDS);

                // Well, this bit succeeded, at least.
                return CommandResult.success();
            } else if (unloadWorld(src, world, plugin.getMessageProvider(), disable)) {
                return CommandResult.success();
            } else {
                return CommandResult.empty();
            }
        }

        throw new ReturnMessageException(plugin.getMessageProvider().getTextMessageWithFormat("command.world.unload.players", worldProperties.getWorldName()));
    }

    private static void disable(WorldProperties worldProperties, CommandSource sender, MessageProvider provider, boolean messageOnError) {
        if (worldProperties.isEnabled()) {
            worldProperties.setEnabled(false);
            sender.sendMessage(provider.getTextMessageWithFormat("command.world.disable.success", worldProperties.getWorldName()));
        } else if (messageOnError) {
            sender.sendMessage(provider.getTextMessageWithFormat("command.world.disable.alreadydisabled", worldProperties.getWorldName()));
        }
    }

    private static boolean unloadWorld(CommandSource source, World world, MessageProvider provider, boolean disable) {
        WorldProperties worldProperties = world.getProperties();
        source.sendMessage(provider.getTextMessageWithFormat("command.world.unload.start", worldProperties.getWorldName()));
        boolean unloaded = Sponge.getServer().unloadWorld(world);
        if (unloaded) {
            source.sendMessage(provider.getTextMessageWithFormat("command.world.unload.success", worldProperties.getWorldName()));
            if (disable) {
                disable(worldProperties, source, provider, true);
            }

            return true;
        } else {
            source.sendMessage(provider.getTextMessageWithFormat("command.world.unload.failed", worldProperties.getWorldName()));
            return false;
        }
    }
}
