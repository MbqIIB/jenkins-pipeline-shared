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

    // Never build for "magic" tags. If you want to build for these tags, use BBCDdockerBuildTagPush instead
    if (branch in ["latest", "prod", "volunteer", "oneserver", "canary", "stage"]) {
        error("I am not allowed to build the '${branch}' branch.")
    }

    // if name starts with docker-, remove the prefix.
    if (repo.startsWith('docker-')) {
        repo = repo.substring(7)
    }

    def image_name = "${org}/${repo}"
    def image_sha_tag = "${image_name}:${sha}"

    // this id will change if we change the credentials it's referencing
    withDockerRegistry([credentialsId: 'd010b34b-8a68-4389-908d-88dc45a65fef']) {
        println("Building ${image_name}:${branch} at ${sha}")
        def img = docker.build("${image_sha_tag}", "--label com.anchorfree.commit=${sha} --label com.anchorfree.build=${env.BUILD_NUMBER} .")

        println("Pushing ${image_sha_tag}")
        img.push()

        println("Pushing ${image_name}:${branch}")
        img.push branch
    }
}
