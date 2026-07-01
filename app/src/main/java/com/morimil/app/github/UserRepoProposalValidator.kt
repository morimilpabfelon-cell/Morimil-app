package com.morimil.app.github

class UserRepoProposalValidator {
    fun validate(
        owner: String,
        repoName: String,
        approved: Boolean
    ): List<String> {
        val errors = mutableListOf<String>()
        if (!owner.matches(SAFE_NAME)) {
            errors += "Owner contains unsafe characters."
        }
        if (!repoName.matches(SAFE_NAME)) {
            errors += "Repo name contains unsafe characters."
        }
        if (repoName.equals("Morimil", ignoreCase = true)) {
            errors += "Genesis repo cannot be used as a user workspace target."
        }
        if (repoName.equals("Morimil-app", ignoreCase = true)) {
            errors += "Morimil-app cannot be used as runtime workspace storage."
        }
        if (!approved) {
            errors += "Human approval is required before a repo proposal can pass."
        }
        return errors
    }

    companion object {
        private val SAFE_NAME = Regex("^[A-Za-z0-9_.-]{1,100}$")
    }
}
