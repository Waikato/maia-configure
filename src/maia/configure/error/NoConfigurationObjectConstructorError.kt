package maia.configure.error

import maia.configure.Configurable
import maia.configure.Configuration
import maia.configure.getConfigurationClassUntyped
import maia.util.createProjectedType
import kotlin.reflect.KClass

/**
 * Error for when a [Configurable] sub-type does not have a
 * constructor which only takes a [Configuration]-initialisation
 * block.
 *
 * @param cls   The [Configurable] sub-type.
 */
class NoConfigurationObjectConstructorError(
        cls : KClass<out Configurable<*>>
) : Exception(
        "$cls has no configuration-object constructor; " +
                "implementation is boiler-plate: " +
                "constructor(config : ${getConfigurationClassUntyped(cls).createProjectedType()}) : this(config.asReconfigureBlock())"
)
