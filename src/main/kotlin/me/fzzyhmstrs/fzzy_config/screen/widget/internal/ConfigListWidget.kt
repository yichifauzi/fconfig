/*
* Copyright (c) 2024 Fzzyhmstrs
*
* This file is part of Fzzy Config, a mod made for minecraft; as such it falls under the license of Fzzy Config.
*
* Fzzy Config is free software provided under the terms of the Timefall Development License - Modified (TDL-M).
* You should have received a copy of the TDL-M with this software.
* If you did not, see <https://github.com/fzzyhmstrs/Timefall-Development-Licence-Modified>.
* */

package me.fzzyhmstrs.fzzy_config.screen.widget.internal

import me.fzzyhmstrs.fzzy_config.screen.LastSelectable
import me.fzzyhmstrs.fzzy_config.screen.entry.BaseConfigEntry
import me.fzzyhmstrs.fzzy_config.screen.entry.SettingConfigEntry
import me.fzzyhmstrs.fzzy_config.screen.internal.ConfigScreen
import me.fzzyhmstrs.fzzy_config.screen.internal.SuggestionWindowListener
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.Element
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.screen.narration.NarrationPart
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.client.gui.widget.ElementListWidget
import net.minecraft.client.gui.widget.ThreePartsLayoutWidget
import net.minecraft.client.gui.widget.Widget
import net.minecraft.client.search.SuffixArray
import java.util.*
import java.util.function.Consumer

//client
internal class ConfigListWidget(minecraftClient: MinecraftClient, width: Int, contentHeight: Int, headerHeight: Int, private val drawBackground: Boolean) :
    ElementListWidget<BaseConfigEntry>(minecraftClient, width, contentHeight, headerHeight, 24), LastSelectable, SuggestionWindowListener
{

    constructor(minecraftClient: MinecraftClient, parent: ConfigScreen, drawBackground: Boolean = true): this(minecraftClient, parent.width, parent.layout.height - parent.layout.headerHeight - parent.layout.footerHeight, parent.layout.headerHeight, drawBackground)

    private var visibleElements = 5

    private var suggestionWindowElement: Element? = null

    override fun setSuggestionWindowElement(element: Element?) {
        this.suggestionWindowElement = element
    }

    init {
        this.setRenderBackground(drawBackground)
        //this.setRenderHeader(true, 20)
    }

    private val wholeList: List<BaseConfigEntry> by lazy {
        this.children().toList()
    }

    private val  search: SuffixArray<Int> by lazy {
        val array = SuffixArray<Int>()
        for ((i, entry) in wholeList.withIndex()) {
            array.add(i, entry.name.string.lowercase(Locale.ROOT))
        }
        array.build()
        array
    }

    override var lastSelected: Element? = null

    override fun pushLast() {
        lastSelected = focused
    }

    override fun popLast() {
        (lastSelected as? BaseConfigEntry)?.let { focused = it }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        return suggestionWindowElement?.mouseClicked(mouseX, mouseY, button) ?: super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double, verticalAmount: Double): Boolean {
        return suggestionWindowElement?.mouseScrolled(mouseX, mouseY, amount, verticalAmount) ?: super.mouseScrolled(mouseX, mouseY, amount, verticalAmount)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        return suggestionWindowElement?.keyPressed(keyCode, scanCode, modifiers) ?: super.keyPressed(keyCode, scanCode, modifiers)
    }


    fun updateSearchedEntries(searchInput: String): Int {
        if (searchInput == "") {
            this.replaceEntries(wholeList.toList())
            scrollAmount = 0.0
            return wholeList.size
        }
        val results = search.findAll(searchInput.lowercase(Locale.ROOT))
        val list = wholeList.filterIndexed { index, _ -> results.contains(index) }
        this.replaceEntries(list)
        scrollAmount = 0.0
        return list.size
    }

    override fun forEachChild(consumer: Consumer<ClickableWidget>) {
    }

    override fun getRowWidth(): Int {
        return 260
    }

    fun position(width: Int, height: Int, y: Int) {
        this.setDimensions(width, height)
        setPosition(0, y)
        var count = 0
        for (i in 0 until this.entryCount) {
            val entryY = getRowTop(i)
            val entryYBottom = getRowBottom(i)
            if (entryY >= this.y && entryYBottom <= this.bottom) {
                count++
            }
            this.getEntry(i).positionWidget(entryY)
        }
        visibleElements = count
    }

    fun position(width: Int, layout: ThreePartsLayoutWidget) {
        this.position(width, (layout.height - layout.headerHeight - layout.footerHeight), layout.headerHeight)
    }

    fun getScrollbarX(): Int {
        return scrollbarPositionX
    }

    override fun getScrollbarPositionX(): Int {
        return x + this.width / 2 + this.rowWidth / 2 + 10
    }

    fun getClient(): MinecraftClient {
        return this.client
    }

    fun page(up: Boolean) {
        if (up) {
            scrollAmount -= (visibleElements * itemHeight)
        } else {
            scrollAmount += (visibleElements * itemHeight)
        }
    }

    fun add(entry: BaseConfigEntry) {
        this.addEntry(entry)
    }

    fun copy() {
        (focused as? SettingConfigEntry) ?.copyAction?.run() ?: (hoveredEntry as? SettingConfigEntry)?.copyAction?.run()
    }

    fun paste() {
        (focused as? SettingConfigEntry)?.pasteAction?.run() ?: (hoveredEntry as? SettingConfigEntry)?.pasteAction?.run()
    }

    override fun appendClickableNarrations(builder: NarrationMessageBuilder) {
        super.appendClickableNarrations(builder)
        builder.put(NarrationPart.USAGE, "")
    }

    override fun appendNarrations(builder: NarrationMessageBuilder, entry: BaseConfigEntry) {
        if(entry == focused || entry == hoveredEntry) {
            builder.put(NarrationPart.TITLE, entry.name)
            entry.appendEntryNarrations(builder)
        }
        super.appendNarrations(builder, entry)
    }

}