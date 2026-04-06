package utils

import kotlin.random.Random

/**
 * Generates a random lowercase string of a specified length.
 * Used for camouflage fallbacks and temporary class naming.
 */
fun makeRandomLowerCaseString(length: Int): String {
    val charPool : List<Char> = ('a'..'z').toList()
    return (1..length)
        .map { Random.nextInt(0, charPool.size) }
        .map(charPool::get)
        .joinToString("")
}
