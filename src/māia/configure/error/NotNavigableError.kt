package māia.configure.error

/**
 * Error for when trying to resolve a dotted name of a configuration element,
 * and one of the intermediary names is not a sub-configuration (and therefore
 * doesn't have sub-elements to navigate through).
 *
 * @param fullName  The full dotted name that was being navigated.
 * @param problemElement    The name in [fullName] that caused the error.
 */
class NotNavigableError(
        val fullName : String,
        val problemElement : String
) : Exception("$problemElement is not a sub-configuration, and therefore can't have sub-members (in $fullName)")
