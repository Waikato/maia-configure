package māia.configure.error

import māia.configure.Configurable
import kotlin.reflect.KClass

/**
 * Error for when the configuration class of a configurable class
 * is not registered.
 *
 * @param cls   The configurable class that is not registered.
 */
class ConfigurableNotRegisteredError(
        cls : KClass<out Configurable<*>>
) : Exception("$cls is not registered. Annotate it with the ${Configurable.Register::class}")
