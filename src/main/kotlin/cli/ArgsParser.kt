package cli

/**
 * Finds the value associated with a command-line argument.
 */
fun findArg(full: String, short: String? = null, args: Array<String>, last: Boolean = false): String? {
    val indices = args.indices.filter { i -> 
        args[i] == "--$full" || (short != null && args[i] == "-$short") 
    }
    if (indices.isEmpty()) return null

    val index = if (last) indices.last() else indices.first()
    val value = args.getOrNull(index + 1)

    // Safety: If the next "value" starts with a dash, it's likely another flag, not a value.
    if (value != null && value.startsWith("-")) return null

    return value
}

/**
 * Checks if a boolean flag argument is present.
 */
fun boolArg(full: String, short: String? = null, args: Array<String>): Boolean {
    return args.any { it == "--$full" || (short != null && it == "-$short") }
}
