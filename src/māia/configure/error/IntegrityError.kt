package māia.configure.error

/**
 * Error for when the integrity of a configuration is violated.
 *
 * @author Corey Sterling (csterlin at waikato dot ac dot nz)
 */
class IntegrityError(message : String?, cause : Throwable?) : Exception(message, cause) {

    constructor(message: String) : this(message, null)

    constructor() : this(null, null)

    constructor(cause : Throwable) : this(null, cause)

}
