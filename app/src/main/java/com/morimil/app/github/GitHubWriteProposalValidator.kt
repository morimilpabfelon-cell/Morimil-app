package com.morimil.app.github

class GitHubWriteProposalValidator {
    fun validate(proposal: GitHubWriteProposal): List<String> {
        val errors = mutableListOf<String>()

        if (proposal.owner != ALLOWED_OWNER) {
            errors += "Owner must be $ALLOWED_OWNER."
        }
        if (proposal.repo != ALLOWED_REPO) {
            errors += "Repo must be $ALLOWED_REPO."
        }
        if (proposal.branch != ALLOWED_BRANCH) {
            errors += "Branch must be $ALLOWED_BRANCH."
        }
        if (!proposal.path.matches(SAFE_PATH)) {
            errors += "Path contains unsafe characters."
        }
        if (proposal.path.startsWith("/") || proposal.path.contains("..")) {
            errors += "Path traversal is not allowed."
        }
        if (!proposal.path.startsWith(ALLOWED_PREFIX)) {
            errors += "Only docs/proposals/ writes can be proposed in Phase 5C."
        }
        if (proposal.commitMessage.isBlank()) {
            errors += "Commit message is required."
        }
        if (proposal.content.isBlank()) {
            errors += "Content is required."
        }
        if (proposal.content.length > MAX_CONTENT_LENGTH) {
            errors += "Content exceeds Phase 5C preview limit."
        }
        if (!proposal.humanApproved) {
            errors += "Human approval checkbox is required."
        }

        return errors
    }

    companion object {
        const val ALLOWED_OWNER = "morimilpabfelon-cell"
        const val ALLOWED_REPO = "Morimil-app"
        const val ALLOWED_BRANCH = "main"
        const val ALLOWED_PREFIX = "docs/proposals/"
        private const val MAX_CONTENT_LENGTH = 4_000
        private val SAFE_PATH = Regex("^[A-Za-z0-9_./-]+$")
    }
}
