#!/usr/bin/env groovy
package com.anchorfree;

import groovy.json.JsonSlurper

class AnsibleTowerApiInventory extends AnsibleTowerApi {
	String name
	String inventory_file
	AnsibleTowerApiProject project = null
	def inventory_source = null
	def inventory_update = null

	AnsibleTowerApiInventory(AnsibleTowerApi a, AnsibleTowerApiProject pr, String n, String i) {
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
	    def response = new JenkinsHttpClient().postJson(awx.host, "api/v2/inventories/",
	    	messageBody, awx.user, awx.password)
	    if (checkResponse(response, "Can't create inventory ${name}") != true ) { return null }
		subj = new groovy.json.JsonSlurper().parseText(response.bodyText())
		inventory_source = createInventorySource(name)
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
		    def response = request.postJson(awx.host, "api/v2/inventories/${subj.id}/inventory_sources/",
		    	messageBody, awx.user, awx.password)
		    if (checkResponse(response, "Can't create inventory source ${inventory_source_name}") != true ) { return null }
			return new groovy.json.JsonSlurper().parseText(response.bodyText())
		}
		catch(java.lang.NullPointerException e) {
			awx.failed=true
			awx.error_messages.add("Can't create inventory source ${inventory_source_name}. Probably ${project.name}(${project.type}) didn't created: \n"+e.getMessage())
			return null
		}
	}

	def launch() {
		try {
		    def response = new JenkinsHttpClient().postJson(awx.host, "/api/v2/inventory_sources/${inventory_source.id}/update/", [:], awx.user, awx.password)
		    if (checkResponse(response, "Can't launch inventory update ${name}") != true ) { return null }
			inventory_update = new groovy.json.JsonSlurper().parseText(response.bodyText())			
		}
		catch(java.lang.NullPointerException e) {
			awx.failed=true
			awx.error_messages.add("Can't launch inventory update. Probably ${name}(inventory_source) didn't created: "+e.getMessage())
		}
	}

	def waitSuccessStatus() {
		try {
			inventory_update = waitStatus(inventory_update, "api/v2/inventory_updates/${inventory_update.id}/")
			if (inventory_update.status != "successful") {
				awx.failed=true
				awx.error_messages.add("${name} (inventory_updates) status is ${inventory_update.status}")
			}			
		}
		catch(java.lang.NullPointerException e) {
			awx.failed=true
			awx.error_messages.add("Can't check status. Probably ${name}(inventory_updates) didn't created: "+e.getMessage())
		}
	}

	def stdout() {
		try {
			awx.out.echo("Stdout of inventory update ${name}\n"+inventory_update.result_stdout)
		}
		catch(java.lang.NullPointerException e) {
			awx.failed=true
			awx.error_messages.add("Can't get stdout of inventory update. Probably ${name}(inventory_updates) didn't created: "+e.getMessage())
		}
	}


	/** We can leave inventory_source because it will be removed by awx as soon as related inventory will be removed
	*	private remove() {
	*		def remove_inv_src = remove("inventory_sources", inventory_source)
	*		def remove_inv = remove(type, subj)
	*		return remove_inv+remove_inv_src
	*	}
	*/
}