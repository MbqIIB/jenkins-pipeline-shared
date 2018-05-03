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
def genNodeName (String prefix = "" , String postfix = "", String job_name = env.JOB_NAME ) {
    //Hostname limitted 30 chars (SEC-1025)
    def nodeName = ""
    if ( "${prefix}${postfix}".length() > 15 ) {
        error("Prefix+Postfix should be less 16 chars")
    }
    def tokens = job_name.tokenize('/')
    def org = tokens[tokens.size()-3]
    def repo = tokens[tokens.size() - 2]
    def branch = tokens[tokens.size() - 1].replaceAll('%2F','-')
    def sha = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
    def jiraTicket = sh(returnStdout: true, script: "echo \"${branch}\" | grep -oP \"[A-Z]+-[0-9]+\" | head -1").trim()
    def tag
    if((jiraTicket == "")||(jiraTicket.length()>10)) {
        tag = sha.substring(0,7)
    } else {
        tag = jiraTicket.toLowerCase().replaceAll('-','')
    }
    def randhash = sh(returnStdout: true, script: "echo \"\$(date +%s%N)\$(tr -cd '[:alnum:]' < /dev/urandom \
                        | fold -w30 | head -n1)\" | md5sum | cut -d ' ' -f 1").trim().replaceAll("[\n]{2,}", "\n")

    nodeName = "${prefix}-${tag}-${randhash.substring(0,3)}${postfix}"
    return nodeName
}

/***
* Provide regression test
*/
def regression (String entrypoint, String host, String buildNumber, String password, String linkbase, String user = "jenkins" ) {
    def response = sh(returnStdout: true, script: "wget -O - --no-verbose --no-check-certificate --tries=1 '${entrypoint}/test_server?host=${host}&srv=${buildNumber}&user=${user}&test_set=full&pwd=${password}'").trim()
    def tokens = response.tokenize(',')
    def id
    def status
    def link
    try {
	    id=tokens[0]
	    status=tokens[1]
	    link="${linkbase}/index.php?view=platform&req=${id}"
    	echo("Regression rusult: ${status}\n${link}")
    }
    catch(Exception e) {
    	echo("Regression response:\n"+response)
    	echo("Regression response is incorrect:\n"+e.getMessage())
    	error("Regression response is incorrect")
    }
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

def slackAPI(secret, target, username = "CI", job_name = env.JOB_NAME, external_text = "", external_ts = "", color = "", external_title = "", extra = "") {
    echo("Start slack messaging")
    def tokens = "${job_name}".tokenize('/')
    def org = tokens[tokens.size() - 3]
    def repo = tokens[tokens.size() - 2]
    def branch = tokens[tokens.size() - 1].replaceAll('%2F','-')
    def text = external_text ?: "Staging for _${branch}_ [${env.BUILD_NUMBER}] has been started (<${env.RUN_DISPLAY_URL}|parent build>)"
    def thread_ts = external_ts ? "\"thread_ts\":\"${external_ts}\"," : ""
    if (external_title) {
        pretext = "\"pretext\": \"${external_title}\","
    } else {
        pretext = ""
    }
    def body
    if(color) {
        body="\"attachments\": [ \
                        { \
                            \"fallback\": \"There are some stage results.\", \
                            \"color\": \"${color}\", \
                            ${pretext} \
                            \"title\": \"${text}\", \
                            ${extra} \
                            \"mrkdwn_in\": [\"text\", \"pretext\", \"fields\"] \
                        } \
                    ],"
    } else {
        body="\"text\": \"${text}\","
    }
    def response
    try {
        response=sh(returnStdout: true, script: "curl -X POST \
            -H 'Content-type: application/json' \
            -H 'Authorization: Bearer ${secret}' \
            --data '{ \
                    ${body} \
                    \"channel\": \"${target}\", \
                    \"link_names\": 1, \
                    \"username\": \"${username}\", \
                    ${thread_ts} \
                    \"icon_emoji\": \":jenkins:\" \
                }' \
            https://slack.com/api/chat.postMessage")
    }
    catch(Exception e) {
        echo("Cannot send slack message:\n"+e.getMessage())
    }
    def result = new groovy.json.JsonSlurper().parseText(response)
    return result.ts
}


def notify(args) {
    username    = args.username ?: "CI"
    ts          = args.ts ?: ""
    targetUrl   = args.targetUrl ?: null
    def text = ""
    def title   = ""
    def color
    def marker
    def status_source = args.overal ? currentBuild.result : args.status
    def slack_extra = args.slack_extra ? "\"fields\": [ ${args.slack_extra} ]," : null
    switch(status_source) {
        case ["SUCCESS", null]:
            color = "good"
            marker = ":heavy_check_mark: "
            break
        case "FAILURE":
            color = "danger"
            marker = ":x: "
            break
        case "ERROR":
            color = "warning"
            marker = ":warning: "
            break
        case "ABORTED":
            color = "null"
            break
        default:
            color = ""
            marker = ""
            break
    }
    if(args.overal) {
        title = args.context + " (<${env.RUN_DISPLAY_URL}|build>)"
        text = args.description
        if (currentBuild.result) { text += " (${currentBuild.result})" }
    } else {
        color = ""
        text = "Step *${args.context}* (<${env.RUN_DISPLAY_URL}|build>)\n${marker} ${args.description}"
        if (targetUrl) { text += " (<${targetUrl}|link>)" }
    }
    if ((args.status != "PENDING") && (args.slack_switch == 'true')) {
        slackAPI(args.slack_secret, args.slack_target, username, env.JOB_NAME, text, ts, color, title, slack_extra)
    }
    if (args.status) {
        githubNotify(status:args.status,description:args.description,context:args.context,repo:args.repo,sha:args.sha,targetUrl:targetUrl)
    }
    if ((args.status == "PENDING") && (args.failed != null)) {
        return args.failed
    }
    // Jenkins do not permit use 'remove' method for maps
    return ""
}

/***
* Run a speed test, wait 10m to be sure that it has been done
*/
def speedTest(entrypoint, host, job = "speed_test_individual" ) {
    echo("Start a speed test")
    withCredentials([string(credentialsId: 'speed_test_token', variable: 'token')]) {
        sh("wget -O - 'https://${entrypoint}/buildByToken/buildWithParameters?job=${job}&token=${token}&host=${host}'")
    }
    echo("Wait 10m to be sure that the speed test has been done")
    sleep(time: 600, unit: 'SECONDS')
}
