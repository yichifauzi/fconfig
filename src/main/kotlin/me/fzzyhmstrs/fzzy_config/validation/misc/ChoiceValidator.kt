package me.fzzyhmstrs.fzzy_config.validation.misc

import me.fzzyhmstrs.fzzy_config.entry.EntryValidator
import me.fzzyhmstrs.fzzy_config.util.ValidationResult
import me.fzzyhmstrs.fzzy_config.validation.misc.ChoiceValidator.ValuesPredicate
import java.util.function.Predicate

/**
 * a validated set of choices.
 *
 * This is only an EntryValidator, used in Lists and Maps to define the valid new choices you can make
 * @param predicate a [ValuesPredicate] that defines the valid choices the user can make
 * @author fzzyhmstrs
 * since 0.2.0
 */
open class ChoiceValidator<T>(private val  predicate: ValuesPredicate<T>): EntryValidator<T> {

    override fun validateEntry(input: T, type: EntryValidator.ValidationType): ValidationResult<T> {
        return ValidationResult.predicated(input, predicate.test(input), "Value not allowed")
    }

    fun toStringValidator(): ChoiceValidator<String>{
        return ChoiceValidator(predicate.toStringPredicate())
    }

    class ValuesPredicate<T>(private val disallowedValues: List<T>?, private val allowableValues: List<T>?): Predicate<T>{
        override fun test(t: T): Boolean {
            return if(disallowedValues != null){
                if (allowableValues != null){
                    !disallowedValues.contains(t) && allowableValues.contains(t)
                } else {
                    !disallowedValues.contains(t)
                }
            } else allowableValues?.contains(t) ?: true
        }

        fun toStringPredicate(): ValuesPredicate<String>{
            return ValuesPredicate(disallowedValues?.mapNotNull { it?.toString() }, allowableValues?.mapNotNull { it?.toString() })
        }
    }

    companion object {
        fun <T> any(): ChoiceValidator<T> {
            return ChoiceValidator(ValuesPredicate(null,null))
        }

        fun <T> allowed(allowed: List<T>): ChoiceValidator<T>{
            return ChoiceValidator(ValuesPredicate(null,allowed))
        }
    }
}