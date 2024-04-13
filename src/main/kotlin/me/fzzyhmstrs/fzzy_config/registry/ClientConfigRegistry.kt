package me.fzzyhmstrs.fzzy_config.registry

import me.fzzyhmstrs.fzzy_config.FC
import me.fzzyhmstrs.fzzy_config.config.Config
import me.fzzyhmstrs.fzzy_config.impl.ConfigSet
import me.fzzyhmstrs.fzzy_config.screen.internal.ConfigScreenManager
import me.fzzyhmstrs.fzzy_config.updates.UpdateManager
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.metadata.CustomValue
import net.minecraft.client.gui.screen.Screen
import java.util.*

/**
 * Client registry for [Config] instances. Handles GUIs.
 *
 * This is not a "true" registry in the Minecraft since; as such there are not the typical helper methods like get(), getId(), etc. This registry's scope is much narrower, handling synchronization and updates of Configs.
 */
@Environment(EnvType.CLIENT)
internal object ClientConfigRegistry {

    private val clientConfigs : MutableMap<String, ConfigPair> = mutableMapOf()
    private val configScreenManagers: MutableMap<String, ConfigScreenManager> = mutableMapOf()
    private var validScopes: MutableSet<String> = mutableSetOf() //configs are sorted into Managers by namespace
    private var validSubScopes: MutableSet<String> = mutableSetOf()
    private var hasScrapedMetadata = false
    @Environment(EnvType.CLIENT)
    internal fun getScreenScopes(): Set<String>{
        if (!hasScrapedMetadata) {
            val set = mutableSetOf(*validScopes.toTypedArray())
            for (container in FabricLoader.getInstance().allMods) {
                val customValue = container.metadata.getCustomValue("fzzy_config") ?: continue
                if (customValue.type != CustomValue.CvType.ARRAY) continue
                val arrayValue = customValue.asArray
                for (thing in arrayValue) {
                    if (thing.type != CustomValue.CvType.STRING) continue
                    set.add(thing.asString)
                }
            }
            hasScrapedMetadata = true
            return set.toSet()
        } else {
            return validScopes
        }
    }

    @Environment(EnvType.CLIENT)
    internal fun getSubScreenScopes(): Set<String>{
        return validSubScopes
    }

    @Environment(EnvType.CLIENT)
    internal fun openScreen(scope: String) {
        val namespaceScope = getValidScope(scope)
        if (namespaceScope == null){
            FC.LOGGER.error("Failed to open a FzzyConfig screen. Invalid scope provided: [$scope]")
            return
        }
        val manager = configScreenManagers.computeIfAbsent(namespaceScope) {
            ConfigScreenManager(
                namespaceScope,
                clientConfigs.filterKeys { s -> s.startsWith(namespaceScope) }.map { ConfigSet(it.value.active, it.value.base, !SyncedConfigRegistry.hasConfig(it.key)) })
        }
        manager.openScreen(scope)
    }

    @Environment(EnvType.CLIENT)
    internal fun provideScreen(scope: String): Screen? {
        val namespaceScope = getValidScope(scope)
        if (namespaceScope == null){
            FC.LOGGER.error("Failed to open a FzzyConfig screen. Invalid scope provided: [$scope]")
            return null
        }
        val manager = configScreenManagers.computeIfAbsent(namespaceScope) {
            ConfigScreenManager(
                namespaceScope,
                clientConfigs.filterKeys { s -> s.startsWith(namespaceScope) }.map { ConfigSet(it.value.active, it.value.base, !SyncedConfigRegistry.hasConfig(it.key)) })
        }
        return manager.provideScreen(scope)
    }

    @Environment(EnvType.CLIENT)
    internal fun handleForwardedUpdate(update: String, player: UUID, scope: String, summary: String){
        val namespaceScope = getValidScope(scope)
        if (namespaceScope == null){
            FC.LOGGER.error("Failed to handle a forwarded setting. Invalid scope provided: [$scope]")
            return
        }
        val manager = configScreenManagers[namespaceScope]
        if (manager == null){
            FC.LOGGER.error("Failed to handle a forwarded setting. Unknown scope provided: [$scope]")
            return
        }
        manager.receiveForwardedUpdate(update, player, scope, summary)
    }

    @Environment(EnvType.CLIENT)
    private fun getValidScope(scope: String): String?{
        if(validScopes.contains(scope)) return scope
        var validScopeTry = scope.substringBeforeLast('.')
        if (validScopeTry == scope) return null
        while(!validScopes.contains(validScopeTry) && validScopeTry.contains('.')){
            validScopeTry = validScopeTry.substringBeforeLast('.')
        }
        return if(validScopes.contains(validScopeTry)) validScopeTry else null
    }

    @Environment(EnvType.CLIENT)
    internal fun registerConfig(config: Config, baseConfig: Config){
        validScopes.add(config.getId().namespace)
        validSubScopes.add(config.getId().path)
        UpdateManager.applyKeys(config)
        clientConfigs[config.getId().toTranslationKey()] = ConfigPair(config,baseConfig)
    }

    private class ConfigPair(val active: Config, val base: Config)
}