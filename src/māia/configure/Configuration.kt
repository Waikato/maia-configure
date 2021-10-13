package māia.configure

import māia.configure.error.*
import māia.configure.util.getCommonConfigurationElements
import māia.configure.util.ifNotAbsent
import māia.configure.visitation.ConfigurationVisitable
import māia.util.*
import kotlin.reflect.*
import kotlin.reflect.full.memberProperties

/**
 * Base class for configuration objects. A configuration holds all of the
 * instantiation parameters for a [Configurable] object. This way, objects
 * can be easily serialised and deserialised by noting their class and
 * configuration.
 *
 * @author Corey Sterling (csterlin at waikato dot ac dot nz)
 */
abstract class Configuration : ConfigurationVisitable {

    // All configurations must have a default constructor. As constructor
    // semantics cannot be statically enforced (a la interfaces), this is a run-time
    // check on first construction.
    init {
        if (this::class.getZeroArgConstructor() == null)
            throw NoDefaultConstructorError(this::class)
    }

    // region Registered Elements

    /** The ordering of the configuration elements of this configuration. */
    internal val orderedElementNames = ArrayList<String>()

    /** The configuration items of this configuration. */
    internal val elements = HashMap<String, ConfigurationElement<Any?>>()

    /**
     * Called by configuration elements to register themselves with the
     * configuration at delegation time.
     *
     * @param element   The configuration element being registered with this
     *                  configuration.
     */
    internal fun registerElement(
        element : ConfigurationElement<Any?>,
        name: String
    ) {
        orderedElementNames.add(name)
        elements[name] = element
    }

    /**
     * Resolves a dotted name to a configuration element in this configuration.
     *
     * @param name                      The dotted name of the configuration element.
     * @return                          The configuration element.
     * @throws AbsentError              If one of the intermediary names is absent in this configuration.
     * @throws NotNavigableError        If one of the intermediary names is not a sub-configuration.
     * @throws NoSuchElementException   If one of the dotted names doesn't exist on this configuration.
     */
    private fun resolveName(name : String) : ConfigurationElement<Any?> {
        // If the name is not dotted, get the direct item
        if ('.' !in name) return getRegisteredElement(name)

        // Split the top-level name from the rest
        val (topLevelName, theRest) = name.split(".", limit = 2)

        // Resolve the top-level name
        val topLevelItem = getRegisteredElement(topLevelName)
        if (topLevelItem !is SubConfiguration) throw NotNavigableError(name, topLevelName)

        // Recurse
        try {
            return (topLevelItem as SubConfiguration<*>).getValue().resolveName(theRest)
        } catch (e : AbsentError) {
            // Supplant the error with a more-informative one rooted at the
            // current recursion level
            throw AbsentError("$topLevelName.${e.name}")
        } catch (e : NotNavigableError) {
            // Supplant the error with a more-informative one rooted at the
            // current recursion level
            throw NotNavigableError(name, e.problemElement)
        }
    }

    /**
     * Gets the configuration element registered with this configuration, by name.
     *
     * @param name                      The name of the element to find.
     * @return                          The element if found.
     * @throws NoSuchElementException   If the configuration element wasn't found.
     */
    private fun getRegisteredElement(name : String) : ConfigurationElement<Any?> {
        return elements[name] ?: throw NoSuchElementException("No config item named $name")
    }

    // endregion

    // region Modification By Name

    /**
     * Gets the value of a configuration element by name.
     *
     * @param name                      The name of the configuration element.
     * @return                          The value of the element with the given name.
     */
    fun getValueByName(name : String) : Any? {
        return resolveName(name).getValue()
    }

    /**
     * Sets the value of a configuration element by name.
     *
     * @param name                      The name of the configuration element.
     * @param value                     The value to dataset against the element.
     */
    fun setValueByName(name : String, value : Any?) {
        resolveName(name).setValue(value)
    }

    /**
     * Clears the value of a configuration element by name.
     *
     * @param name                      The name of the configuration element.
     */
    fun clearValueByName(name : String) {
        resolveName(name).clearValue()
    }

    // endregion

    // region Iteration

    /***
     * Iterates through all configuration items in this configuration
     * (by name only).
     */
    fun iterateElementNames() : Iterator<String> {
        return orderedElementNames.iterator()
    }

    internal fun iterateConfigurationElements() : Iterator<ConfigurationElement<*>> {
        return iterateElementNames().map { resolveName(it) }
    }

    internal fun forEachConfigurationElement(block : ConfigurationElement<*>.() -> Unit) {
        iterateConfigurationElements().forEach { it.block() }
    }

    internal fun forEachConfigurationItem(block : ConfigurationItem<*>.() -> Unit) {
        forEachConfigurationElement { if (this is ConfigurationItem) block() }
    }

