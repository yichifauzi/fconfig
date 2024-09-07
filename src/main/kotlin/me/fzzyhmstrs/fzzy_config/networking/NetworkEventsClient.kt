/*
 * Copyright (c) 2024 Fzzyhmstrs
 *
 * This file is part of Fzzy Config, a mod made for minecraft; as such it falls under the license of Fzzy Config.
 *
 * Fzzy Config is free software provided under the terms of the Timefall Development License - Modified (TDL-M).
 * You should have received a copy of the TDL-M with this software.
 * If you did not, see <https://github.com/fzzyhmstrs/Timefall-Development-Licence-Modified>.
 */

package me.fzzyhmstrs.fzzy_config.networking

import com.mojang.brigadier.CommandDispatcher
import me.fzzyhmstrs.fzzy_config.FC
import me.fzzyhmstrs.fzzy_config.FCC
import me.fzzyhmstrs.fzzy_config.impl.ConfigApiImplClient
import me.fzzyhmstrs.fzzy_config.impl.ValidScopesArgumentType
import me.fzzyhmstrs.fzzy_config.impl.ValidSubScopesArgumentType
import me.fzzyhmstrs.fzzy_config.registry.ClientConfigRegistry
import me.fzzyhmstrs.fzzy_config.util.FcText.translate
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.CustomPayload
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.neoforged.fml.ModList
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent
import net.neoforged.neoforge.client.gui.IConfigScreenFactory
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext
import net.neoforged.neoforge.network.registration.NetworkRegistry
import java.util.*
import java.util.function.Supplier

internal object NetworkEventsClient {

    fun canSend(id: CustomPayload.Id<*>): Boolean {
        val handler = MinecraftClient.getInstance().networkHandler ?: return false
        return NetworkRegistry.hasChannel(handler, id.id())
    }

    fun send(payload: CustomPayload) {
        PacketDistributor.sendToServer(payload)
    }

    fun forwardSetting(update: String, player: UUID, scope: String, summary: String) {
        if (!canSend(SettingForwardCustomPayload.type)) {
            MinecraftClient.getInstance().player?.sendMessage("fc.config.forwarded_error.c2s".translate())
            FC.LOGGER.error("Can't forward setting; not connected to a server or server isn't accepting this type of data")
            FC.LOGGER.error("Setting not sent:")
            FC.LOGGER.warn(scope)
            FC.LOGGER.warn(summary)
            return
        }
        send(SettingForwardCustomPayload(update, player, scope, summary))
    }

    fun updateServer(serializedConfigs: Map<String, String>, changeHistory: List<String>, playerPerm: Int) {
        if (!canSend(ConfigUpdateC2SCustomPayload.type)) {
            FC.LOGGER.error("Can't send Config Update; not connected to a server or server isn't accepting this type of data")
            FC.LOGGER.error("changes not sent:")
            for (change in changeHistory) {
                FC.LOGGER.warn(change)
            }
            return
        }
        send(ConfigUpdateC2SCustomPayload(serializedConfigs, changeHistory, playerPerm))
    }

    fun handleConfigurationConfigSync(payload: ConfigSyncS2CCustomPayload, context: IPayloadContext) {
        ClientConfigRegistry.receiveSync(
            payload.id,
            payload.serializedConfig
        ) { text -> context.disconnect(text) }
    }

    fun handleReloadConfigSync(payload: ConfigSyncS2CCustomPayload, context: IPayloadContext) {
        ClientConfigRegistry.receiveReloadSync(
            payload.id,
            payload.serializedConfig,
            context.player()
        )
    }

    fun handlePermsUpdate(payload: ConfigPermissionsS2CCustomPayload, context: IPayloadContext) {
        ClientConfigRegistry.receivePerms(payload.id, payload.permissions)
    }

    fun handleUpdate(payload: ConfigUpdateS2CCustomPayload, context: IPayloadContext) {
        ClientConfigRegistry.receiveUpdate(payload.updates, context.player())
    }

    fun handleSettingForward(payload: SettingForwardCustomPayload, context: IPayloadContext) {
        ClientConfigRegistry.handleForwardedUpdate(payload.update, payload.player, payload.scope, payload.summary)
    }

    private fun handleTick(event: ClientTickEvent.Post) {
        FCC.withScope { scopeToOpen ->
            if (scopeToOpen != "") {
                ClientConfigRegistry.openScreen(scopeToOpen)
            }
        }

        FCC.withRestart { openRestartScreen ->
            if (openRestartScreen) {
                ConfigApiImplClient.openRestartScreen()
            }
        }
    }

    private fun registerConfigs(event: ClientPlayerNetworkEvent.LoggingIn) {
        ModList.get().forEachModInOrder { modContainer ->
            val id = modContainer.modId
            if (ClientConfigRegistry.getScreenScopes().contains(id)) {
                if (modContainer.getCustomExtension(IConfigScreenFactory::class.java).isEmpty) {
                    modContainer.registerExtensionPoint(IConfigScreenFactory::class.java, Supplier {
                        IConfigScreenFactory { _, screen -> ClientConfigRegistry.provideScreen(id) ?: screen }
                    })
                }
            }
        }
    }

    fun registerClient() {
        NeoForge.EVENT_BUS.addListener(this::registerCommands)
        NeoForge.EVENT_BUS.addListener(this::registerConfigs)
        NeoForge.EVENT_BUS.addListener(this::handleTick)
    }

    private fun registerCommands(event: RegisterClientCommandsEvent) {
        registerClientCommands(event.dispatcher)
    }

    private fun registerClientCommands(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            CommandManager.literal("configure")
                .then(CommandManager.argument("base_scope", ValidScopesArgumentType())
                    .executes{ context ->
                        val scope = ValidScopesArgumentType.getValidScope(context, "base_scope")
                        FCC.openScopedScreen(scope ?: "")
                        1
                    }
                    .then(
                        CommandManager.argument("sub_scope", ValidSubScopesArgumentType())
                        .executes{ context ->
                            val scope = ValidScopesArgumentType.getValidScope(context, "base_scope")
                            val subScope = ValidSubScopesArgumentType.getValidSubScope(context, "sub_scope")
                            FCC.openScopedScreen(scope?.plus(subScope?.let { ".$it" } ?: "") ?: "")
                            1
                        }
                    )
                )
        )
    }
}