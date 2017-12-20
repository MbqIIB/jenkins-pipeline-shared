#!/usr/bin/env groovy
package com.anchorfree;

import groovy.json.JsonSlurper

class AnsibleTowerApiJobTemplate extends AnsibleTowerApi {
	String name = null
	String job_type = null
	String playbook = null
	String credential = null
	String extra_vars = null
	String limit = null
	String job_tags = null
	String skip_tags = null
	String start_at_task = null
	String become_enabled = null
	AnsibleTowerApiProject project = null
	AnsibleTowerApiInventory inventory = null

	AnsibleTowerApiJobTemplate(AnsibleTowerApi a, String n, String t, String pl, String c, String v,
			AnsibleTowerApiProject pr, AnsibleTowerApiInventory i, String become,
			String l, String tags, String skip, String start) {
		super(a); name = n; job_type = t; playbook = pl;
		credential = c; extra_vars = v; project = pr; inventory = i; become_enabled = become
		limit = l; job_tags = tags; skip_tags = skip; start_at_task = start
		type = "job_templates"
	}

	// We can't call awx' REST API from constructors due https://issues.jenkins-ci.org/browse/JENKINS-26313
	// So we had to create make() method
	def make() {
		try {
		    def messageBody = ['name': name,
				'description': "Created by jenkins",
				'job_type': job_type,
				'inventory': inventory.subj.id,
				'project': project.subj.id,
				'playbook': playbook,
				'credential': credential,
				'vault_credential': null,
				'forks': 0,
				'limit': limit,
				'verbosity': 0,
				'extra_vars': extra_vars,
				'job_tags': job_tags,
				'force_handlers': false,
				'skip_tags': skip_tags,
				'start_at_task': start_at_task,
				'timeout': 0,
				'use_fact_cache': false,
				'host_config_key': '',
				'ask_diff_mode_on_launch': false,
				'ask_variables_on_launch': false,
				'ask_limit_on_launch': false,
				'ask_tags_on_launch': false,
				'ask_skip_tags_on_launch': false,
				'ask_job_type_on_launch': false,
				'ask_verbosity_on_launch': false,
				'ask_inventory_on_launch': false,
				'ask_credential_on_launch': false,
				'survey_enabled': false,
				'become_enabled': become_enabled,
				'diff_mode': false,
				'allow_simultaneous': false ]
		    def response = new JenkinsHttpClient().postJson(awx.host, "api/v2/job_templates/", messageBody, awx.user, awx.password)
		    if (checkResponse(response, "Unable to create job template ${name}") != true ) { return null }
			subj = new groovy.json.JsonSlurper().parseText(response.bodyText())
		}
		catch(java.lang.NullPointerException e) {
			awx.failed=true
			awx.error_messages.add("Unable to create job template ${name}. Probably ${project.name}(${project.type}) or ${inventory.name}(${inventory.type}) didn't created: "+e.getMessage())
		}
	}

	//We can leave job (not template) class and get related info from job_template
	def launch() {
		try {
		    def response = new JenkinsHttpClient().postJson(awx.host, "/api/v2/job_templates/${subj.id}/launch/", [:], awx.user, awx.password)
		    if (checkResponse(response, "Unable to launch job ${name}") != true ) { return null }
			subj = update("api/v2/${type}/${subj.id}/")			
		}
		catch(java.lang.NullPointerException e) {
			awx.failed=true
			awx.error_messages.add("Unable to launch job. Probably ${name}(${type}) didn't created: "+e.getMessage())
		}
	}

	def getJobEvents(job_events, String path) {
		try {
			def response = new JenkinsHttpClient().get(awx.host, path, awx.user, awx.password)
		    if (checkResponse(response, "Unable to receive job's events ${name}") != true ) { return null }
			def page = new groovy.json.JsonSlurper().parseText(response.bodyText())
			job_events.add(page)
			if (page.next != null) {
				job_events = getJobEvents(job_events, page.next)
			}
			return job_events
		}
		catch(java.lang.NullPointerException e) {
			awx.failed=true
			awx.error_messages.add("Unable to read page->next. Probably page '${path}' doesn't exist: "+e.getMessage())
			return job_events
		}
	}

	def stdout() {
		def stdout_full = []
		def job_events = []
		try {
			job_events = getJobEvents(job_events, "api/v2/jobs/${subj.summary_fields.last_job.id}/job_events/")
			stdout_full.add("Stdout of ${name}")
			job_events.each { page ->
				page.results.each { event ->
					stdout_full.add(event.stdout)
				}
			}
			awx.out.echo(stdout_full.join('\n'))
		}
		catch(java.lang.NullPointerException e) {
			awx.failed=true
			awx.error_messages.add("Unable to receive job_events. Probably ${name}(${type}) didn't created: "+e.getMessage())
			awx.out.echo(stdout_full.join('\n'))
		}
	}
}
