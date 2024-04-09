package me.fzzyhmstrs.fzzy_config.entry

import java.util.function.Consumer
import java.util.function.Supplier

/**
 * A base Entry for configs.
 *
 * Performs 8 basic functions
 * - serialize contents
 * - deserialize input
 * - validate updates
 * - correct errors
 * - provide widgets
 * - apply inputs
 * - supply outputs
 * - create instances
 *
 * SAM: [instanceEntry] creates an instance of this entry. Should be a copy of the original where possible
 * @param T the non-null type of the Entry stored value
 * @author fzzyhmstrs
 * @since 0.2.0
 */
interface Entry<T, E: Entry<T,E>>: EntryHandler<T>, EntryWidget<T>, Consumer<T>, Supplier<T> {
    fun instanceEntry(): E
    fun canCopyEntry(): Boolean
    fun isValidEntry(input: Any?): Boolean
    fun trySet(input: Any?)
}