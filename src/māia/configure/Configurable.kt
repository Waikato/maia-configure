package māia.configure

/*
 * TODO
 *
 * TODO: Remove requirement for object constructor by adding method to
 *       Configuration which instantiates.
 */

import māia.configure.error.ConfigurableNotRegisteredError
import māia.configure.error.NoConfigurationBlockConstructorError
import māia.configure.error.NoConfigurationObjectConstructorError
import māia.configure.visitation.ConfigurationVisitable
import māia.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.findAnnotation

/**
 * Base class for types which are configured by a separate [Configuration]
 * object.
 *
 * Sub-types of this class must implement two constructors, one taking a
 * block which configures an existing configuration, and one which takes a
 * pre-made configuration. This check cannot be enforced by the compiler so is
 * only checked at run-time, when an instance of the sub-type is constructed.
 *
 * Sub-types of this class must also register their configuration types so
 * that it can be reflected dynamically.
 *
 * @param C     The type of [Configuration] the configurable takes.
 */
abstract class Configurable<out C : Configuration> : ConfigurationVisitable {

    constructor(config : C) : this(config.asReconfigureBlock())

    // Checks that the configurable can be constructed by a block and another configuration.
    // This is a run-time check on construction of the sub-class as Kotlin currently doesn't
    // allow interfaces to define constructor conventions.
    constructor(block : C.() -> Unit = {}) {
        // Get the type of configuration for this configurable (must be registered)
        val configurationClass = getConfigurationClass(this::class)

        // Getting the constructors will fail if they don't exist
        this::class.getConfigurationBlockConstructor(configurationClass)
        this::class.getConfigurationObjectConstructor(configurationClass)

        // Initialise a new read-only configuration for this configurable
        configuration = configurationClass.initialise(block).finalise()
    }

    /** The configuration instance for this object. */
    val configuration : C

    // region Registration

    /**
     * Annotation for registering a [Configurable] with the type of [Configuration]
     * it expects. Each concrete [Configurable] sub-type must be annotated with this
     * annotation, otherwise a [ConfigurableNotRegisteredError] will be thrown on
     * construction.
     *
     * @param configurableClass     The [KClass] of the [Configurable] sub-type.
     * @param configurationClass    The [KClass] of the [Configuration] type for the [configurableClass].
     */
    @Target(AnnotationTarget.CONSTRUCTOR)
    annotation class Register<C : Configurable<CC>, CC: Configuration>(
            val configurableClass : KClass<C>,
            val configurationClass : KClass<CC>
    )

    /**
     * Keeps track of the registered configurable sub-types and their configuration types.
     */
    companion object Registrations {

        /** The mapping from configurable sub-types to the type of configuration they take. */
        private val registrations = HashMap<KClass<out Configurable<*>>, KClass<out Configuration>>()

        /**
         * Gets the configuration class for a configurable.
         *
         * @param configurableClass                 The [KClass] of the [Configurable] to get the configuration-type for.
         * @return                                  The [KClass] of the [Configuration] that corresponds to the configurable.
         * @throws ConfigurableNotRegisteredError   If the configurable is not annotated with the [Register]
         *                                          annotation.
         */
        internal fun getConfigurationClassInternal(configurableClass : KClass<out Configurable<*>>) : KClass<out Configuration> {
            // See if the configuration type is already in the cache
            val configurationClass = registrations[configurableClass]

            // If it is, return it
            if (configurationClass != null) return configurationClass

            // Find the registration annotation
            val annotation = configurableClass.constructors.iterator().map {
                it.findAnnotation<Register<*, *>>()
            }.asIterable().firstOrNull() ?: throw ConfigurableNotRegisteredError(configurableClass)

            // Add the information from the annotation to the cache
            registrations[annotation.configurableClass] = annotation.configurationClass

            return annotation.configurationClass
        }
    }

    // endregion

    // region Visitation

    override fun getConfigurationClass() : KClass<out Configuration> = configuration.getConfigurationClass()

    override fun iterateElements() : Iterator<ConfigurationVisitable.Element> = configuration.iterateElements()

    // endregion

}

// region Get Constructors

/**
 * TODO: Comment
 * TODO: Returns type like (CC) -> Unit, should be CC.() -> Unit. It still works,
 *       but needs fixing anyway
 */
fun <CC : Configuration> KClass<out CC>.createConfigurationBlockType() : KType {
    return createType<CC.() -> Unit>(
            listOf(
                    KTypeProjection.invariant(createProjectedType()),
                    KTypeProjection.invariant(createType<Unit>())
            )
    )
}

/**
 * TODO
 */
fun <CC : Configuration> KClass<out CC>.createConfigurationObjectType() : KType {
    return createProjectedType()
}

/**
 * Gets the constructor for a given configurable sub-type which
 * takes a configuration block of the given type.
 *
 * @receiver                                        The class of the [Configurable] sub-type.
 * @param configurationClass                        The type of configuration the class takes, or a sub-type thereof.
 * @return                                          A constructor of the configurable sub-type.
 * @throws NoConfigurationBlockConstructorError     If the constructor doesn't exist.
 */
