package com.example.appcolegios.data

fun main() {
    // Simple verification for sanitizeFields without JUnit to avoid missing test deps in the environment
    val repo = FirestoreRepository()
    val largeString = "a".repeat(300_000)
    val fields = mapOf<String, Any?>(
        "name" to "John",
        "photoBase64" to "data:image/jpeg;base64,AAA...",
        "bio" to largeString,
        "email" to "john@example.com"
    )

    val sanitized = repo.sanitizeFields(fields)
    if (sanitized["name"] != "John") throw AssertionError("name mismatch")
    if (sanitized["email"] != "john@example.com") throw AssertionError("email mismatch")
    if (sanitized.containsKey("photoBase64")) throw AssertionError("photoBase64 not removed")
    if (sanitized.containsKey("bio")) throw AssertionError("bio not removed")

    println("FirestoreRepository.sanitizeFields self-check passed")
}
