package me.fzzyhmstrs.fzzy_config.screen.widget

import me.fzzyhmstrs.fzzy_config.util.FcText
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.widget.PressableWidget
import net.minecraft.text.MutableText
import java.util.function.Consumer
import java.util.function.Supplier

class RevertChangesWidget(private val changesSupplier: Supplier<Int>, private val pressAction: Consumer<RevertChangesWidget>): PressableWidget(0,0,80,20,FcText.translatable("fc.button.revert")) {

    override fun getNarrationMessage(): MutableText {
        return FcText.translatable("fc.button.revert.message",changesSupplier.get())
    }

    override fun appendClickableNarrations(builder: NarrationMessageBuilder) {
        appendDefaultNarrations(builder)
    }

    override fun onPress() {
        pressAction.accept(this)
    }
}