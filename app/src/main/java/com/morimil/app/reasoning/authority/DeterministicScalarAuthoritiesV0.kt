package com.morimil.app.reasoning.authority

import java.math.BigInteger

internal object DeterministicArithmeticAuthorityV0 {
    private val addThenMultiply = Regex(
        """calcula\s+(-?\d+)\s*\+\s*(-?\d+)\s+por\s+(-?\d+)""",
        RegexOption.IGNORE_CASE
    )
    private val divideThenAdd = Regex(
        """calcula\s+(-?\d+)\s+dividido\s+entre\s+(-?\d+).*luego\s+suma\s+(-?\d+)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val subtractThenMultiply = Regex(
        """calcula\s+(-?\d+)\s+menos\s+(-?\d+)\s+por\s+(-?\d+)""",
        RegexOption.IGNORE_CASE
    )
    private val multiply = Regex(
        """calcula\s+(-?\d+)\s+por\s+(-?\d+)""",
        RegexOption.IGNORE_CASE
    )

    fun solve(prompt: String): DeterministicAuthorityResult {
        addThenMultiply.find(prompt)?.let { match ->
            val left = match.groupValues[1].toBigInteger()
            val middle = match.groupValues[2].toBigInteger()
            val right = match.groupValues[3].toBigInteger()
            val result = left + middle * right
            return DeterministicAuthorityResult.accepted(
                value = result,
                reason = "deterministic_arithmetic_add_multiply",
                trace = "$left+($middle*$right)=$result"
            )
        }

        divideThenAdd.find(prompt)?.let { match ->
            val numerator = match.groupValues[1].toBigInteger()
            val denominator = match.groupValues[2].toBigInteger()
            val addend = match.groupValues[3].toBigInteger()
            if (denominator == BigInteger.ZERO) {
                return DeterministicAuthorityResult.unsupported("deterministic_division_by_zero")
            }
            val division = numerator.divideAndRemainder(denominator)
            if (division[1] != BigInteger.ZERO) {
                return DeterministicAuthorityResult.unsupported("deterministic_non_integer_division")
            }
            val result = division[0] + addend
            return DeterministicAuthorityResult.accepted(
                value = result,
                reason = "deterministic_arithmetic_divide_add",
                trace = "($numerator/$denominator)+$addend=$result"
            )
        }

        subtractThenMultiply.find(prompt)?.let { match ->
            val left = match.groupValues[1].toBigInteger()
            val middle = match.groupValues[2].toBigInteger()
            val right = match.groupValues[3].toBigInteger()
            val result = left - middle * right
            return DeterministicAuthorityResult.accepted(
                value = result,
                reason = "deterministic_arithmetic_subtract_multiply",
                trace = "$left-($middle*$right)=$result"
            )
        }

        multiply.find(prompt)?.let { match ->
            val left = match.groupValues[1].toBigInteger()
            val right = match.groupValues[2].toBigInteger()
            val result = left * right
            return DeterministicAuthorityResult.accepted(
                value = result,
                reason = "deterministic_arithmetic_multiply",
                trace = "$left*$right=$result"
            )
        }

        return DeterministicAuthorityResult.unsupported("deterministic_arithmetic_prompt_unsupported")
    }
}

internal object RestrictedCodeAuthorityV0 {
    private val sumList = Regex(
        """print\(sum\(\[([0-9,\s-]+)]\)\)""",
        RegexOption.IGNORE_CASE
    )
    private val stringLength = Regex(
        """print\(len\((['\"])(.*?)\1\)\)""",
        RegexOption.IGNORE_CASE
    )
    private val multiplyAssignment = Regex(
        """x\s*=\s*(-?\d+)\s*;\s*x\s*\*=\s*(-?\d+)\s*;\s*print\(x\)""",
        RegexOption.IGNORE_CASE
    )
    private val squareComprehension = Regex(
        """print\(\[i\*i\s+for\s+i\s+in\s+range\((\d+)\)]\[-1]\)""",
        RegexOption.IGNORE_CASE
    )

    fun solve(prompt: String): DeterministicAuthorityResult {
        sumList.find(prompt)?.let { match ->
            val values = parseIntegerList(match.groupValues[1])
                ?: return DeterministicAuthorityResult.unsupported("restricted_code_integer_list_invalid")
            val result = values.fold(BigInteger.ZERO, BigInteger::add)
            return DeterministicAuthorityResult.accepted(
                value = result,
                reason = "restricted_code_sum",
                trace = "sum(${values.joinToString(",")})=$result"
            )
        }

        stringLength.find(prompt)?.let { match ->
            val value = match.groupValues[2]
            val result = value.codePointCount(0, value.length).toBigInteger()
            return DeterministicAuthorityResult.accepted(
                value = result,
                reason = "restricted_code_string_length",
                trace = "len(${value.length})=$result"
            )
        }

        multiplyAssignment.find(prompt)?.let { match ->
            val initial = match.groupValues[1].toBigInteger()
            val multiplier = match.groupValues[2].toBigInteger()
            val result = initial * multiplier
            return DeterministicAuthorityResult.accepted(
                value = result,
                reason = "restricted_code_multiply_assignment",
                trace = "$initial*$multiplier=$result"
            )
        }

        squareComprehension.find(prompt)?.let { match ->
            val stop = match.groupValues[1].toBigInteger()
            if (stop <= BigInteger.ZERO) {
                return DeterministicAuthorityResult.unsupported("restricted_code_empty_comprehension")
            }
            val last = stop - BigInteger.ONE
            val result = last * last
            return DeterministicAuthorityResult.accepted(
                value = result,
                reason = "restricted_code_square_comprehension",
                trace = "(${stop}-1)^2=$result"
            )
        }

        return DeterministicAuthorityResult.unsupported("restricted_code_prompt_unsupported")
    }

    private fun parseIntegerList(raw: String): List<BigInteger>? {
        if (raw.isBlank()) return emptyList()
        return raw.split(',').map { item ->
            item.trim().takeIf { value -> INTEGER.matches(value) }?.toBigInteger()
                ?: return null
        }
    }

    private val INTEGER = Regex("-?\\d+")
}

internal object DeterministicClaimAuthorityV0 {
    private val multiplicationClaim = Regex(
        """afirma\s+que\s+(-?\d+)\s+por\s+(-?\d+)\s+es\s+(-?\d+)""",
        RegexOption.IGNORE_CASE
    )
    private val listLengthClaim = Regex(
        """len\(\[([0-9,\s-]+)]\)\s+es\s+(-?\d+)""",
        RegexOption.IGNORE_CASE
    )
    private val divisionClaim = Regex(
        """afirma\s+que\s+(-?\d+)\s+dividido\s+entre\s+(-?\d+)\s+es\s+(-?\d+)""",
        RegexOption.IGNORE_CASE
    )

    fun solve(prompt: String): DeterministicAuthorityResult {
        multiplicationClaim.find(prompt)?.let { match ->
            val left = match.groupValues[1].toBigInteger()
            val right = match.groupValues[2].toBigInteger()
            val claimed = match.groupValues[3].toBigInteger()
            val result = left * right
            return DeterministicAuthorityResult.accepted(
                value = result,
                reason = "deterministic_claim_multiplication",
                trace = "claim=$claimed;actual=$left*$right=$result"
            )
        }

        listLengthClaim.find(prompt)?.let { match ->
            val values = match.groupValues[1].split(',').map { it.trim() }
            if (values.any { !INTEGER.matches(it) }) {
                return DeterministicAuthorityResult.unsupported("deterministic_claim_list_invalid")
            }
            val claimed = match.groupValues[2].toBigInteger()
            val result = values.size.toBigInteger()
            return DeterministicAuthorityResult.accepted(
                value = result,
                reason = "deterministic_claim_list_length",
                trace = "claim=$claimed;actual=len=${values.size}"
            )
        }

        if (prompt.contains("todos los numeros pares son impares", ignoreCase = true)) {
            return DeterministicAuthorityResult.acceptedToken(
                value = "NO",
                reason = "deterministic_claim_even_odd",
                trace = "counterexample=2"
            )
        }

        divisionClaim.find(prompt)?.let { match ->
            val numerator = match.groupValues[1].toBigInteger()
            val denominator = match.groupValues[2].toBigInteger()
            val claimed = match.groupValues[3].toBigInteger()
            if (denominator == BigInteger.ZERO) {
                return DeterministicAuthorityResult.unsupported("deterministic_claim_division_by_zero")
            }
            val division = numerator.divideAndRemainder(denominator)
            if (division[1] != BigInteger.ZERO) {
                return DeterministicAuthorityResult.unsupported("deterministic_claim_non_integer_division")
            }
            val result = division[0]
            return DeterministicAuthorityResult.accepted(
                value = result,
                reason = "deterministic_claim_division",
                trace = "claim=$claimed;actual=$numerator/$denominator=$result"
            )
        }

        return DeterministicAuthorityResult.unsupported("deterministic_claim_prompt_unsupported")
    }

    private val INTEGER = Regex("-?\\d+")
}
