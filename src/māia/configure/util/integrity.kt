package māia.configure.util

/*
 * Package for common integrity checks for configurations.
 */

import māia.configure.*
import māia.util.isNotSubClassOf
import māia.util.kotlinClass
import kotlin.reflect.KClass

/**
 * Checks if the given class is a [Configurable] that can be instantiated
 * with the given [Configuration].
 *
 * @param cls
 *          The class of configurable.
 * @param configuration
 *          The configuration to try instantiate the class with.
 * @return
 *          null if the class can be instantiated, or an error message if not.
 */
fun <T : Any> classMatchesConfiguration(
        cls : KClass<out T>,
        configuration: Configuration
) : String? {
    // The class must be configurable
    if (cls isNotSubClassOf Configurable::class)
        return "${cls.qualifiedName} is not configurable"

    // Get the required type of configuration for the class
    val requiredConfigurationClass = getConfigurationClassUntyped(cls as KClass<out Configurable<*>>)

    // Get the type of the given configuration
    val actualLearnerConfigurationClass = configuration.kotlinClass

    // Make sure they match
    return if (actualLearnerConfigurationClass isNotSubClassOf requiredConfigurationClass)
        "The configuration for ${cls.qualifiedName} should be a " +
                "${requiredConfigurationClass.qualifiedName} but is a " +
                "${actualLearnerConfigurationClass.qualifiedName} instead"
    else
        null
}
