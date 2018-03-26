#!/usr/bin/env groovy
package com.anchorfree;

import groovy.json.JsonSlurper
import com.anchorfree.AnsibleTowerApiInventory

class AnsibleTowerApiProject extends AnsibleTowerApi {
	String name
	String scm_path
	String scm_branch // also could be a tag or sha
	String awx_cred_name
	String awx_org_name
	String awx_cred_id
	String awx_org_id
	def project_update = null

	AnsibleTowerApiProject(AnsibleTowerApi a, String n, String p, String b, String c, String o) {
		super(a); name = n; scm_path = p; scm_branch = b; awx_cred_name = c; awx_org_name = o
		type = "projects"
	}

	// We can't call awx' REST API from constructors due https://issues.jenkins-ci.org/browse/JENKINS-26313
	// So we had to create make() method
	def make() {
		awx_cred_id = getIDbyName("credentials", awx_cred_name)
		awx_org_id = getIDbyName("organizations", awx_org_name)
	    def messageBody = ['name': name,
	        'description': "Created by jenkins",
	        'scm_type': 'git',
	        'scm_url': "git@github.com:${scm_path}.git",
	        'scm_branch': scm_branch,
	        'scm_clean': 'true',
	        'credential': awx_cred_id,
	        'organization': awx_org_id,
	        'scm_update_on_launch': 'false' ]
	    subj = tolerantMake(messageBody)
	}

	def createInventory(String inventory_name, String inventory_file = '') {
		def inventory = new AnsibleTowerApiInventory(awx, this, inventory_name, inventory_file)
		inventory.make()
		return inventory
	}

	def stdout() {
		try {
			def response
			try {
				response = tolerantHttpClient("get",
				"api/v2/project_updates/${subj.summary_fields.last_job.id}/stdout/?format=ansi",
				"Unable to get project's stdout ${name}")
			}
			catch(java.lang.NullPointerException e) {
				response = tolerantHttpClient("get",
					"api/v2/project_updates/${subj.summary_fields.current_job.id}/stdout/?format=ansi",
					"Unable to get project's stdout ${name}")
			}
			awx.out.echo("Stdout of project update ${name}\n"+response)
		}
		catch(java.lang.NullPointerException e) {
			awx.addError("Unable to get ${name} project stdout: "+e.getMessage(), "Unable to get project stdout")
		}
	}
}
