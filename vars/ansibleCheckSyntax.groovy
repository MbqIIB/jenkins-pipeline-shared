#!/usr/bin/env groovy
package com.anchorfree;
import com.anchorfree.AnsibleTowerApi

// From: https://github.com/jenkinsci/pipeline-examples/blob/master/pipeline-examples/github-org-plugin/access-repo-information.groovy
// github-organization-plugin jobs are named as 'org/repo/branch'
// we don't want to assume that the github-organization job is at the top-level
// instead we get the total number of tokens (size)
// and work back from the branch level Pipeline job where this would actually be run
// Note: that branch job is at -1 because Java uses zero-based indexing

def call(String[] playbooks = ["playbook.yml"] , String inventory_file = "inventory") {
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
    def unlock_required = fileExists(unlock_playbook)    

    /*** Credentials which used from Jenkins credentials store:
    * awx                       - Creds for access to Ansible Tower API
    * awx_git_crypt_key_path    - Path for standard git-crypt key on AWX host
    * awx_host                  - AWX DNS entrypoint
    * awx_git_cred              - Credential's ID which stored on AWX (github's key of 'aftower' user)
    * awx_ssh_cred              - Credential's ID which stored on AWX ('aftower' ssh key)
    * awx_org                   - ID of Ansible Tower's Organization (aka AnchorFree)
    */

    /* As soon Jenkins will masks all strings which match with secrets values
    * we can faced with a lot of * symbols in playbooks' output.
    * It happens because next credential's variables is a digits.
    * It is not really sensitive so we make it insecure (move out from security block)
    * and we make playbooks's result more readable.
    */
    def awx_git_cred, awx_ssh_cred, awx_org
    withCredentials([string(credentialsId: 'awx_git_cred', variable: 'awx_git_cred_id'),
                    string(credentialsId: 'awx_ssh_cred', variable: 'awx_ssh_cred_id'),
                    string(credentialsId: 'awx_org', variable: 'awx_org_id')]) {
        awx_git_cred = awx_git_cred_id
        awx_ssh_cred = awx_ssh_cred_id
        awx_org = awx_org_id
    }

    withCredentials([usernamePassword(credentialsId: 'awx', usernameVariable: 'user', passwordVariable: 'password'),
            string(credentialsId: 'awx_git_crypt_key_path', variable: 'awx_git_crypt_key_path'),
            string(credentialsId: 'awx_host', variable: 'awx_host')]) {

        def unlock_vars = "git_crypt_key_path: ${awx_git_crypt_key_path}"

        echo("Creation of awx project")
        def awx = new AnsibleTowerApi(awx_host, user, password, this)
        def project = awx.createProject(name, scm_path, sha, awx_git_cred, awx_org)
        project.waitSuccessStatus()
        project.stdout()

        echo("Creation of awx inventory")
        def inventory = project.createInventory(name, inventory_file)
        inventory.launch()
        inventory.waitSuccessStatus()
        inventory.stdout()

        if (unlock_required) {
            echo("Repositry unlocking")
            def unlock = awx.createJobTemplate("${name} - unlock", "run", 
                unlock_playbook, awx_ssh_cred, unlock_vars, project, inventory)
            unlock.launch()
            unlock.waitSuccessStatus()
            unlock.stdout()
            unlock.remove()  
        }
        
        playbooks.each { playbook ->
            echo("Dry run of ${playbook}")
            def dry_run = awx.createJobTemplate("${name} - ${playbook} dry_run", "check",
                playbook, awx_ssh_cred, "", project, inventory)
            dry_run.launch()
            dry_run.waitSuccessStatus()
            dry_run.stdout()
            dry_run.remove()
        }

        inventory.remove()
        project.remove()

        echo("Verify overall result of syntax check")
        awx.checkOverallStatus()
    }
}
