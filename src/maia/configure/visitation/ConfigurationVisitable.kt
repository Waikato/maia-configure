package maia.configure.visitation

import maia.configure.Configuration
import maia.configure.ConfigurationElement
import kotlin.reflect.KClass

/**
 * Interface for objects which can be visited in the same manner as
 * a configuration.
 */
interface ConfigurationVisitable {

    /**
     * Gets the type of configuration that this visitable represents.
     *
     * @return  The configuration type.
     */
    fun getConfigurationClass() : KClass<out Configuration>

    /**
     * Iterates over the elements of the visitable
     * that can be visited.
     *
     * @return  An iterator of visitable elements.
     */
    fun iterateElements() : Iterator<Element>

    /**
     * A representation of the elements of a configuration.
     *
     * @param name      The name of the configuration element.
     * @param value     The value of the configuration element.
     */
    sealed class Element(
            val name : String,
            open val value : Any?,
            val metadata : ConfigurationElement.Metadata) {

        class Item(
                name : String,
                value : Any?,
                metadata : ConfigurationElement.Metadata
        ) : Element(name, value, metadata)

        class SubConfiguration(name : String,
                               override val value : ConfigurationVisitable,
                               metadata : ConfigurationElement.Metadata
        ) : Element(name, value, metadata)
    }

}
