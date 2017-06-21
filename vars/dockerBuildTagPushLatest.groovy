package com.anchorfree;

// From: https://github.com/jenkinsci/pipeline-examples/blob/master/pipeline-examples/github-org-plugin/access-repo-information.groovy
// github-organization-plugin jobs are named as 'org/repo/branch'
// we don't want to assume that the github-organization job is at the top-level
// instead we get the total number of tokens (size)
// and work back from the branch level Pipeline job where this would actually be run
// Note: that branch job is at -1 because Java uses zero-based indexing
def call() {
    def tokens = "${env.JOB_NAME}".tokenize('/')
    def org = tokens[tokens.size()-3]
    def repo = tokens[tokens.size() - 2]
    def branch = tokens[tokens.size() - 1]
    def sha = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()

    // if name starts with docker-, remove the prefix.
    if (repo.startsWith('docker-')) {
        repo = repo.substring(7)
    }

    def image_name = "${org}/${repo}"

    // this id will change if we change the credentials it's referencing
    println("Building ${image_name} on branch ${branch} at ${sha}")
    withDockerRegistry([credentialsId: 'd010b34b-8a68-4389-908d-88dc45a65fef']) {
        def img = docker.build("${image_name}", "--label com.anchorfree.commit=${sha} --label com.anchorfree.build=${env.BUILD_NUMBER} .")

        println("Pushing ${image_name}")
        img.push()
    }
}
