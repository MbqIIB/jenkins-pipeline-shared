# CI tools

Little helpers to construct your own complex CI staging

## toolsCI.genNodeName

Generate a name for a node based on JIRA ticket with defined prefix (aka cluster) and suffix.

### Usage Example

```groovy
#!groovy

pipeline {
    agent { label 'ubuntu16' }

    stages {
        stage('Generate a name') {
            steps {
                script{
                    NAME = toolsCI.genNodeName(CLUSTER, SUFFIX)
                }
            }
        }
        ...
    }
}
```

For example commit in branch with name `TICKET-123-some-fixes` and method `toolsCI.genNodeName("abc-sfo-stage", "1")` will generate name like that `abc-sfo-stage-ticket123-fgh1`. Pay attention that three letters in latest segment (here it's `fgh`) is random hash.

## toolsCI.createNode

Create a node in DigitalOcaen, register it in ITAdmin and return its IP.

### Usage Example

```groovy
#!groovy

pipeline {
    agent { label 'ubuntu16' }

    stages {
        ...
        stage('Create node') {
            steps {
                script{
                    IP = toolsCI.createNode(DO_TOKEN, ITADMIN_TOKEN, NAME, DO_SSH_KEYS, DO_IMAGE, DO_REGION, DO_SIZE)
                }
            }
        }
        ...
    }
}
```

## toolsCI.removeNode

Destroy a node from DigitalOcaen and remove it from ITAdmin.

### Usage Example

```groovy
#!groovy

pipeline {
    agent { label 'ubuntu16' }

    stages {
        ...
        stage('Actions') {
            steps {
                ...
                }
            }
        }
        ...
    }
    post {
        always {
            script{
                toolsCI.removeNode(DO_TOKEN, ITADMIN_TOKEN, NAME)
                }
            }
        }
    }
}
```

You must use this step in post section to be sure that a node will be destroyed at the end of a build in any cases.

## toolsCI.regression

Run a regression test for a node, print short result and link to detailed report, return short result and link.

### Arguments

- URL of QA entrypoint for query to start a regression
- Name of the node
- Version of tested product
- Password to access QA entrypoint
- URL which should be base for link to regression's result

### Usage Example

```groovy
#!groovy

pipeline {
    agent { label 'ubuntu16' }

    stages {
        ...
        stage('Regression') {
            steps {
                script{
                    regression = toolsCI.regression(QA_ENTRYPOINT,NAME,CURRENT_VERSION,QA_PASSWORD,QA_LINKBASE)
                }
            }
        }
        ...
    }
}
```

Be note that the method will return a map `["link":link, "status":status]` so in further steps you can use variables `regression.link` and `regression.status`

## toolsCI.waitItadminConsulSync

Check by node name that it was successfully registered in consul after itadmin2consul sync.

### Usage Example

```groovy
#!groovy

pipeline {
    agent { label 'ubuntu16' }

    stages {
        ...
        stage('itadmin2consul') {
            steps {
                toolsCI.waitItadminConsulSync(NAME)
            }
        }
        ...
    }
}
```

## toolsCI.notify

Make an additional separate notification in Slack and GitHub

### Arguments

The method has a named arguments:
- slack_switch - true if we want to send Slack notification as well
- status - PENDING, SUCCESS, FAILURE, ERROR or ABORTED. Manage status of github check or color for slack message. Also slack will not be used for PENDING status.
- description - An extended notification messsage
- context - A short notification name
- repo - A repo name where we should post a github check
- sha - A commit where we should post a github check
- username - Slack bot name
- ts - Slack `ts` value of some message if you want to post a notification as reply in the thread
- slack_secret - secret key of a bot for slack API
- slack_target - Slack channel (or person for PM) where notification will be announced

### Usage Example

Here is example of handling of additional notifications for some step.

```groovy
#!groovy

pipeline {
    agent { label 'ubuntu16' }

    stages {
        ...
        stage('Actions') {
            steps {
                toolsCI.notify(slack_switch:"true",status:"PENDING",description:"The action is in a progress",context:"A separate action",repo:REPO,sha:SHA,username:SLACK_BOT_NAME,ts:SLACK_TS,slack_secret:SLACK_SECRET,slack_target:SLACK_TARGET)
                ... some steps ...
                toolsCI.notify(slack_switch:"true",status:"SUCCESS",description:"The actions finished successfully",context:"A separate action",repo:REPO,sha:SHA,username:SLACK_BOT_NAME,ts:SLACK_TS,slack_secret:SLACK_SECRET,slack_target:SLACK_TARGET)
            }
        }
        ...
    }
    post {
        failure {
            script{
                toolsCI.notify(slack_switch:"true",status:"FAILURE",description:"The actions failed",context:"A separate action",repo:REPO,sha:SHA,username:SLACK_BOT_NAME,ts:SLACK_TS,slack_secret:SLACK_SECRET,slack_target:SLACK_TARGET)
            }
        }
    }
}
```