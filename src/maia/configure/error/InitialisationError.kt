package maia.configure.error

/**
 * Exception that occurs when there is a problem during initialisation
 * of a configuration.
 *
 * @param reason    The reason the error occurred.
 */
class InitialisationError(reason : String) : Exception(reason)
