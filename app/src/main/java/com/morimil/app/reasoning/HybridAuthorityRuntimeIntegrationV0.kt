package com.morimil.app.reasoning

import com.morimil.app.reasoning.authority.HybridAuthorityTaskKind
import java.text.Normalizer
import java.util.Locale

/**
 * Structured task kind carried from the kernel into the intrinsic coordinator.
 * Classification is conservative: unsupported or ambiguous prompts stay UNKNOWN.
 */
enum class ReasoningTaskKind {
    ARITHMETIC,
    RESTRICTED_CODE,
    CLAIM_VERIFICATION,
    LOGIC,
    SPANISH,
    INSTRUCTION,
    UNKNOWN
}

/**
 * Local, deterministic classifier for the bounded routes supported by
 * [com.morimil.app.reasoning.authority.HybridAuthorityRouterV0].
 */
object ReasoningTaskKindClassifierV0 {
    fun classify(input: String): ReasoningTaskKind {
        val prompt = normalize(input)
        if (prompt.isBlank()) return ReasoningTaskKind.UNKNOWN

        return when {
            CLAIM_PATTERNS.any { pattern -> pattern.containsMatchIn(prompt) } ->
                ReasoningTaskKind.CLAIM_VERIFICATION

            CODE_PATTERNS.any { pattern -> pattern.containsMatchIn(prompt) } ->
                ReasoningTaskKind.RESTRICTED_CODE

            ARITHMETIC_PATTERNS.any { pattern -> pattern.containsMatchIn(prompt) } ->
                ReasoningTaskKind.ARITHMETIC

            LOGIC_PATTERNS.any { pattern -> pattern.containsMatchIn(prompt) } ->
                ReasoningTaskKind.LOGIC

            SPANISH_PATTERNS.any { pattern -> pattern.containsMatchIn(prompt) } ->
                ReasoningTaskKind.SPANISH

            INSTRUCTION_PATTERNS.any { pattern -> pattern.matches(prompt) } ->
                ReasoningTaskKind.INSTRUCTION

            else -> ReasoningTaskKind.UNKNOWN
        }
    }

    private fun normalize(input: String): String {
        val decomposed = Normalizer.normalize(input.trim(), Normalizer.Form.NFKD)
        return decomposed
            .filterNot { character ->
                Character.getType(character) == Character.NON_SPACING_MARK.toInt()
            }
            .lowercase(Locale.ROOT)
    }

    private val CLAIM_PATTERNS = listOf(
        Regex(
            """afirma\s+que\s+-?\d+\s+por\s+-?\d+\s+es\s+-?\d+""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """afirma\s+que\s+-?\d+\s+dividido\s+entre\s+-?\d+\s+es\s+-?\d+""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """afirma\s+que\s+len\(\[[0-9,\s-]+]\)\s+es\s+-?\d+""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """todos\s+los\s+numeros\s+pares\s+son\s+impares""",
            RegexOption.IGNORE_CASE
        )
    )

    private val CODE_PATTERNS = listOf(
        Regex(
            """print\(sum\(\[[0-9,\s-]+]\)\)""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """print\(len\((['\"]).*?\1\)\)""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """x\s*=\s*-?\d+\s*;\s*x\s*\*=\s*-?\d+\s*;\s*print\(x\)""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """print\(\[i\*i\s+for\s+i\s+in\s+range\(\d+\)]\[-1]\)""",
            RegexOption.IGNORE_CASE
        )
    )

    private val ARITHMETIC_PATTERNS = listOf(
        Regex(
            """calcula\s+-?\d+\s*\+\s*-?\d+\s+por\s+-?\d+""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """calcula\s+-?\d+\s+dividido\s+entre\s+-?\d+.*luego\s+suma\s+-?\d+""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ),
        Regex(
            """calcula\s+-?\d+\s+menos\s+-?\d+\s+por\s+-?\d+""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """calcula\s+-?\d+\s+por\s+-?\d+""",
            RegexOption.IGNORE_CASE
        )
    )

    private val CLOSED_ORDER_ENTITY = "[a-z][a-z0-9_-]{0,31}"
    private val LOGIC_PATTERNS = listOf(
        Regex(
            """^$CLOSED_ORDER_ENTITY\s+llego\s+antes\s+que\s+$CLOSED_ORDER_ENTITY\s+y\s+$CLOSED_ORDER_ENTITY\s+antes\s+que\s+$CLOSED_ORDER_ENTITY\.\s*¿?quien\s+llego\s+(primero|ultimo)\s*\??$""",
            RegexOption.IGNORE_CASE
        ),
        Regex("""todos\s+los\s+.+\s+son\s+.+""", RegexOption.IGNORE_CASE),
        Regex("""ningun(?:a)?\s+.+\s+es\s+.+""", RegexOption.IGNORE_CASE),
        Regex("""algunos?\s+.+\s+son\s+.+""", RegexOption.IGNORE_CASE)
    )

    private val SPANISH_PATTERNS = listOf(
        Regex("""llego\s+antes\s+que""", RegexOption.IGNORE_CASE),
        Regex("""esta\s+dentro\s+de""", RegexOption.IGNORE_CASE),
        Regex("""cual\s+fue\s+el\s+motivo""", RegexOption.IGNORE_CASE),
        Regex("""a\s+la\s+izquierda\s+de""", RegexOption.IGNORE_CASE)
    )

    private const val INSTRUCTION_INTEGER = "(?:0|-?[1-9][0-9]{0,63})"
    private val INSTRUCTION_PATTERNS = listOf(
        Regex(
            """^devuelve\s+exactamente\s+final:(?:[a-z][a-z0-9_-]{0,31}|$INSTRUCTION_INTEGER)\s+y\s+nada\s+mas\.?$"""
        ),
        Regex(
            """^calcula\s+$INSTRUCTION_INTEGER\s*-\s*$INSTRUCTION_INTEGER\s+y\s+devuelve\s+exactamente\s+final:<resultado>\.?$"""
        )
    )
}

/** Runtime switch. Production construction keeps this false. */
data class HybridAuthorityRuntimePolicy(
    val hybridAuthorityRuntimeEnabled: Boolean = false
)

enum class TriMotorFinalizationStatus {
    LEGACY_UNROUTED,
    ACCEPTED_BY_AUTHORITY,
    ABSTAINED_BY_AUTHORITY
}

/**
 * Only routes backed by exact local computation may reach the authority router.
 *
 * LOGIC and INSTRUCTION are admitted only for their closed deterministic grammars.
 * SPANISH remains useful for motor selection and research telemetry, but generated
 * replies are not independent proof and must abstain at the final-authority boundary.
 */
internal fun ReasoningTaskKind.toHybridAuthorityTaskKind(): HybridAuthorityTaskKind {
    return when (this) {
        ReasoningTaskKind.ARITHMETIC -> HybridAuthorityTaskKind.ARITHMETIC
        ReasoningTaskKind.RESTRICTED_CODE -> HybridAuthorityTaskKind.RESTRICTED_CODE
        ReasoningTaskKind.CLAIM_VERIFICATION -> HybridAuthorityTaskKind.CLAIM_VERIFICATION
        ReasoningTaskKind.LOGIC -> HybridAuthorityTaskKind.LOGIC
        ReasoningTaskKind.INSTRUCTION -> HybridAuthorityTaskKind.INSTRUCTION
        ReasoningTaskKind.SPANISH,
        ReasoningTaskKind.UNKNOWN -> HybridAuthorityTaskKind.UNKNOWN
    }
}
