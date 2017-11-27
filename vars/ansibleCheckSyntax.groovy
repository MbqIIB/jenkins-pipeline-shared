#!/usr/bin/env groovy
package com.anchorfree;
import com.anchorfree.AnsibleTowerApi

// From: https://github.com/jenkinsci/pipeline-examples/blob/master/pipeline-examples/github-org-plugin/access-repo-information.groovy
// github-organization-plugin jobs are named as 'org/repo/branch'
// we don't want to assume that the github-organization job is at the top-level
// instead we get the total number of tokens (size)
// and work back from the branch level Pipeline job where this would actually be run
// Note: that branch job is at -1 because Java uses zero-based indexing

def call(String[] playbooks, String inventory_file) {
    def tokens = "${env.JOB_NAME}".tokenize('/')
    def org = tokens[tokens.size()-3]
    def repo = tokens[tokens.size() - 2]
    def branch = tokens[tokens.size() - 1].replaceAll('%2F','-')
    def sha = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
    def scm_path = "${org}/${repo}"
    // if name starts with ansible-, remove the prefix.
    if (repo.startsWith('ansible-')) {
        repo = repo.substring(8)
    }
    def name = "${repo}(${sha.substring(0,7)})" // general name of awx's objects
    def unlock_playbook = "unlock.yml"
    def unlock_vars = "git_crypt_key_path: /tmp/hssunified-ubuntu.key"
    def awx_host = "awx.afdevops.com"
    def awx_cred_id = 2 // for github pulling
    def awx_ssh_id = 6 // for servers' access
    def awx_org_id = 2
    def unlock_required = fileExists(unlock_playbook)

    withCredentials([usernamePassword(credentialsId: 'awx', usernameVariable: 'user', passwordVariable: 'password')]) {
        echo("Creation of awx project and inventory")
        def awx = new AnsibleTowerApi(awx_host, user, password, this)
        def project = awx.createProject(name, scm_path, sha, awx_cred_id, awx_org_id)
        def inventory = project.createInventory(name, inventory_file)
        project.waitSuccessStatus()

        if (unlock_required) {
            echo("Repositry unlocking")
            def unlock = awx.createJobTemplate("${name} - unlock", "run", 
                unlock_playbook, awx_ssh_id, unlock_vars, project, inventory)
            unlock.launch()
            unlock.waitSuccessStatus()
            unlock.stdout()
            unlock.remove()  
        }
        
        playbooks.each { playbook ->
            echo("Dry run of ${playbook}")
            def dry_run = awx.createJobTemplate("${name} - ${playbook} dry_run", "check",
                playbook, awx_ssh_id, "", project, inventory)
            dry_run.launch()
            dry_run.waitSuccessStatus()
            dry_run.stdout()
            dry_run.remove()
        }

        inventory.remove()
        project.remove()

        awx.checkOverallStatus()
    }
}