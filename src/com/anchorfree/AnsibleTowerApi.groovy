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
			AnsibleTowerApiProject project, AnsibleTowerApiInventory inventory, String become = 'false',
			String limit = '', String job_tags = '', String skip_tags = '', String start_at_task = '') {
		def job_template = new AnsibleTowerApiJobTemplate(this, name, job_type, playbook, credential, 
			extra_vars, project, inventory, become, limit, job_tags, skip_tags, start_at_task)
		job_template.make()
		return job_template
	}

	/*** Process an error
	* message - wide error message which will be added to stack
	* error - will show red mark with this short text if it's defined
	* failed - overal project state (default true)
	*/
	def addError(String message = "", error = null, failed = true) {
		awx.failed=failed
		awx.error_messages.add(message)
		// create red marker with short message if it's present
		if (error != null) {try { awx.out.error(error) } catch (err) {}}
	}

	// Check that response's code is 2xx
    def checkResponse(response, String message, error = null) {
		try {
			if (String.valueOf(response.statusCode()).take(1) != "2") {
				awx.addError("${message}. Response: ${response.statusCode()} ${response.statusPhrase()}; Message: ${response.bodyText()}", error)
				return false
		    }
		    return true
		}
		catch(Exception e) {
			awx.addError("Unable to receive response: \n"+e.getMessage(), error)
			return false
		}

    }

	def update(String path = "api/v2/${type}/${subj.id}/" ) {
		try {
			def response = new JenkinsHttpClient().get(awx.host, path, awx.user, awx.password)
			if (checkResponse(response, "Unable to receive status of ${path}") != true ) { return null } 
			return new groovy.json.JsonSlurper().parseText(response.bodyText())
		}
		catch(java.lang.NullPointerException e) {
			awx.addError("Unable to update. Probably ${name}(${type}) didn't created: "+e.getMessage(), "Unable to update")
		}
	}

	def waitStatus(obj = subj, String path = "api/v2/${type}/${subj.id}/",  timeout = 600 ) {
		if(type == "job_templates") {
			timeout = 7200
		}
		Date finish = new Date()
		Date current = new Date()
		finish.setTime(finish.getTime()+(timeout*1000))
		while( current.before(finish) && (obj.status =~ /^(new)|(waiting)|(pending)|(running)$/) ) {
			sleep(5000)
			current = new Date()
			obj = update(path)
		}
		if ( current.before(finish) == false ) {
			awx.addError("${path} exited by timeout (${timeout}s)", "Exited by timeout")
		}
		return obj
	}

	def waitSuccessStatus() {
		try {
			subj = waitStatus()
			if (subj.status != "successful") {
				awx.addError("${name} (${type}) status is ${subj.status}", "Status is ${subj.status}")
			}			
		}
		catch(java.lang.NullPointerException e) {
			awx.addError("Unable to receive status for ${name}(${type}): "+e.getMessage(), "Unable to receive status")
		}
	}

	def checkOverallStatus() {
		if (awx.failed != false) {
			awx.out.echo("Error messages:\n"+awx.error_messages.join('\n')+"\n\n")
			// Error signal step body could be not expanded
			// https://issues.jenkins-ci.org/browse/JENKINS-46112
			awx.out.error("Please take a look step above")
		}
	}

	def remove(obj = subj, String path = type) {
		try {
		    def response = new JenkinsHttpClient().delete(awx.host, "api/v2/${path}/${obj.id}/", awx.user, awx.password)
		    checkResponse(response, "Unable to remove ${path}/${obj.id} ")
		    return response.bodyText()			
		}
		catch(java.lang.NullPointerException e) {
			awx.addError("Unable to remove. Probably ${name}(${type}) didn't created: "+e.getMessage(), "Unable to remove")
		}
	}

	def getIDbyName(String type, String name) {
		def id = null
		try {
			def response = new JenkinsHttpClient().get(awx.host, "api/v2/${type}/", awx.user, awx.password)
		    if (checkResponse(response, "Unable to get ${type}", "Unable to get ${type}") != true ) { return null }
			def subj = new groovy.json.JsonSlurper().parseText(response.bodyText())
			subj.results.each { i ->
				if ( i.name == name ) { id = i.id }
			}
		}
		catch(java.lang.NullPointerException e) {
			awx.addError("Unable to get ID of '${name}' ('${type}'): "+e.getMessage(), "Unable to get ID")
			return id
		}
		if ( id == null) {
			awx.addError("Unable to get ID of '${name}' ('${type}'): such object was not found", "Unable to get ID")
		}
		return id
	}

}
