package com.morimil.app.github

data class GitHubRepoStatus(
    val fullName: String,
    val privateRepo: Boolean,
    val defaultBranch: String,
    val visibility: String,
    val htmlUrl: String
)
