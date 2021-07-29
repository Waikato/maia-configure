package māia.configure.util

import māia.configure.Configuration
import māia.util.isSubClassOf
import māia.util.kotlinClass

/**
 * Gets a list of names of configuration elements that two configurations share.
 *
 * @param c1    The first configuration.
 * @param c2    The second configuration.
 * @return      A list of names of shared configuration elements.
 */
internal fun getCommonConfigurationElements(c1 : Configuration, c2 : Configuration) : List<String> {
    // Get the classes of the configuration for inheritance ordering
    val class1 = c1.kotlinClass
    val class2 = c2.kotlinClass

    // Determine which items are common to both configurations
    return when {
        class1 isSubClassOf class2 -> c2.orderedElementNames
        class2 isSubClassOf class1 -> c1.orderedElementNames
        else -> TODO("Currently can only get common elements from configurations with a linear inheritance relationship")
    }
}
