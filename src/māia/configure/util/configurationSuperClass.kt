package māia.configure.util

import māia.configure.Configuration
import māia.util.property.CachedReadOnlyProperty
import māia.util.isSubClassOf
import kotlin.reflect.KClass
import kotlin.reflect.full.superclasses

/**
 * Gets the configuration class that the given class sub-types.
 *
 * @throws Exception    If called on the [Configuration] class itself.
 */
val KClass<out Configuration>.configurationSuperClass : KClass<out Configuration> by CachedReadOnlyProperty(
        cacheInitialiser = {
            // If the Configuration class itself is given, no super-class exists
            if (this == Configuration::class) throw Exception("Configuration has no configuration super-class")

            // Return the super-class that is a sub-class of Configuration (guaranteed only one
            // due to single-inheritance)
            @Suppress("UNCHECKED_CAST")
            this.superclasses.first {
                it isSubClassOf Configuration::class
            } as KClass<out Configuration>
        }
)
