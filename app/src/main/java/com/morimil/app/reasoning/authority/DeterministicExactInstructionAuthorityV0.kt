package com.morimil.app.reasoning.authority

import java.util.Locale

/**
 * Exact, fail-closed authority for two bounded Spanish instruction forms.
 *
 * Supported forms:
 *
 * `Devuelve exactamente FINAL:AZUL y nada más.`
 * `Calcula 12 - 5 y devuelve exactamente FINAL:<resultado>.`
 *
 * The whole prompt must match. Output markers, literal values and integers must already be
 * canonical. Extra prose, multiple lines, non-canonical values and unsupported operations
 * abstain.
 */
internal object DeterministicExactInstructionAuthorityV0 {
    private const val MAX_PROMPT_CHARS = 256
    private const val CANONICAL_INTEGER = "(?:0|-?[1-9][0-9]{0,63})"
    private const val CANONICAL_TOKEN = "[A-Z][A-Z0-9_-]{0,31}"

    private val canonicalLiteralValue = Regex("(?:$CANONICAL_TOKEN|$CANONICAL_INTEGER)")
    private val literalInstruction = Regex(
        """^devuelve\s+exactamente\s+(FINAL:($CANONICAL_TOKEN|$CANONICAL_INTEGER))\s+y\s+nada\s+m[aá]s\.?$""",
        RegexOption.IGNORE_CASE
    )
    private val subtractionInstruction = Regex(
        """^calcula\s+($CANONICAL_INTEGER)\s*-\s*($CANONICAL_INTEGER)\s+y\s+devuelve\s+exactamente\s+(FINAL:<resultado>)\.?$""",
        RegexOption.IGNORE_CASE
    )

    fun solve(prompt: String): DeterministicAuthorityResult {
        if (prompt.length > MAX_PROMPT_CHARS) {
            return DeterministicAuthorityResult.unsupported(
                "deterministic_exact_instruction_prompt_too_long"
            )
        }
        if (prompt.indexOf('\u0000') >= 0 || prompt.contains('\n') || prompt.contains('\r')) {
            return DeterministicAuthorityResult.unsupported(
                "deterministic_exact_instruction_multiline_or_nul"
            )
        }

        val logical = prompt.trim()
        literalInstruction.matchEntire(logical)?.let { match ->
            val marker = match.groupValues[1]
            val value = match.groupValues[2]
            if (marker != "FINAL:$value" || !canonicalLiteralValue.matches(value)) {
                return DeterministicAuthorityResult.unsupported(
                    "deterministic_exact_instruction_literal_not_canonical"
                )
            }
            return DeterministicAuthorityResult.acceptedToken(
                value = value,
                reason = "deterministic_exact_instruction_literal",
                trace = "literal=${value.uppercase(Locale.ROOT)}"
            )
        }

        subtractionInstruction.matchEntire(logical)?.let { match ->
            val marker = match.groupValues[3]
            if (marker != "FINAL:<resultado>") {
                return DeterministicAuthorityResult.unsupported(
                    "deterministic_exact_instruction_placeholder_not_canonical"
                )
            }
            val left = match.groupValues[1].toBigInteger()
            val right = match.groupValues[2].toBigInteger()
            val result = left - right
            return DeterministicAuthorityResult.accepted(
                value = result,
                reason = "deterministic_exact_instruction_subtraction",
                trace = "$left-$right=$result;format=FINAL:<resultado>"
            )
        }

        return DeterministicAuthorityResult.unsupported(
            "deterministic_exact_instruction_prompt_unsupported"
        )
    }
}
