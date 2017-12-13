#!/usr/bin/env groovy
package com.anchorfree;

import groovy.json.JsonSlurper
import com.anchorfree.JenkinsHttpClient
import com.anchorfree.AnsibleTowerApiProject
import com.anchorfree.AnsibleTowerApiJobTemplate

class AnsibleTowerApi {
	AnsibleTowerApi awx = null
	def subj = null // Variables' storing (json) which will be taken from awx
	String host = null
	String user = null
	String password = null
	String type = null
	def error_messages = []
	def failed = false
	Script out // for echo to jenkins log

	AnsibleTowerApi(String a, String u, String p, Script o) { host = a; user = u; password = p; out = o; awx = this }
	AnsibleTowerApi(AnsibleTowerApi a) { awx = a }

	def createProject(String name, String scm_path, String scm_point, String awx_cred_id, String awx_org_id) {
		def project = new AnsibleTowerApiProject(this, name, scm_path, scm_point, awx_cred_id, awx_org_id)
		project.make()
		return project			

	}

	def createJobTemplate(String name, String job_type, String playbook, String credential, String extra_vars,
			AnsibleTowerApiProject project, AnsibleTowerApiInventory inventory,
			String limit = '', String job_tags = '', skip_tags = '', start_at_task = '') {
		def job_template = new AnsibleTowerApiJobTemplate(this, name, job_type, playbook, credential, 
			extra_vars, project, inventory, limit, job_tags, skip_tags, start_at_task)
		job_template.make()
		return job_template
	}

	// Check that response's code is 2xx
    def checkResponse(response, String message) {
		try {
	    	if (String.valueOf(response.statusCode()).take(1) != "2") {
		    	awx.failed=true
		    	awx.error_messages.add("${message}. Response: ${response.statusCode()} ${response.statusPhrase()}; Message: ${response.bodyText()}")
		    	return false
		    }
		    return true
		}
		catch(Exception e) {
			awx.failed=true
			awx.error_messages.add("Unable to receive response: \n"+e.getMessage())
			return false
		}

    }

	def update(String path = "api/v2/${type}/${subj.id}/" ) {
	    def response = new JenkinsHttpClient().get(awx.host, path, awx.user, awx.password)
	    if (checkResponse(response, "Unable to receive status of ${path}") != true ) { return null } 
		return new groovy.json.JsonSlurper().parseText(response.bodyText())
	}

	def waitStatus(obj = subj, String path = "api/v2/${type}/${subj.id}/" ) {
		while( obj.status =~ /^(new)|(waiting)|(pending)|(running)$/) {
			sleep(5000)
			obj = update(path)
		}
		return obj
	}

	def waitSuccessStatus() {
		try {
			subj = waitStatus()
			if (subj.status != "successful") {
				awx.failed=true
				awx.error_messages.add("${name} (${type}) status is ${subj.status}")
			}			
		}
		catch(java.lang.NullPointerException e) {
			awx.failed=true
			awx.error_messages.add("Unable to receive status. Probably ${name}(${type}) didn't created: "+e.getMessage())
		}
	}

	def checkOverallStatus() {
		if (awx.failed != false) {
			def allErrors = "Error messages:\n"+awx.error_messages.join('\n')+"\n\n"
			assert awx.failed == false : "${allErrors}"
		}
	}

	def remove(obj = subj, String path = type) {
		try {
		    def response = new JenkinsHttpClient().delete(awx.host, "api/v2/${path}/${obj.id}/", awx.user, awx.password)
		    checkResponse(response, "Unable to remove ${path}/${obj.id} ")
		    return response.bodyText()			
		}
		catch(java.lang.NullPointerException e) {
			awx.failed=true
			awx.error_messages.add("Unable to remove. Probably ${name}(${type}) didn't created: "+e.getMessage())			
		}
	}
}


