package maia.configure.error

/**
 * Error for when an attempt is made to modify a read-only configuration.
 */
class ReadOnlyModificationError : Exception("Attempted to modify a read-only configuration")
