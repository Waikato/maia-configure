package māia.configure.error

import māia.configure.ConfigurationElement

/**
 * Error for when trying to access an optional element that
 * currently has no value.
 *
 * @param name  The name of the element being accessed.
 */
class AbsentError(val name : String) : Exception("Configuration element $name is absent") {

    constructor(element : ConfigurationElement<*>) : this(element.name)

}
