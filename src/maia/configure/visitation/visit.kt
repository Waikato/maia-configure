package maia.configure.visitation

/**
 * Allows a configuration-visitor to visit a configuration-visitable.
 *
 * @receiver            The configuration-visitor.
 * @param visitable     The configuration-visitable.
 * @return              The visitor (chainable).
 * @param CVr           The type of the visitor.
 * @param CVe           The type of the visitable.
 */
fun <CVr : ConfigurationVisitor, CVe : ConfigurationVisitable> CVr.visit(visitable : CVe) : CVr {
    // Defer to the reverse call
    visitable.visit(this)

    return this
}

/**
 * Allows a configuration-visitable to be visited by a configuration-visitor.
 *
 * @receiver        The configuration-visitable.
 * @param visitor   The configuration-visitor.
 * @return          The visitable (chainable).
 * @param CVe       The type of the visitable.
 * @param CVr       The type of the visitor.
 */
fun <CVe : ConfigurationVisitable, CVr : ConfigurationVisitor> CVe.visit(visitor : CVr) : CVe {
    visitor.begin(getConfigurationClass())
    visitNoBegin(visitor)
    visitor.end()
    return this
}

/**
 * Performs visitation without the [ConfigurationVisitor.begin]/[ConfigurationVisitor.end]
 * calls, so that the implementation can be recursively used for sub-configurations.
 *
 * @receiver        The configuration-visitable.
 * @param visitor   The configuration-visitor.
 */
internal fun ConfigurationVisitable.visitNoBegin(visitor : ConfigurationVisitor) {
    // Visit each top-level element in turn
    for (element in iterateElements()) {
        // If the element is a simple item, let the visitor visit it
        if (element is ConfigurationVisitable.Element.Item)
            visitor.item(element.name, element.value, element.metadata)

        // Otherwise it is a sub-configuration
        else {
            // Get the value of the sub-configuration visitable
            val sub = (element as ConfigurationVisitable.Element.SubConfiguration).value

            // Let the visitor know we are beginning a sub-configuration
            visitor.beginSubConfiguration(element.name, sub.getConfigurationClass(), element.metadata)

            // Recursively visit the sub-configuration
            sub.visitNoBegin(visitor)

            // Let the visitor know we are ending the sub-configuration
            visitor.endSubConfiguration()
        }
    }
}
