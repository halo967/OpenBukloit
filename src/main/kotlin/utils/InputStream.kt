package utils

import java.io.InputStream

/**
 * Reads all bytes from an InputStream and returns them as a ByteArray.
 * Using .use {} ensures the stream is closed immediately after reading, 
 * which is critical when bulk-processing many JARs.
 */
fun getBytesFromInputStream(stream: InputStream): ByteArray {
    return stream.use { it.readBytes() }
}
