package māia.configure

import māia.configure.error.AbsentError
import māia.configure.error.ClearRequiredValueError
import māia.configure.error.ConfigurationElementMissingMetadataError
import māia.configure.error.InitialisationError
import māia.configure.error.IntegrityError
import māia.configure.error.ReadOnlyModificationError
import māia.util.Absent
import māia.util.Optional
import māia.util.Present
import māia.util.kotlinClass
import māia.util.lambda
import māia.util.property.CachedReadOnlyProperty
import māia.util.property.SingleUseReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.full.findAnnotation

/**
 * Base class for all component element delegates for configurations.
 *
 * @param optional      Whether this element is allowed to be missing from the configuration.
 * @param initialiser   The default initialiser for this element if not value is explicitly set.
 * @param T             The type of value held by this element.
 *
 * @author Corey Sterling (csterlin at waikato dot ac dot nz)
 */
sealed class ConfigurationElement<T>(
        val optional : Boolean = false,
        private var initialiser : (() -> T)? = null
) : SingleUseReadWriteProperty<Configuration, T>() {

    /** The actual value of the element. */
    internal var actual : Optional<T> = Absent

    override fun onDelegation(
        owner : Configuration,
        property : KProperty<*>,
        name : String
    ) {
        // Register ourselves with our owner
        owner.registerElement(this as ConfigurationElement<Any?>, name)
    }

    /**
     * Gets the current value for this element of the configuration.
     *
     * @return                      The current value.
     * @throws InitialisationError  If the configuration is not initialised.
     * @throws AbsentError          If this is an optional element and the value is missing.
     */
    override fun getValue() : T = ensureInitialised {
        return if (actual is Present)
            actual.get()
        else
            throw AbsentError(this)
    }

    /**
     * Sets the value of this configuration element.
     *
     * @param value                         The new value for the element.
     * @throws ReadOnlyModificationError    If the configuration is read-only.
     * @throws IntegrityError               If the new value violates the integrity of the configuration.
     */
    override fun setValue(value: T) = ensureDelegated {
        modifyValue { value }
    }

    /**
     * Clears the value of this element.
     *
     * @throws
     */
    fun clearValue() = ensureDelegated {
        // Can't clear the value if it's not optional
        if (!optional) throw ClearRequiredValueError(this)

        modifyValue(null)
    }

    /**
     * Common functionality for [setValue]/[clearValue].
     *
     * @param supplier  A function which supplies the new value if setting,
     *                  or null if clearing.
     */
    private fun modifyValue(supplier : (() -> T)?) {
        // If the owning configuration is in read-only mode, this is an error
        if (owner.finalised) throw ReadOnlyModificationError()

        // If the owning configuration is not in a writable state, must be
        // pre-initialisation, also an error
        if (!owner.writable) throw InitialisationError("Attempted to modify value of '${name}' ($owner) " +
                "before initialisation")

        // Perform bulk modification is integrity checks are suspended
        if (owner.integrityChecksSuspended)
            modifyValueBulk(supplier)

        // Otherwise just perform a single modification of this value
        else
            modifyValueSingle(supplier)

        // This item is now initialised, so we don't need the initialiser anymore
        initialiser = null
    }

    /**
     * Modifies the value of this configuration element and immediately
     * performs an integrity check of the entire owning configuration.
     *
     * @param supplier
     *          A supplier of the new value for this element, or null to
     *          clear the value.
     */
    private fun modifyValueSingle(supplier : (() -> T)?) {
        // Save the current value so we can revert if integrity is compromised
        val current = actual

        // Prep and set the value
        actual = prepSupplier(supplier)

        // Check the integrity, and revert if it is violated
        try {
            owner.performIntegrityCheck()
        } catch (e: IntegrityError) {
            actual = current
            throw e
        }
    }

    /**
     * Modifies the value of this configuration element, delaying
     * integrity checks until all changes have been made.
     *
     * @param supplier
     *          A supplier of the new value for this element, or null to
     *          clear the value.
     */
    private fun modifyValueBulk(supplier : (() -> T)?) {
        // Save the current value so we can revert if integrity is compromised
        owner.setRestorePoint(name, actual)

        // Prep and set the value
        actual = prepSupplier(supplier)
    }

    /**
     * Executes the supplier function if it exists, and preps the value
     * returned.
     *
     * @param supplier
     *          A supplier of the new value for this element, or null to
     *          clear the value.
     * @return
     *          The prepared value.
     */
    private fun prepSupplier(supplier : (() -> T)?) : Optional<T> {
        return if (supplier != null)
            Present(prepValue(supplier()))
        else
            Absent
    }

    protected abstract fun prepValue(value : T) : T

    /**
     * Initialises this configuration element if it hasn't been already.
     */
    internal fun initialise() {
        // Abort if already initialised
        if (initialiser == null) {
            if (!optional && actual is Absent)
                throw InitialisationError(
                        "Configuration element '$name' of ${owner::class.qualifiedName} " +
                                "has no default initialiser and wasn't set explicitly"
                )
            else
                return
        }

        // Set our value to the result of the initialiser
        // (automatically removes the initialiser)
        setValue(initialiser!!())
    }

    /**
     * Makes sure this element is initialised before performing
     * some action that depends on this being the case.
     *
     * @param block                 The action to perform if the element is initialised.
     * @return                      The result of performing the action.
     * @throws InitialisationError  If the element or the owning configuration is not initialised.
     * @param R                     The return type of the action.
     */
    private inline fun <R> ensureInitialised(block : () -> R) : R = ensureDelegated {
        initialise()

        return block()
    }

    // region Metadata

    /** The metadata for this type of configuration element. */
    val metadata : Metadata
        get() = property.metadata

    /**
     * Property meta-data object for configuration elements.
     */
    data class Metadata(
        val description : String
    )

    /**
     * Annotation which defines the metadata for a configuration element.
     */
    @Target(AnnotationTarget.PROPERTY)
    annotation class WithMetadata(
        val description : String
    )

    // endregion

}

