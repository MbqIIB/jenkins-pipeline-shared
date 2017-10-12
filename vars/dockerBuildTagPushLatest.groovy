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
    def branch = tokens[tokens.size() - 1].replaceAll('%2F','-')
    def sha = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
    def latest_tag = sh(returnStdout: true, script: 'git tag -l --points-at HEAD').trim()

    // if name starts with docker-, remove the prefix.
    if (repo.startsWith('docker-')) {
        repo = repo.substring(7)
    }

    def image_name = "${org}/${repo}"

    // this id will change if we change the credentials it's referencing
    println("Building ${image_name} on branch ${branch} at ${sha}")
    withDockerRegistry([credentialsId: 'dockerhub']) {
        def img = docker.build("${image_name}", "--label com.anchorfree.commit=${sha} --label com.anchorfree.build=${env.BUILD_NUMBER} .")

        println("Pushing ${image_name}")
        img.push()
        
        // If the commit for which we are building is tagged, then 
        // we want to tag the image with the git tag as well
        if (latest_tag) {
            println("Pushing ${image_name}:${latest_tag}")
            img.push latest_tag
        }

    }
}
