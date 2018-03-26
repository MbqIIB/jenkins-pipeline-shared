#!/usr/bin/env groovy
package com.anchorfree;

import groovy.json.JsonSlurper

class AnsibleTowerApiInventory extends AnsibleTowerApi {
	String name
	String inventory_file
	AnsibleTowerApiProject project = null
	def inventory_source = null
	def inventory_update = null

	AnsibleTowerApiInventory(AnsibleTowerApi a, AnsibleTowerApiProject pr, String n, String i = '') {
		super(a); project = pr; name = n; inventory_file = i
		type = "inventories"
	}

	// We can't call awx' REST API from constructors due https://issues.jenkins-ci.org/browse/JENKINS-26313
	// So we had to create make() method
	def make() {
	    def messageBody = ['name': name,
	        'description': "Created by jenkins",
	        'organization': project.awx_org_id,
	        'kind':'' ]
		subj = tolerantMake(messageBody)
		if (inventory_file != '') { inventory_source = createInventorySource(name) }
	}

	def addHost(String host_name, String host_variables) {
	    def messageBody = ['name': host_name,
	        'description': "added by jenkins",
	        'enabled': true,
	        'instance_id': '',
	        'variables': host_variables ]
		def response = tolerantHttpClient("post",
			"api/v2/inventories/${subj.id}/hosts/",
			"Unable to add host ${host_name} to inventory ${name}", messageBody)
	}

	def createInventorySource(String inventory_source_name) {
	    def request = new JenkinsHttpClient()
		try {
		    def messageBody = ['name': inventory_source_name,
		        'description': "Created by jenkins",
				'source': 'scm',
				'source_path': inventory_file,
				'source_script': null,
				'source_vars': '',
				'credential': null,
				'source_regions': '',
				'instance_filters': '',
				'group_by': '',
				'overwrite': false,
				'overwrite_vars': true,
				'timeout': 0,
				'verbosity': 1,
				'update_on_launch': true,
				'update_cache_timeout': 0,
				'source_project': project.subj.id,
				'update_on_project_update': false ]
		    return tolerantMake(messageBody, "inventories/${subj.id}/inventory_sources", inventory_source_name)
		}
		catch(java.lang.NullPointerException e) {
			awx.addError("Unable to create inventory source ${inventory_source_name}. Probably ${project.name}(${project.type}) didn't created: \n"+e.getMessage(), "Unable to create inventory source")
			return null
		}
	}

	def launch() {
		try {
			def response = tolerantHttpClient("post",
				"/api/v2/inventory_sources/${inventory_source.id}/update/",
				"Unable to trigger inventory update ${name}", [:])
			if (response == null) { return null }
			inventory_update = new groovy.json.JsonSlurper().parseText(response.bodyText())			
		}
		catch(java.lang.NullPointerException e) {
			awx.addError("Unable to trigger inventory update. Probably ${name}(inventory_source) didn't created: "+e.getMessage(), "Unable to trigger inventory update")
		}
	}

	def waitSuccessStatus() {
		try {
			inventory_update = waitStatus(inventory_update, "api/v2/inventory_updates/${inventory_update.id}/")
			if (inventory_update.status != "successful") {
				awx.addError("${name} (inventory_updates) status is ${inventory_update.status}", "inventory_updates bad status")
			}			
		}
		catch(java.lang.NullPointerException e) {
			awx.addError("Unable to receive status. Probably ${name}(inventory_updates) didn't created: "+e.getMessage(), "Unable to receive status")
		}
	}

	def stdout() {
		try {
			def response = tolerantHttpClient("get",
				"api/v2/inventory_updates/${inventory_update.id}/stdout/?format=ansi",
				"Unable to get inventory's stdout ${name}")
		    if (response == null) { return null } 
			awx.out.echo("Stdout of inventory update ${name}\n"+response)
		}
		catch(java.lang.NullPointerException e) {
			awx.addError("Unable to obtain stdout of ${name} inventory update: "+e.getMessage(), "Unable to get obtain for inventory update")
		}
	}
}
