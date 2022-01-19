package maia.configure.error

import kotlin.reflect.KProperty

class ConfigurationElementMissingMetadataError(
        element : KProperty<*>
) : Exception("No meta-data found for configuration element $element")