    internal fun forEachSubConfiguration(block : SubConfiguration<*>.() -> Unit) {
        forEachConfigurationElement { if (this is SubConfiguration) block() }
    }

    internal fun forEachSubConfigurationActual(block : Configuration.() -> Unit) {
        forEachSubConfiguration { actual.get().block() }
    }

    // endregion

    // region Visitation

    override fun getConfigurationClass() : KClass<out Configuration> = kotlinClass

    override fun iterateElements() : Iterator<ConfigurationVisitable.Element> {
        return iterateConfigurationElements().chainMap {
            if (it.actual is Absent)
                // Skip missing optional elements
                EmptyIterator
            else if (it is ConfigurationItem)
                // Return the simple item element
                itemIterator(ConfigurationVisitable.Element.Item(it.name, it.getValue(), it.metadata))
            else {
                // Get the sub-configuration value
                val sub = (it as SubConfiguration<Configuration>).getValue()

                // Return the sub-configuration element
                itemIterator(ConfigurationVisitable.Element.SubConfiguration(it.name, sub.safeVisitable(), it.metadata))
            }
        }
    }

    /**
     * Returns a object which allows visitation safely (i.e. without exposing
     * write-access to the visitor).
     *
     * @return  A [ConfigurationVisitable] object which defers to this configuration.
     */
    fun safeVisitable() : ConfigurationVisitable = object : ConfigurationVisitable by this {}

    // endregion

    // region Integrity

    /** Contains the item values to revert to once integrity checks are resumed (if they fail). */
    internal var restorePoint = HashMap<String, Optional<*>>()

    /**
     * Sets a restore point for a configuration element when it is first modified
     * during reconfiguration.
     *
     * @param name      The name of the configuration element being modified.
     * @param value     The original value of the element before modification.
     */
    internal fun setRestorePoint(name : String, value : Optional<*>) {
        // Don't keep restore points unless we are reconfiguring
        if (lifecyclePhase != LifecyclePhase.RECONFIGURING) return

        // If we already have a restore point for this element, this is a secondary
        // modification, so keep the original value
        if (name in restorePoint) return

        // Create the restore point for this element
        restorePoint[name] = value
    }

    /**
     * Suspends integrity checks while bulk changes to the configuration
     * are being made.
     *
     * @throws InitialisationError  If called before the configuration is initialised.
     */
    internal fun enterReconfiguration() : Unit = ensureReconfigurable {
        // This is a no-op if already reconfiguring
        if (lifecyclePhase == LifecyclePhase.RECONFIGURING) return

        // Suspend integrity checks for all sub-configurations first
        forEachSubConfigurationActual { enterReconfiguration() }

        // Suspend our integrity checks
        lifecyclePhase = LifecyclePhase.RECONFIGURING
    }

    /**
     * Resumes integrity checks after bulk modification. Performs an integrity check
     * before returning, and if it fails, reverts the state of the configuration back
     * to before the
     *
     * @throws InitialisationError  If called before the configuration is initialised.
     * @throws IntegrityError       If the integrity of the configuration is compromised.
     */
    internal fun endReconfiguration() {
        // If integrity checks are not suspended, this is a no-op
        if (lifecyclePhase != LifecyclePhase.RECONFIGURING) return

        try {
            // Check each sub-configuration for integrity first
            forEachSubConfigurationActual { endReconfiguration() }

            // Check our overall integrity
            performIntegrityCheck()

        } catch (e : IntegrityError) {
            // If integrity was compromised, unwind all changes
            unwindChanges()

            // Propagate the integrity error
            throw e
        } finally {
            // Resume integrity checks
            lifecyclePhase = LifecyclePhase.INITIALISED
        }
    }

    /**
     * Restores all configuration elements back to the state they were in
     * when integrity checks were suspended.
     */
    internal fun unwindChanges() {
        // If integrity checks are not suspended, this is a no-op
        if (lifecyclePhase != LifecyclePhase.RECONFIGURING) return

        // Restore values to their prior state
        for ((name, state) in restorePoint.entries) {
            if (state is Present)
                setValueByName(name, state.get())
            else
                clearValueByName(name)
        }

        // Clear the restore point
        restorePoint.clear()
    }

    /**
     * Checks the integrity of this configuration.
     *
     * @throws InitialisationError  If this configuration is not initialised.
     * @throws IntegrityError       If the integrity of this configuration is compromised.
     */
    internal fun performIntegrityCheck() : Unit = ensureInitialised {
        // See if the sub-types integrity check method fails
        val error = checkIntegrity() ?: return

        // Throw an integrity error if the integrity is compromised
        throw IntegrityError(error)
    }