fun <C : Configurable<CC>, CC : Configuration> KClass<C>.getConfigurationBlockConstructor(
        configurationClass : KClass<out CC> = getConfigurationClass()
) : (CC.() -> Unit) -> C {
    // Create a type-description of the argument to the configuration-block constructor
    val type = configurationClass.createConfigurationBlockType()

    // Get the constructor with that argument type, throwing if it doesn't exist
    val constructor = getConstructorWithParamTypes(type) ?: throw NoConfigurationBlockConstructorError(this)

    return { block -> constructor.call(block) }
}

/**
 * Gets the constructor for a given configurable sub-type which
 * takes a configuration object of the given type.
 *
 * @receiver                                        The class of the [Configurable] sub-type.
 * @param configurationClass                        The type of configuration the class takes, or a sub-type thereof.
 * @return                                          A constructor of the configurable sub-type.
 * @throws NoConfigurationObjectConstructorError    If the constructor doesn't exist.
 */
fun KClass<out Configurable<*>>.getConfigurationObjectConstructorUntyped(
        configurationClass : KClass<out Configuration> = getConfigurationClass()
) : (Configuration) -> Configurable<*> {
    // Create a type-description of the argument to the configuration-object constructor
    val type = configurationClass.createConfigurationObjectType()

    // Get the constructor which takes a single configuration object as its only argument, throwing if it doesn't exist
    val constructor = getConstructorWithParamTypes(type) ?: throw NoConfigurationObjectConstructorError(this)

    return { configuration -> constructor.call(configuration)}
}

/**
 * Gets the constructor for a given configurable sub-type which
 * takes a configuration object of the given type. Statically-typed.
 *
 * @receiver                                        The class of the [Configurable] sub-type.
 * @param configurationClass                        The type of configuration the class takes, or a sub-type thereof.
 * @return                                          A constructor of the configurable sub-type.
 * @throws NoConfigurationObjectConstructorError    If the constructor doesn't exist.
 */
fun <C : Configurable<CC>, CC : Configuration> KClass<C>.getConfigurationObjectConstructor(configurationClass : KClass<out CC>) : ((CC) -> C) {
    return this.getConfigurationObjectConstructorUntyped(configurationClass) as ((CC) -> C)
}

// endregion

// region Get Configuration Class

/**
 * Gets the configuration class for a given class of configurable.
 *
 * @param configurableClass                 The type of [Configurable].
 * @return                                  The type of [Configuration].
 * @throws ConfigurableNotRegisteredError   If the configurable is not annotated with the [Configurable.Register]
 *                                          annotation.
 */
fun getConfigurationClassUntyped(configurableClass : KClass<out Configurable<*>>) : KClass<out Configuration> {
    return Configurable.getConfigurationClassInternal(configurableClass)
}

/**
 * Gets the configuration class for a given class of configurable.
 *
 * @param configurableClass                 The type of [Configurable].
 * @return                                  The type of [Configuration].
 * @throws ConfigurableNotRegisteredError   If the configurable is not annotated with the [Configurable.Register]
 *                                          annotation.
 */
fun <C : Configurable<CC>, CC : Configuration> getConfigurationClass(configurableClass : KClass<C>) : KClass<CC> {
    return getConfigurationClassUntyped(configurableClass) as KClass<CC>
}

/**
 * Gets the configuration class for a given class of configurable.
 *
 * @param configurableClass                 The type of [Configurable].
 * @return                                  The type of [Configuration].
 * @throws ConfigurableNotRegisteredError   If the configurable is not annotated with the [Configurable.Register]
 *                                          annotation.
 */
inline fun <reified C : Configurable<CC>, CC : Configuration> getConfigurationClass() : KClass<CC> {
    return getConfigurationClass(C::class)
}

// endregion

// region Construction

/**
 * Constructs an instance of a class of [Configurable]s from a [Configuration].
 *
 * @receiver                The class of [Configurable] to construct.
 * @param configuration     The [Configuration] to initialise the [Configurable] with.
 * @return                  An instance of the [Configurable] type.
 * @param C                 The type of [Configurable] to construct.
 * @param CC                The type of [Configuration] the [Configurable] takes.
 */
fun <C : Configurable<CC>, CC : Configuration> KClass<C>.initialise(configuration : CC) : C {
    return getConfigurationObjectConstructor(configuration::class)(configuration)
}

/**
 * Clones a configurable object.
 *
 * @receiver    The [Configurable] to clone.
 * @return      The clone of the receiver.
 * @param C     The type of the configurable object.
 * @param CC    The type of configuration the configurable takes.
 */
fun <C : Configurable<CC>, CC : Configuration> C.clone() : C {
    return this::class.initialise(configuration)
}

// endregion
