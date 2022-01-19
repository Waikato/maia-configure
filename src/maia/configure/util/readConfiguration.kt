package maia.configure.util

import maia.configure.Configuration
import maia.configure.ConfigurationElement
import maia.configure.visitation.ConfigurationVisitable
import maia.configure.visitation.ConfigurationVisitor
import maia.configure.initialise
import maia.configure.visitation.visit
import maia.util.lambda
import java.util.*
import kotlin.reflect.KClass

/**
 * Reads an instance of a configuration from a source.
 *
 * @param source    A source that can be visited like a configuration.
 * @return          The initialised configuration.
 */
fun readConfiguration(source : ConfigurationVisitable) : Configuration {
    return ConfigurationReader().visit(source).result
}

/**
 * Helper class for building a configuration from any object
 * that is visitable like a configuration.
 */
private class ConfigurationReader : ConfigurationVisitor {

    /** The configuration, once it is read. */
    lateinit var result : Configuration

    /** A stack of configuration builders for descending sub-configurations. */
    private val stack = Stack<Pair<String, PieceWiseConfigurationBuilder>>()

    override fun begin(cls : KClass<out Configuration>) {
        // Add the overall configuration to the stack
        stack.push(Pair("", PieceWiseConfigurationBuilder(cls)))
    }

    override fun item(name : String, value : Any?, metadata : ConfigurationElement.Metadata) {
        // Initialise the current sub-configuration with the item
        stack.peek().second.append { setValueByName(name, value) }
    }

    override fun beginSubConfiguration(name : String, cls : KClass<out Configuration>, metadata : ConfigurationElement.Metadata) {
        // Push a new sub-configuration on to the stack
        stack.push(Pair(name, PieceWiseConfigurationBuilder(cls)))
    }

    override fun endSubConfiguration() {
        // Remove the name/builder for the sub-configuration from the stack
        val (subconfigurationName, subconfigurationBuilder) = stack.pop()

        // Add the builder as an initialiser for a sub-configuration
        stack.peek().second.append {
            setValueByName(subconfigurationName, subconfigurationBuilder.instantiate())
        }
    }

    override fun end() {
        // Pop the top-level configuration builder from the stack and instantiate
        result = stack.pop().second.instantiate()
    }

    /**
     * Helper class which allows building a configuration in a piece-wise
     * fashion.
     *
     * @param cls   The type of configuration to build.
     */
    private class PieceWiseConfigurationBuilder(val cls : KClass<out Configuration>) {

        /** The initialiser for the configuration so far. */
        var initialiser : Configuration.() -> Unit = lambda<Configuration.() -> Unit> {}

        /**
         * Adds more initialisation to the initialiser.
         *
         * @param block     The additional initialisation to perform.
         */
        fun append(block : Configuration.() -> Unit) {
            // Create a closure of the current state of the initialiser
            val current = initialiser

            // Set the initialiser to perform the current lot of initialisation,
            // followed by the new bit
            initialiser = {
                current()
                block()
            }
        }

        /**
         * Creates an instance of the configuration from the current state.
         *
         * @return  The configuration instance.
         */
        fun instantiate() : Configuration {
            return cls.initialise(initialiser)
        }
    }
}