    /**
     * Checks the integrity of the configuration. Override to
     * enforce custom integrity checks.
     *
     * @return  A string describing the reason the integrity is
     *          compromised, or null if the integrity is okay.
     */
    open fun checkIntegrity() : String? {
        return null
    }

    // endregion

    // region Lifecycle Control

    /**
     * The lifecycle phases of a configuration.
     */
    enum class LifecyclePhase {
        UNINITIALISED, // Constructed but not initialised
        INITIALISING, // In the process of initialisation
        INITIALISED, // Initialised, usable state
        RECONFIGURING, // Performing bulk updates to the configuration
        FINALISED // Made read-only
    }

    /** The current phase of this configuration's lifecycle. */
    internal var lifecyclePhase : LifecyclePhase = LifecyclePhase.UNINITIALISED

    /** If the configuration is currently reconfigurable. */
    val reconfigurable : Boolean
        get() = lifecyclePhase in LifecyclePhase.INITIALISED..LifecyclePhase.RECONFIGURING

    /** If the configuration has finished being initialised. */
    val initialised : Boolean
        get() = lifecyclePhase >= LifecyclePhase.INITIALISED

    /** If configuration elements are currently writable. */
    val writable : Boolean
        get() = lifecyclePhase in LifecyclePhase.INITIALISING..LifecyclePhase.RECONFIGURING

    /** If bulk updates are being made to the configuration. */
    val integrityChecksSuspended : Boolean
        get() = lifecyclePhase == LifecyclePhase.INITIALISING || lifecyclePhase == LifecyclePhase.RECONFIGURING

    /** If the configuration has entered the read-only lifecycle end-phase. */
    val finalised : Boolean
        get() = lifecyclePhase == LifecyclePhase.FINALISED

    /** If the configuration is in a state that its integrity has been checked. */
    val integrityAssured : Boolean
        get() = lifecyclePhase == LifecyclePhase.INITIALISED || lifecyclePhase == LifecyclePhase.FINALISED

    /** If the configuration is in the initialisation phase. */
    val initialising : Boolean
        get() = lifecyclePhase == LifecyclePhase.INITIALISING

    /**
     * Only performs the provided action if this configuration is initialised.
     *
     * @param block                 The action to perform.
     * @return                      The result of performing the action.
     * @throws InitialisationError  If the configuration is uninitialised.
     * @param R                     The return type of the action.
     */
    internal inline fun <R> ensureInitialised(block : () -> R) : R {
        if (!initialised) throw InitialisationError("Configuration is not initialised")
        return block()
    }

    /**
     * Performs the provided function under the assurance that the receiving
     * configuration is reconfigurable.
     *
     * @param block                         The function to perform.
     * @return                              The result of the performed function.
     * @throws InitialisationError          If the configuration is uninitialised.
     * @throws ReadOnlyModificationError    If the configuration is in read-only mode.
     * @param R                             The return type of the performed function.
     */
    internal inline fun <R> ensureReconfigurable(block : () -> R) : R = ensureInitialised {
        // Ensure the configuration is not in read-only mode
        if (!reconfigurable) throw ReadOnlyModificationError()

        // Perform the function and return its result
        return block()
    }

    /**
     * Performs the provided function under the assurance that the receiving
     * configuration has had its integrity checked in its current state.
     *
     * @param block                         The function to perform.
     * @return                              The result of the performed function.
     * @throws InitialisationError          If the configuration is uninitialised.
     * @throws IntermediateStateError       If the configuration is in an intermediate state.
     * @param R                             The return type of the performed function.
     */
    internal inline fun <R> ensureIntegrity(block : () -> R) : R = ensureInitialised {
        if (!integrityAssured) throw IntermediateStateError()
        return block()
    }

    // endregion

}

// region Initialisation

/**
 * Initialises the values of an uninitialised configuration.
 */
internal fun <C : Configuration> C.initialiseWithoutIntegrityCheck(block : C.() -> Unit = {}) : C {
    // Can only call this function once to initialise a configuration
    if (lifecyclePhase != Configuration.LifecyclePhase.UNINITIALISED)
        throw InitialisationError("Configuration is already initialised or initialising")

    // Move to the initialising lifecycle phase
    lifecyclePhase = Configuration.LifecyclePhase.INITIALISING

    // Initialise with the provided initialiser block
    block()

    // For any element not initialised by the block, run its default initialiser
    forEachConfigurationElement { initialise() }

    // Mark the configuration as initialised
    lifecyclePhase = Configuration.LifecyclePhase.INITIALISED

    return this
}

/**
 * Performs construction and initialisation of a configuration in one step.
 */
