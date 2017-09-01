package com.anchorfree;

// From: https://github.com/jenkinsci/pipeline-examples/blob/master/pipeline-examples/github-org-plugin/access-repo-information.groovy
// github-organization-plugin jobs are named as 'org/repo/branch'
// we don't want to assume that the github-organization job is at the top-level
// instead we get the total number of tokens (size)
// and work back from the branch level Pipeline job where this would actually be run
// Note: that branch job is at -1 because Java uses zero-based indexing
def call() {
    def tokens = "${env.JOB_NAME}".tokenize('/')
    def org = tokens[tokens.size() - 3]
    def repo = tokens[tokens.size() - 2]
    def branch = tokens[tokens.size() - 1]
    def sha = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
    def tags = sh(returnStdout: true, script: 'git tag -l --points-at HEAD').tokenize("\n").collect { it.trim() }
    def tags_string = tags.join(", ")
    println("tags [${tags_string}]")

    // if name starts with docker-, remove the prefix.
    if (repo.startsWith('docker-')) {
        repo = repo.substring(7)
    }

    def image_name = "${org}/${repo}"
    def image_sha_tag = "${image_name}:${sha}"

    withDockerRegistry([credentialsId: 'dockerhub']) {

        // If there are tags, first try docker pull based on the SHA.
        // If the container can be pulled, apply the tag(s) and push.
        def img_pulled = false
        def img = docker.image("${image_name}:${sha}")

        if (tags.size() > 0) {
            println("Pulling ${image_name}:${branch} at ${sha}")
            try {
                img.pull()
                img_pulled = true
            } catch (Exception e){
                println("Failed to pull: ${e}")
            }
        }

        // If I can't pull the image, I need to build it.
        if (!img_pulled) {
            // Never build for "magic" branches. If you want to build for these branches, use BBCDdockerBuildTagPush instead.
            if (branch in ["latest", "prod", "volunteer", "oneserver", "canary", "stage"]) {
                error("I am not allowed to build the '${branch}' branch.")
            }
            println("Building ${image_name}:${branch} at ${sha}")
            img = docker.build("${image_sha_tag}", "--label com.anchorfree.commit=${sha} --label com.anchorfree.build=${env.BUILD_NUMBER} .")

            println("Pushing ${image_sha_tag}")
            img.push()

            println("Pushing ${image_name}:${branch}")
            img.push branch
        }

        tags.each {
            println("Pushing ${image_name}:${it}")
            img.push it
        }
    }
}
