package com.klisee.reloaded.gmk.update

/**
 * Una versión con el formato que publica el repositorio: `0.6.7`, `0.7.0-beta1`, `1.0.0-rc.2`.
 *
 * Es una implementación propia del orden semver. La comparación de números sueltos que había antes
 * (`"0.6.7".split('.')`) trataba `0.10.0` como anterior a `0.9.0`, y no entendía las etiquetas de
 * preestreno, así que una beta podía ofrecerse como si fuera más nueva que la estable.
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: List<String> = emptyList(),
) : Comparable<AppVersion> {
    val isPrerelease: Boolean get() = prerelease.isNotEmpty()

    override fun compareTo(other: AppVersion): Int {
        major.compareTo(other.major).takeIf { it != 0 }?.let { return it }
        minor.compareTo(other.minor).takeIf { it != 0 }?.let { return it }
        patch.compareTo(other.patch).takeIf { it != 0 }?.let { return it }
        // Una versión sin etiqueta es posterior a cualquier preestreno del mismo número.
        if (!isPrerelease && other.isPrerelease) return 1
        if (isPrerelease && !other.isPrerelease) return -1
        for (index in 0 until maxOf(prerelease.size, other.prerelease.size)) {
            val left = prerelease.getOrNull(index) ?: return -1
            val right = other.prerelease.getOrNull(index) ?: return 1
            val leftNumber = left.toIntOrNull()
            val rightNumber = right.toIntOrNull()
            val order = when {
                leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                leftNumber != null -> -1
                rightNumber != null -> 1
                else -> left.compareTo(right, ignoreCase = true)
            }
            if (order != 0) return order
        }
        return 0
    }

    override fun toString(): String = buildString {
        append(major).append('.').append(minor).append('.').append(patch)
        if (prerelease.isNotEmpty()) append('-').append(prerelease.joinToString("."))
    }

    companion object {
        private val pattern = Regex(
            "^[vV]?(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$",
        )

        /** Acepta el prefijo `v` que usa GitHub (`v0.6.7`) y devuelve `null` si no es una versión. */
        fun parse(value: String): AppVersion? {
            val match = pattern.matchEntire(value.trim()) ?: return null
            val major = match.groupValues[1].toIntOrNull() ?: return null
            val minor = match.groupValues[2].toIntOrNull() ?: return null
            val patch = match.groupValues[3].toIntOrNull() ?: return null
            val prerelease = match.groupValues[4]
                .takeIf(String::isNotEmpty)
                // `beta1` y `beta.1` se ordenan igual; si no, "beta10" quedaría antes que "beta9".
                ?.replace(Regex("(?i)^(alpha|beta|rc)(\\d+)(?=\\.|$)"), "$1.$2")
                ?.split('.')
                .orEmpty()
            return AppVersion(major, minor, patch, prerelease)
        }

        /** `true` cuando [candidate] es posterior a [installed]; cualquier versión ilegible no cuenta. */
        fun isNewer(candidate: String, installed: String): Boolean {
            val left = parse(candidate) ?: return false
            val right = parse(installed) ?: return false
            return left > right
        }
    }
}
