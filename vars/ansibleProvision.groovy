#!/usr/bin/env groovy
package com.anchorfree;
import com.anchorfree.AnsibleTowerApi

// From: https://github.com/jenkinsci/pipeline-examples/blob/master/pipeline-examples/github-org-plugin/access-repo-information.groovy
// github-organization-plugin jobs are named as 'org/repo/branch'
// we don't want to assume that the github-organization job is at the top-level
// instead we get the total number of tokens (size)
// and work back from the branch level Pipeline job where this would actually be run
// Note: that branch job is at -1 because Java uses zero-based indexing

def call(String[] playbooks, Map<String, String> hosts,
            extra_vars = '', limit = '', become = 'true', job_tags = '', skip_tags = '', external_sha = '', awx_host_credentials_id = 'awx_host') {
    def tokens = "${env.JOB_NAME}".tokenize('/')
    def org = tokens[tokens.size()-3]
    def repo = tokens[tokens.size() - 2]
    def branch = tokens[tokens.size() - 1].replaceAll('%2F','-')
    def original_sha = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
    
    def scm_path = "${org}/${repo}"
    // if name starts with ansible-, remove the prefix.
    if (repo.startsWith('ansible-')) {
        repo = repo.substring(8)
    }
    
    def unlock_playbook = "unlock.yml"
    def unlock_required = fileExists(unlock_playbook)

    def randhash = sh(returnStdout: true, script: "echo \"\$(date +%s%N)\$(tr -cd '[:alnum:]' < /dev/urandom \
        | fold -w30 | head -n1)\" | md5sum | cut -d ' ' -f 1").trim().replaceAll("[\n]{2,}", "\n")
    def sha // commit/tag which will be used for provision
    def name // general name of awx's objects
    if (external_sha == '') {
        sha = original_sha
        name = "${repo}(${sha.substring(0,7)})-[${randhash.substring(0,8)}]"
    }
    else {
        sha = external_sha
        if (external_sha.length() == 40) { // if it real sha make it short
            external_sha = "${external_sha.substring(0,7)}"
        }
        // if we got external sha/tag we will mark it name to prevent of conflicts in awx
        name = "${repo}(${original_sha.substring(0,7)})/(${external_sha})-[${randhash.substring(0,8)}]"
    }
 

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
    withCredentials([string(credentialsId: 'awx_git_cred', variable: 'awx_git_cred_name'),
                    string(credentialsId: 'awx_ssh_cred', variable: 'awx_ssh_cred_name'),
                    string(credentialsId: 'awx_org', variable: 'awx_org_name')]) {
        awx_git_cred = awx_git_cred_name
        awx_ssh_cred = awx_ssh_cred_name
        awx_org = awx_org_name
    }

    withCredentials([usernamePassword(credentialsId: 'awx', usernameVariable: 'user', passwordVariable: 'password'),
            string(credentialsId: 'awx_git_crypt_key_path', variable: 'awx_git_crypt_key_path'),
            string(credentialsId: awx_host_credentials_id, variable: 'awx_host')]) {

        def unlock_vars = "git_crypt_key_path: ${awx_git_crypt_key_path}"

        echo("Creation of awx project")
        def awx = new AnsibleTowerApi(awx_host, user, password, this)
        def project = awx.createProject(name, scm_path, sha, awx_git_cred, awx_org)
        project.waitSuccessStatus()
        project.stdout()

        echo("Creation of awx inventory")
        def inventory = project.createInventory(name)
        hosts.each{ host, variables -> inventory.addHost(host, variables) }

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
            echo("Provision of ${playbook}")
            def dry_run = awx.createJobTemplate("${name} - ${playbook}", "run",
                playbook, awx_ssh_cred, extra_vars, project, inventory, become, limit, job_tags, skip_tags)
            dry_run.launch()
            dry_run.waitSuccessStatus()
            dry_run.stdout()
            dry_run.remove()
        }

        inventory.remove()
        project.remove()

        echo("Verify overall result of provisioning")
        awx.checkOverallStatus()
    }
}

def call(args) {

    String[] playbooks = ["playbook.yml"]
    Map<String, String> hosts
    def extra_vars = ''
    def limit = ''
    def become = 'true'
    def job_tags = ''
    def skip_tags = ''
    def external_sha = ''
    def awx_host_credentials_id = 'awx_host'

    if (args.playbooks != null) { playbooks = args.playbooks }
    if (args.hosts != null) { hosts = args.hosts }
    if (args.extra_vars != null) { extra_vars = args.extra_vars }
    if (args.limit != null) { limit = args.limit }
    if (args.become != null) { become = args.become }
    if (args.job_tags != null) { job_tags = args.job_tags }
    if (args.skip_tags != null) { skip_tags = args.skip_tags }
    if (args.external_sha != null) { external_sha = args.external_sha }
    if (args.awx_host_credentials_id != null) { awx_host_credentials_id = args.awx_host_credentials_id }
    call(playbooks, hosts, extra_vars, limit, become, job_tags, skip_tags, external_sha, awx_host_credentials_id)
}
