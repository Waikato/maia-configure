package māia.configure.error

import māia.configure.ConfigurationElement

/**
 * Error when an attempt is made to clear a configuration element
 * that is non-optional.
 */
class ClearRequiredValueError(
        element : ConfigurationElement<*>
) : Exception("Attempted to clear non-optional element '${element.name}'")
