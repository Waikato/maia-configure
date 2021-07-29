package māia.configure.util

import māia.configure.error.AbsentError
import māia.util.ThenContinuationWithFailureValue
import māia.util.ThenContinuationWithSuccessValue

/**
 * Attempts to access an element of a configuration. Returns an object which
 * allows for an action to be taken if the value is not absent.
 *
 * @param accessor  A function which accesses the value of a configuration element.
 * @return          A continuation object which provides a "then"
 *                  method, which will be executed in the case the value is not absent.
 * @param T         The type of the configuration element being accessed.
 */
inline fun <T> ifNotAbsent(accessor : () -> T) : ThenContinuationWithSuccessValue<T> {
    return try {
        ThenContinuationWithSuccessValue(accessor())
    } catch (e : AbsentError) {
        ThenContinuationWithSuccessValue()
    }
}

/**
 * Attempts to access an element of a configuration. Returns an object which
 * allows for an action to be taken if the value is absent.
 *
 * @param accessor  A function which accesses the value of a configuration element.
 * @return          A continuation object which provides a "then"
 *                  method, which will be executed in the case the value is absent.
 * @param T         The type of the configuration element being accessed.
 */
inline fun <T> ifAbsent(accessor : () -> T) : ThenContinuationWithFailureValue<T> {
    return try {
        ThenContinuationWithFailureValue(accessor())
    } catch (e : AbsentError) {
        ThenContinuationWithFailureValue()
    }
}
