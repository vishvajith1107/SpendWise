package com.spendwise.domain.security

import java.security.MessageDigest

object PinSecurityHelper {
    private const val SALT = "spendwise_local_pin_v1"

    fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$SALT:$pin".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyPin(pin: String, storedHash: String?): Boolean {
        if (storedHash.isNullOrBlank()) return false
        return hashPin(pin) == storedHash
    }
}
