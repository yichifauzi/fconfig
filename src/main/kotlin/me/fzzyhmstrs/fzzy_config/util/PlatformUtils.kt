/*
* Copyright (c) 2024 Fzzyhmstrs
*
* This file is part of Fzzy Config, a mod made for minecraft; as such it falls under the license of Fzzy Config.
*
* Fzzy Config is free software provided under the terms of the Timefall Development License - Modified (TDL-M).
* You should have received a copy of the TDL-M with this software.
* If you did not, see <https://github.com/fzzyhmstrs/Timefall-Development-Licence-Modified>.
* */

package me.fzzyhmstrs.fzzy_config.util

import com.mojang.brigadier.CommandDispatcher
import me.fzzyhmstrs.fzzy_config.FC
import me.fzzyhmstrs.fzzy_config.impl.QuarantinedUpdatesArgumentType
import me.fzzyhmstrs.fzzy_config.registry.ClientConfigRegistry
import me.fzzyhmstrs.fzzy_config.registry.SyncedConfigRegistry
import me.fzzyhmstrs.fzzy_config.util.FcText.translate
import net.minecraft.client.gui.screen.Screen
import net.minecraft.command.argument.serialize.ConstantArgumentSerializer
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.ModContainer
import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.server.permission.PermissionAPI
import java.io.File
import java.util.function.BiFunction
import java.util.function.Supplier

internal object PlatformUtils {

    fun isClient(): Boolean {
        return FMLEnvironment.dist == Dist.CLIENT
    }

    fun configDir(): File {
        return FMLPaths.CONFIGDIR.get().toFile()
    }

    fun configName(scope: String, fallback: String): String {
        return ModList.get().getModContainerById(scope).map { it.modInfo.displayName }.orElse(fallback)
    } //ConfigScreenManager

    //only one entry allowed
    fun customScopes(): List<String> {
        val list: MutableList<String> = mutableListOf()
        for (container in ModList.get().mods) {
            val customValue = container.modProperties.get("fzzy_config")
            val test = container.modProperties.get("fzzy_test")
            println(test)
            println(test?.javaClass)
            if (customValue !is String) continue
            list.add(customValue)
        }
        return list
    } //ClientConfigRegistry

    fun hasPermission(player: ServerPlayerEntity, permission: String): Boolean {
        val node = PermissionAPI.getRegisteredNodes().firstOrNull { it.nodeName == permission } ?: return false
        return PermissionAPI.getPermission(player, node) == true
    } //COnfigApiImpl, elsewhere??

    fun registerCommands() {
        NeoForge.EVENT_BUS.addListener(this::registerCommands)
        val COMMAND_ARGUMENT_TYPES = DeferredRegister.create(RegistryKeys.COMMAND_ARGUMENT_TYPE, FC.MOD_ID)
        COMMAND_ARGUMENT_TYPES.register("quarantined_updates", Supplier { ConstantArgumentSerializer.of { _ -> QuarantinedUpdatesArgumentType() }  })
    }

    private fun registerCommands(event: RegisterCommandsEvent) {
        registerCommands(event.dispatcher)
    }

    private fun registerCommands(dispatcher: CommandDispatcher<ServerCommandSource>) {

        dispatcher.register(
            CommandManager.literal("configure_update")
                .requires { source -> source.hasPermissionLevel(3) }
                .then(CommandManager.argument("id", QuarantinedUpdatesArgumentType())
                    .then(
                        CommandManager.literal("inspect")
                        .executes { context ->
                            val id = QuarantinedUpdatesArgumentType.getQuarantineId(context, "id")
                            if (id == null) {
                                context.source.sendError("fc.command.error.no_id".translate())
                                return@executes 0
                            }
                            SyncedConfigRegistry.inspectQuarantine(id, { uuid -> context.source.server.playerManager.getPlayer(uuid)?.name }, { message -> context.source.sendMessage(message) })
                            1
                        }
                    )
                    .then(
                        CommandManager.literal("accept")
                        .executes { context ->
                            val id = QuarantinedUpdatesArgumentType.getQuarantineId(context, "id")
                            if (id == null) {
                                context.source.sendError("fc.command.error.no_id".translate())
                                return@executes 0
                            }
                            SyncedConfigRegistry.acceptQuarantine(id, context.source.server)
                            context.source.sendFeedback({ "fc.command.accepted".translate(id) }, true)
                            1
                        }
                    )
                    .then(
                        CommandManager.literal("reject")
                        .executes { context ->
                            val id = QuarantinedUpdatesArgumentType.getQuarantineId(context, "id")
                            if (id == null) {
                                context.source.sendError("fc.command.error.no_id".translate())
                                return@executes 0
                            }
                            SyncedConfigRegistry.rejectQuarantine(id, context.source.server)
                            context.source.sendFeedback({ "fc.command.rejected".translate(id) }, true)
                            1
                        }
                    )
                )
        )
    }
}