internal fun <C : Configuration> KClass<C>.initialiseWithoutIntegrityCheck(block : C.() -> Unit = {}) : C {
    // Construct the configuration
    val config = getZeroArgConstructor()?.call() ?: throw NoDefaultConstructorError(this)

    // Initialise and return it
    return config.initialiseWithoutIntegrityCheck(block)
}

/**
 * Performs construction and initialisation of a configuration in one step.
 */
fun <C : Configuration> KClass<C>.initialise(block : C.() -> Unit = {}) : C {
    return initialiseWithoutIntegrityCheck(block).apply {
        performIntegrityCheck()
    }
}

/**
 * Performs construction and initialisation of a configuration in one step.
 */
inline fun <reified C : Configuration> initialise(noinline block : C.() -> Unit = {}) : C {
    return C::class.initialise(block)
}

// endregion

// region Re-Configuration

/**
 * Reconfigures a configuration. If the integrity of the configuration
 * is compromised by the reconfiguration, the configuration is reverted
 * to its original state before the call to this function.
 *
 * @param block             The reconfiguration code.
 * @return                  The reconfigured configuration.
 * @throws IntegrityError   If the integrity of the reconfigured configuration is compromised.
 */
fun <C : Configuration> C.reconfigure(block : C.() -> Unit) : C = ensureReconfigurable {
    // Suspend integrity checks for the duration of the operation
    enterReconfiguration()

    // Attempt to execute the block
    try {
        block()

    // If an error occurred inside the block, unwind the changes made so far,
    // and propagate the error
    } catch (e : Exception) {
        unwindChanges()
        throw e

    // Either way, resume integrity checks
    } finally {
        endReconfiguration()
    }

    return this
}

/**
 * Updates the values of this configuration with those in another. Chainable.
 *
 * @param other:    The configuration to draw values from.
 * @return          The updated configuration.
 */
internal fun <C : Configuration, O : Configuration> C.updateWithoutIntegrityCheck(other : O) : C {
    // Determine which items are common to both configurations
    val commonNames = getCommonConfigurationElements(this, other)

    // Set our shared configuration items from the other configuration's values
    for (name in commonNames) {
        ifNotAbsent { other.getValueByName(name) } then {
            setValueByName(name, it)
        } otherwise {
            clearValueByName(name)
        }
    }

    return this
}

/**
 * Updates the values of this configuration with those in another. Chainable.
 *
 * @param other:    The configuration to draw values from.
 * @return          The updated configuration.
 */
fun <C : Configuration, O : Configuration> C.update(other : O) : C = reconfigure {
    updateWithoutIntegrityCheck(other)
}

/**
 * Returns a block which updates its receiver with this configuration.
 *
 * @return:     A block which updates its receiver with this configuration.
 */
fun <C : Configuration, O : Configuration> C.asReconfigureBlock() : O.() -> Unit {
    return {
        this@asReconfigureBlock.ensureIntegrity {
            if (this@asReconfigureBlock.kotlinClass == this.kotlinClass)
                updateWithoutIntegrityCheck(this@asReconfigureBlock)
            else
                update(this@asReconfigureBlock)
        }
    }
}

/**
 * Creates a new configuration object identical to this one.
 *
 * @return  The new configuration object.
 */
fun <C : Configuration> C.clone() : C = ensureInitialised {
    return kotlinClass.initialise(asReconfigureBlock())
}

// endregion

// region Finalisation

/**
 * Finalises a configuration so that no more modifications can be made to it.
 *
 * @receiver                        The configuration to finalise.
 * @return                          The finalised configuration.
 * @throws IntermediateStateError   If the configuration is not in a state that it can be finalised.
 */
fun <C : Configuration> C.finalise() : C = ensureIntegrity {
    lifecyclePhase = Configuration.LifecyclePhase.FINALISED
    return this
}

// endregion

// region Class-Level Metadata



// endregion

// region Unfinished

// TODO: Finish implementations

fun <T> clear(accessor : KProperty0<T>) {
    TODO("Can't currently clear a configuration item directly. " +
            "Call Configuration.clearConfigItemValue(String) instead")
}

/**
 * Iterates through the top-level configuration elements of a configuration class,
 * by name.
 *
 * @return  An iterator of configuration element names.
 */
fun <C : Configuration> KClass<C>.configurationElementIterator() : Iterator<String> {
    return memberProperties
            .iterator()
            .filter { TODO("Currently can't determine if a member property is a configuration element") }
            .map { it.name }
}

// endregion

/**
 * TODO
 */
inline fun <reified C : Configurable<CC>, CC : Configuration> CC.construct() : C {
    return C::class.getConfigurationBlockConstructor().invoke(asReconfigureBlock())
}
