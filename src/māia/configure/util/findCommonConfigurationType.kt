package māia.configure.util

import māia.configure.Configuration
import māia.util.isSubClassOf
import kotlin.reflect.KClass


/**
 * Finds the common ancestor class of two types of configuration.
 *
 * @param first     One of the configuration classes.
 * @param second    The other configuration class.
 * @return          The common base class of the two arguments.
 */
fun <C1 : Configuration, C2 : Configuration> findCommonConfigurationType(first : KClass<C1>, second : KClass<C2>) : KClass<out Configuration> {
    return when {
        first isSubClassOf second -> second
        second isSubClassOf first -> first
        else -> findCommonConfigurationType(first.configurationSuperClass, second.configurationSuperClass)
    }
}
