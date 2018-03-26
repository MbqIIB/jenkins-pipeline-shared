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
	def wait_between_api_attempts = 5000
	def number_of_attempts = 3
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

	// To be tolerant for connection interruptions and AWX's internal issues
	// return response if it's good
	def tolerantHttpClient(String method, String path, String error, messageBody = [:]) {
		def check
		def response
		for(def retry = 0; retry < number_of_attempts; retry++) {
			switch (method) {
				case "get": response = new JenkinsHttpClient().get(awx.host, path, awx.user, awx.password); break;
				case "post": response = new JenkinsHttpClient().postJson(awx.host, path, messageBody, awx.user, awx.password); break;
				case "delete": response = new JenkinsHttpClient().delete(awx.host, path, awx.user, awx.password); break;
				default: awx.addError("tolerantHttpClient: incorrect method \"${method}\"");
			}
		    check = checkResponse(response)
		    if (check == true ) { return response }
		    // Remove a running job if it is blocking the query
			if (String.valueOf(response.statusCode()) == "409") {
				def conflict = new groovy.json.JsonSlurper().parseText(response.bodyText())
				if(conflict.conflict == "Resource is being used by running jobs.") {
					conflict.active_jobs.each { job ->
						def cancelation_response = tolerantHttpClient("post",
							"/api/v2/jobs/${job.id}/cancel/",
							"Unable to cancel job ${job.id}", [:])
						if (cancelation_response != null) { awx.out.echo("Job ${job.id} has been canceled")	}
					}
					sleep(wait_between_api_attempts*4)
				}
		    }
		    sleep(wait_between_api_attempts)
		}
		// if retry != 0 and check == "record already exist"; do "I'm ok with it"
		awx.addError("${error}:\n${check}", error)
	    return null
	}

	def tolerantMake(messageBody, String obj_type = type, String obj_name = name, String error = "Unable to create ${name}(${type})") {
	    try {
			def response = tolerantHttpClient("post", "api/v2/${obj_type}/", error, messageBody)
			if (response != null) {
				return new groovy.json.JsonSlurper().parseText(response.bodyText())
			}
		    // Do not give up and try to get object
		    def subj_id = getIDbyName(obj_type, obj_name)
		    return update("api/v2/${obj_type}/${obj_id}/")
	    }
	    catch(Exception e) {
			awx.addError("${error}:\n"+e.getMessage(), error)
			return null
	    }
	}

	// Check that response's code is 2xx
    def checkResponse(response) {
		try {
			if (String.valueOf(response.statusCode()).take(1) != "2") {
				return "Response: ${response.statusCode()} ${response.statusPhrase()}; Message: ${response.bodyText()}"
		    }
		    return true
		}
		catch(Exception e) {
			return "Unable to receive response: \n"+e.getMessage()
		}

    }

	def update(String path = "api/v2/${type}/${subj.id}/" ) {
		try {
			def response = tolerantHttpClient("get", path, "Unable to receive status of ${path}")
			if (response == null) { return null } 
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
			def response = tolerantHttpClient("delete", "api/v2/${path}/${obj.id}/", "Unable to remove ${path}/${obj.id}")
			if (response == null) { return null }
			return response.bodyText()
		}
		catch(java.lang.NullPointerException e) {
			awx.addError("Unable to remove. Probably ${name}(${type}) didn't created: "+e.getMessage(), "Unable to remove")
		}
	}

	def getIDbyName(String type, String name) {
		def id = null
		try {
			def response = tolerantHttpClient("get", "api/v2/${type}/", "Unable to get ID of ${name} (${type})")
		    if (response != null) {
				def subj = new groovy.json.JsonSlurper().parseText(response.bodyText())
				subj.results.each { i ->
					if ( i.name == name ) { id = i.id }
				}
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
