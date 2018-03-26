#!/usr/bin/env groovy
import groovy.json.JsonSlurper

/***
* Create node in DO and itadmin
*/
def createNode(do_token, itadmin_token, name, ssh_keys, image, region, size) {
    echo("Create node")
    // Check that node isn't exist
    sh("doctl -t ${do_token} compute droplet list --format 'Name' | egrep -v -q '^${name}\$'")
    sh("ITADMIN_KEY=${itadmin_token} itadmin-cli read ${name} </dev/null 2>&1 | grep -q '^404 Not Found'")
    // Create node
    def ip = null
    def response = null
    try {
        response = sh(returnStdout: true, script: "doctl -t ${do_token} compute droplet create $name --size ${size} \
            --image ${image} --region ${region} --ssh-keys ${ssh_keys} --wait --no-header -o json").trim().replaceAll("[\n]{2,}", "\n")
    }
    catch(Exception e) {
        echo("The response for debug:\n"+response)
        echo("Exception: "+e.getMessage())
        error("Unable to create node in DO")
    }
    try {
        def nodes = new groovy.json.JsonSlurper().parseText(response)
        nodes.each { node ->
            node.networks.v4.each { network ->
                ip = network.ip_address
            }
        }
    }
    catch(Exception e) {
        echo("The response for debug:\n"+response)
        error("Unable to get IP of new node")
    }
    sh("ITADMIN_KEY=${itadmin_token} itadmin-cli create ${name} ${ip} 1 18 </dev/null 2>&1")
    return ip
}

/***
* Remove node from DO and itadmin
*/
def removeNode(do_token, itadmin_token, name) {
    echo("remove node")
    try {
        sh("doctl -t ${do_token} compute droplet delete ${name} -f || true")
    }
    catch(Exception e) {
        echo("Removing from DO have an error: "+e.getMessage())
    }
    try {
        sh("ITADMIN_KEY=${itadmin_token} itadmin-cli delete ${name} </dev/null 2>&1 || true")
    }
    catch(Exception e) {
        echo("Removing from itadmin have an error: "+e.getMessage())
    }
}


/***
* Generate node's name based on ticket number/sha
*/
def genNodeName (String prefix = "" , String postfix = "" ) {
    //Hostname limitted 30 chars (SEC-1025)
    def nodeName = ""
    if ( "${prefix}${postfix}".length() > 14 ) {
        error("Prefix+Postfix should be less 18 chars")
    }
    def tokens = "${env.JOB_NAME}".tokenize('/')
    def org = tokens[tokens.size()-3]
    def repo = tokens[tokens.size() - 2]
    def branch = tokens[tokens.size() - 1].replaceAll('%2F','-')
    def sha = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
    def jiraTicket = sh(returnStdout: true, script: "echo \"${branch}\" | grep -oP \"[A-Z]+-[0-9]+\" | head -1").trim()
    def tag
    if((jiraTicket == "")||(jiraTicket.length()>10)) {
        tag = sha.substring(0,7)
    } else {
        tag = jiraTicket.toLowerCase()
    }
    def randhash = sh(returnStdout: true, script: "echo \"\$(date +%s%N)\$(tr -cd '[:alnum:]' < /dev/urandom \
                        | fold -w30 | head -n1)\" | md5sum | cut -d ' ' -f 1").trim().replaceAll("[\n]{2,}", "\n")

    nodeName = "${prefix}-${tag}-${randhash.substring(0,3)}-${postfix}"
    return nodeName
}

/***
* Provide regression test
*/
def regression (String entrypoint, String host, String buildNumber, String password, String linkbase, String user = "jenkins" ) {
    def response = sh(returnStdout: true, script: "wget -O - --no-verbose --no-check-certificate --tries=1 '${entrypoint}/test_server?host=${host}&srv=${buildNumber}&user=${user}&test_set=all&pwd=${password}'").trim()
    def tokens = response.tokenize(',')
    def id=tokens[0]
    def status=tokens[1]
    def link="${linkbase}/index.php?view=platform&req=${id}"
    echo("Regression link: ${link}")
    return ["link":link, "status":status]
}

/***
* Wait until node's info from itadmin will be registered in consul
*/
def waitItadminConsulSync(name) {
    echo("Wait that itadmin is synced with consul")
    timeout(time: 600, unit: 'SECONDS') {
        sh("while ! curl -s 127.0.0.1:8500/v1/kv/itadmin/${name}/colo?raw | grep -q . ; do sleep 15; done")
    }
}

/***
* Short slack message about current jenkins build
*/
def slackStart(base_url, target, username = "CI", text = "\"text\": \"Build <${env.RUN_DISPLAY_URL}|${branch} [${env.BUILD_NUMBER}]> has been started.\"") {
    def tokens = "${env.JOB_NAME}".tokenize('/')
    def org = tokens[tokens.size() - 3]
    def repo = tokens[tokens.size() - 2]
    def branch = tokens[tokens.size() - 1].replaceAll('%2F','-')

    try {
        sh("curl -X POST \
            -H 'Content-type: application/json' \
            --data '{ \
                    ${text}, \
                    \"channel\": \"${target}\", \
                    \"link_names\": 1, \
                    \"username\": \"${username}\", \
                    \"icon_emoji\": \":jenkins:\" \
                }' \
            ${base_url} ")
    }
    catch(Exception e) {
        echo("Cannot send slack message:\n"+e.getMessage())
    }


}

/***
* Specific slack message about current jenkins build for staging procedure's result
*/
def slackStageFinished(base_url, target, username = "CI", color = "null", status = "", extra = "") {
    def tokens = "${env.JOB_NAME}".tokenize('/')
    def org = tokens[tokens.size() - 3]
    def repo = tokens[tokens.size() - 2]
    def branch = tokens[tokens.size() - 1].replaceAll('%2F','-')

    try {
        sh("curl -X POST \
            -H 'Content-type: application/json' \
            --data '{ \
                    \"attachments\": [ \
                        { \
                            \"fallback\": \"There are some stage results.\", \
                            \"color\": \"${color}\", \
                            \"title\": \"Build ${branch} [${env.BUILD_NUMBER}]\", \
                            \"title_link\": \"${env.RUN_DISPLAY_URL}\", \
                            \"text\": \"Stage testing finished ${status}.\", \
                            ${extra} \
                        } \
                    ], \
                    \"channel\": \"${target}\", \
                    \"link_names\": 1, \
                    \"username\": \"${username}\", \
                    \"icon_emoji\": \":jenkins:\" \
                }' \
            ${base_url} ")
    }
    catch(Exception e) {
        echo("Cannot send slack message:\n"+e.getMessage())
    }
}
