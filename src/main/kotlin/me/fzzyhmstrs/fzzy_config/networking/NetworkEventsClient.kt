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
import io.netty.buffer.Unpooled
import me.fzzyhmstrs.fzzy_config.FC
import me.fzzyhmstrs.fzzy_config.FCC
import me.fzzyhmstrs.fzzy_config.impl.ConfigApiImplClient
import me.fzzyhmstrs.fzzy_config.impl.ValidScopesArgumentType
import me.fzzyhmstrs.fzzy_config.impl.ValidSubScopesArgumentType
import me.fzzyhmstrs.fzzy_config.networking.api.ClientPlayNetworkContext
import me.fzzyhmstrs.fzzy_config.registry.ClientConfigRegistry
import me.fzzyhmstrs.fzzy_config.util.FcText.translate
import net.minecraft.client.MinecraftClient
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.util.Identifier
import net.minecraftforge.client.event.RegisterClientCommandsEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.TickEvent
import net.minecraftforge.network.NetworkEvent
import net.minecraftforge.network.PacketDistributor
import java.util.*
import java.util.function.Supplier

internal object NetworkEventsClient {

    fun canSend(id: Identifier): Boolean {
        val handler = MinecraftClient.getInstance().networkHandler ?: return false
        return handler.isConnectionOpen
    }

    fun send(packet: Packet<*>) {
        PacketDistributor.SERVER.noArg().send(packet)
    }

    fun forwardSetting(update: String, player: UUID, scope: String, summary: String) {
        if (!canSend(SettingForwardCustomPayload.id)) {
            MinecraftClient.getInstance().player?.sendMessage("fc.config.forwarded_error.c2s".translate())
            FC.LOGGER.error("Can't forward setting; not connected to a server or server isn't accepting this type of data")
            FC.LOGGER.error("Setting not sent:")
            FC.LOGGER.warn(scope)
            FC.LOGGER.warn(summary)
            return
        }
        val payload = SettingForwardCustomPayload(update, player, scope, summary)
        val buf = PacketByteBuf(Unpooled.buffer())
        payload.write(buf)
        send(CustomPayloadC2SPacket(payload.getId(), buf))
    }

    fun updateServer(serializedConfigs: Map<String, String>, changeHistory: List<String>, playerPerm: Int) {
        if (!canSend(ConfigUpdateC2SCustomPayload.id)) {
            FC.LOGGER.error("Can't send Config Update; not connected to a server or server isn't accepting this type of data")
            FC.LOGGER.error("changes not sent:")
            for (change in changeHistory) {
                FC.LOGGER.warn(change)
            }
            return
        }
        val payload = ConfigUpdateC2SCustomPayload(serializedConfigs, changeHistory, playerPerm)
        val buf = PacketByteBuf(Unpooled.buffer())
        payload.write(buf)
        send(CustomPayloadC2SPacket(payload.getId(), buf))
    }

    fun receiveSync(payload: ConfigSyncS2CCustomPayload, context: ClientPlayNetworkContext) {
        ClientConfigRegistry.receiveSync(
            payload.id,
            payload.serializedConfig
        ) { text -> context.disconnect(text) }
    }

    fun receivePerms(payload: ConfigPermissionsS2CCustomPayload, context: ClientPlayNetworkContext) {
        ClientConfigRegistry.receivePerms(payload.id, payload.permissions)
    }

    fun receiveUpdate(payload: ConfigUpdateS2CCustomPayload, context: ClientPlayNetworkContext) {
        ClientConfigRegistry.receiveUpdate(payload.updates, context.player())
    }

    fun receiveForward(payload: SettingForwardCustomPayload, context: ClientPlayNetworkContext) {
        ClientConfigRegistry.handleForwardedUpdate(payload.update, payload.player, payload.scope, payload.summary)
    }

    fun handleConfigurationConfigSync(payload: ConfigSyncS2CCustomPayload, context: Supplier<NetworkEvent.Context>) {
        ClientConfigRegistry.receiveSync(
            payload.id,
            payload.serializedConfig
        ) { text -> context.get().networkManager.disconnect(text) }
        context.get().packetHandled = true
    }

    fun handleReloadConfigSync(payload: ConfigReloadSyncS2CCustomPayload, context: Supplier<NetworkEvent.Context>) {
        val player = MinecraftClient.getInstance().player
        if (player == null) {
            context.get().packetHandled = true
            return
        }
        ClientConfigRegistry.receiveReloadSync(
            payload.id,
            payload.serializedConfig,
            player
        )
        context.get().packetHandled = true
    }

    fun handlePermsUpdate(payload: ConfigPermissionsS2CCustomPayload, context: Supplier<NetworkEvent.Context>) {
        ClientConfigRegistry.receivePerms(payload.id, payload.permissions)
        context.get().packetHandled = true
    }

    fun handleUpdate(payload: ConfigUpdateS2CCustomPayload, context: Supplier<NetworkEvent.Context>) {
        val player = MinecraftClient.getInstance().player
        if (player == null) {
            context.get().packetHandled = true
            return
        }
        ClientConfigRegistry.receiveUpdate(payload.updates, player)
        context.get().packetHandled = true
    }

    fun handleSettingForward(payload: SettingForwardCustomPayload, context: Supplier<NetworkEvent.Context>) {
        ClientConfigRegistry.handleForwardedUpdate(payload.update, payload.player, payload.scope, payload.summary)
    }

    fun handleTick(event: TickEvent.ClientTickEvent) {
        if (event.phase == TickEvent.Phase.START) return
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

    fun registerClient() {
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands)
        MinecraftForge.EVENT_BUS.addListener(this::handleTick)
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