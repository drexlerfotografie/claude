package de.mediclever.tagebuch.data

import java.security.MessageDigest
import java.security.SecureRandom

/** PIN-Sperre der Oberflaeche. Gespeichert wird nur ein gesalzener Hash, nie die PIN selbst. */
object Pin {
    fun newSalt(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    fun hash(pin: String, salt: String): String {
        var bytes = (salt + ":" + pin).toByteArray()
        // mehrere Runden bremsen das Durchprobieren, falls der Hash je ausgelesen wird
        repeat(10_000) { bytes = MessageDigest.getInstance("SHA-256").digest(bytes) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun matches(pin: String, salt: String, hash: String): Boolean =
        hash.isNotEmpty() && MessageDigest.isEqual(hash(pin, salt).toByteArray(), hash.toByteArray())

    fun isValid(pin: String): Boolean = pin.length in 4..8 && pin.all { it.isDigit() }
}
