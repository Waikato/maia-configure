package maia.configure.visitation

import maia.configure.Configuration
import maia.configure.ConfigurationElement
import kotlin.reflect.KClass

/**
 * Interface for visitors of configurations.
 */
interface ConfigurationVisitor {

    /**
     * Called to begin writing a new configuration.
     *
     * @param cls   The type of configuration being written.
     */
    fun begin(cls : KClass<out Configuration>)

    /**
     * Called multiple times to supply the value of each item
     * in the configuration to the writer.
     *
     * @param name      The name of the configuration item.
     * @param value     The value of the item in the configuration.
     */
    fun item(name : String, value : Any?, metadata : ConfigurationElement.Metadata)

    /**
     * Called at the start of each sub-configuration in the configuration.
     * Possibly nested for sub-configurations of sub-configurations.
     *
     * @param name  The name of the sub-configuration.
     * @param cls   The type of the sub-configuration.
     */
    fun beginSubConfiguration(name : String, cls : KClass<out Configuration>, metadata : ConfigurationElement.Metadata)

    /**
     * Called at the end of a sub-configuration.
     */
    fun endSubConfiguration()

    /**
     * Called at the end of the configuration.
     */
    fun end()

}
