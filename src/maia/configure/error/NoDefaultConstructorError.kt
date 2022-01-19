package maia.configure.error

import maia.configure.Configuration
import kotlin.reflect.KClass

/**
 * Error for when a [Configuration] class doesn't have
 * a default constructor.
 *
 * @param cls   The class of [Configuration].
 */
class NoDefaultConstructorError(
        cls : KClass<out Configuration>
) : Exception("$cls has no default constructor (one which takes no arguments)")
