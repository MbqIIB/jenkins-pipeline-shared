package com.anchorfree;

def call(String goal = "fresh", String branch = "master") {
    def tokens = "${env.JOB_NAME}".tokenize('/')
    def repo = tokens[tokens.size() - 2]
    def sha = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
    def container_version = repo.replaceAll('-','_')+"_version"
    build job: "../ansible-hss-u2/${branch}", wait: false, parameters: [
        booleanParam(name: 'syntaxCheck', value: false),
        string(name: 'goal', value: goal),
        string(name: 'ansibleVars', value: "\"${container_version}\": \"${sha}\""),
        string(name: 'upstreamJobName', value: env.JOB_NAME),
        string(name: 'upstreamSHA', value: sha)
        ]
}
