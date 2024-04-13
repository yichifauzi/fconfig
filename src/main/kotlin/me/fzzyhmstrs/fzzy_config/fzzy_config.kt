package me.fzzyhmstrs.fzzy_config

import com.mojang.brigadier.CommandDispatcher
import me.fzzyhmstrs.fzzy_config.impl.ValidScopesArgumentType
import me.fzzyhmstrs.fzzy_config.impl.ValidSubScopesArgumentType
import me.fzzyhmstrs.fzzy_config.registry.ClientConfigRegistry
import me.fzzyhmstrs.fzzy_config.registry.SyncedConfigRegistry
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.util.Identifier
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object FC: ModInitializer {
    internal const val MOD_ID = "fzzy_config"
    internal val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

    override fun onInitialize() {
        SyncedConfigRegistry.registerAll()
    }
}

object FCC: ClientModInitializer {

    private var scopeToOpen = ""

    override fun onInitializeClient() {
        SyncedConfigRegistry.registerClient()
        ClientCommandRegistrationCallback.EVENT.register{ dispatcher, _ ->
            registerClientCommands(dispatcher)
        }
        ClientTickEvents.START_CLIENT_TICK.register{ _ ->
            if (scopeToOpen != "") {
                ClientConfigRegistry.openScreen(scopeToOpen)
                scopeToOpen = ""
            }
        }
    }

    private fun registerClientCommands(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        dispatcher.register(
            ClientCommandManager.literal("configure")
                .then(ClientCommandManager.argument("base_scope",ValidScopesArgumentType())
                    .executes{ context ->
                        val scope = ValidScopesArgumentType.getValidScope(context, "base_scope")
                        scopeToOpen = scope ?: ""
                        1
                    }
                    .then(ClientCommandManager.argument("sub_scope",ValidSubScopesArgumentType())
                        .executes{ context ->
                            val scope = ValidScopesArgumentType.getValidScope(context, "base_scope")
                            val subScope = ValidSubScopesArgumentType.getValidSubScope(context, "sub_scope")
                            scopeToOpen = scope?.plus(subScope?.let { ".$it" } ?: "") ?: ""
                            1
                        }
                    )
                )
        )
    }
}

internal fun String.fcId(): Identifier {
    return Identifier(FC.MOD_ID,this)
}