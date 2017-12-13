#!/usr/bin/env groovy
package com.anchorfree;

import groovy.json.JsonSlurper
import com.anchorfree.AnsibleTowerApiInventory

class AnsibleTowerApiProject extends AnsibleTowerApi {
	String name
	String scm_path
	String scm_branch // also could be a tag or sha
	String awx_cred_id
	String awx_org_id
	def project_update = null

	AnsibleTowerApiProject(AnsibleTowerApi a, String n, String p, String b, String c, String o) {
		super(a); name = n; scm_path = p; scm_branch = b; awx_cred_id = c; awx_org_id = o
		type = "projects"
	}

	// We can't call awx' REST API from constructors due https://issues.jenkins-ci.org/browse/JENKINS-26313
	// So we had to create make() method
	def make() {
	    def messageBody = ['name': name,
	        'description': "Created by jenkins",
	        'scm_type': 'git',
	        'scm_url': "git@github.com:${scm_path}.git",
	        'scm_branch': scm_branch,
	        'scm_clean': 'true',
	        'credential': awx_cred_id,
	        'organization': awx_org_id,
	        'scm_update_on_launch': 'true' ]
	    def response = new JenkinsHttpClient().postJson(awx.host, "api/v2/projects/", messageBody, awx.user, awx.password)
	    if (checkResponse(response, "Unable to create project ${name}") != true ) { return null }
		subj = new groovy.json.JsonSlurper().parseText(response.bodyText())
	}

	def createInventory(String inventory_name, String inventory_file = '') {
		def inventory = new AnsibleTowerApiInventory(awx, this, inventory_name, inventory_file)
		inventory.make()
		return inventory
	}

	def stdout() {
		try {
			def response = new JenkinsHttpClient().get(awx.host,
				"api/v2/project_updates/${subj.summary_fields.last_job.id}/", awx.user, awx.password)
		    if (checkResponse(response, "Unable to trigger project update ${name}") != true ) { return null }
			project_update = new groovy.json.JsonSlurper().parseText(response.bodyText())
			awx.out.echo("Stdout of project update ${name}\n"+project_update.result_stdout)
		}
		catch(java.lang.NullPointerException e) {
			awx.failed=true
			awx.error_messages.add("Unable to get job_events. Probably ${name}(${type}) didn't created: "+e.getMessage())
		}
	}
}