// region Property-Level Metadata

val KProperty<*>.metadata : ConfigurationElement.Metadata by CachedReadOnlyProperty(
        cacheInitialiser = {
            // TODO: Make sure the property is a configuration element
            val annotation = findAnnotation<ConfigurationElement.WithMetadata>()
                    ?: throw ConfigurationElementMissingMetadataError(this)
            ConfigurationElement.Metadata(annotation.description)
        }
)

// endregion

/**
 * Base class for configuration elements with simple values.
 *
 * @author Corey Sterling (csterlin at waikato dot ac dot nz)
 */
class ConfigurationItem<T>(
    optional : Boolean = false,
    initialiser: (() -> T)? = null
) : ConfigurationElement<T>(
    optional,
    initialiser
) {
    override fun prepValue(value: T): T {
        return value
    }
}

/**
 * A configuration element which represents a sub-section of the configuration,
 * which is a configuration in and of itself.
 *
 * @author Corey Sterling (csterlin at waikato dot ac dot nz)
 */
class SubConfiguration<C : Configuration> private constructor(
    optional : Boolean = false,
    cls : KClass<out C>? = null,
    block : (C.() -> Unit)? = null,
    requiresIntegrityCheck : Boolean = true
) : ConfigurationElement<C>(
    optional,
    when {
        cls == null -> null
        block == null -> null
        requiresIntegrityCheck -> lambda<() -> C> { cls.initialise(block) }
        else -> lambda<() -> C> { cls.initialiseWithoutIntegrityCheck(block) }
    }
) {

    constructor(initValue: C, optional : Boolean = false) : this(optional, initValue.kotlinClass, initValue.asReconfigureBlock(), false)

    constructor(cls : KClass<out C>, optional : Boolean = false, block : C.() -> Unit) : this(optional, cls, block, true)

    constructor(cls : KClass<out C>, optional : Boolean = false) : this(optional, cls, if (optional) null else lambda<C.() -> Unit> {}, false)

    override fun prepValue(value: C) : C = value.ensureIntegrity {
        // If the configuration is read-only, just keep the reference, otherwise
        // clone it to maintain integrity
        return if (owner.finalised && value.finalised)
            value
        else
            value.clone().also { if (owner.finalised) it.finalise() }
    }
}

/**
 * TODO
 */
inline fun <reified C : Configuration> subconfiguration(
        optional : Boolean = false,
        noinline block : (C.() -> Unit)? = null
) : SubConfiguration<C> {
    return if (block == null)
        SubConfiguration(C::class, optional)
    else
        SubConfiguration(C::class, optional, block)

}
