package māia.configure.error

import māia.configure.Configurable
import māia.configure.Configuration
import māia.configure.getConfigurationClassUntyped
import māia.util.createProjectedType
import kotlin.reflect.KClass

/**
 * Error for when a [Configurable] sub-type does not have a
 * constructor which only takes a [Configuration]-initialisation
 * block.
 *
 * @param cls   The [Configurable] sub-type.
 */
class NoConfigurationBlockConstructorError(
        cls : KClass<out Configurable<*>>
) : Exception(
        "$cls has no configuration-block constructor; " +
                "implementation is boiler-plate: " +
                "constructor(block : ${getConfigurationClassUntyped(cls).createProjectedType()}.() -> Unit = {}) : super(block)"
)
