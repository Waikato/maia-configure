package māia.configure.error

/**
 * Exception for when an attempt is made to use a configuration in an
 * unintended manner during initialisation/reconfiguration.
 */
class IntermediateStateError : Exception("Attempted to use a configuration in an intermediate state")
