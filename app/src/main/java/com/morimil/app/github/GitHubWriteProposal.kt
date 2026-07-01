package com.morimil.app.github

data class GitHubWriteProposal(
    val owner: String,
    val repo: String,
    val branch: String,
    val path: String,
    val commitMessage: String,
    val content: String,
    val humanApproved: Boolean
) {
    val target: String
        get() = "$owner/$repo:$branch/$path"
}
