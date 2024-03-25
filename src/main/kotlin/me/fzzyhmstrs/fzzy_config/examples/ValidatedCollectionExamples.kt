package me.fzzyhmstrs.fzzy_config.examples

import me.fzzyhmstrs.fzzy_config.validation.list.ValidatedIdentifierList
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedIdentifier
import net.minecraft.registry.tag.ItemTags
import net.minecraft.util.Identifier

object ValidatedCollectionExamples {

    // validated identifier list. default list and ValidatedIdentifier for handling
    val validatedIdList = ValidatedIdentifierList(listOf(Identifier("stone_axe")), ValidatedIdentifier.ofTag(ItemTags.AXES))



}