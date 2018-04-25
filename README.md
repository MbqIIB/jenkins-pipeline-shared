# jenkins-pipeline-shared

Shared Pipeline Library for Jenkins builds at AF.

After github project gets file "Jenkinsfile" in the root of project then
CI begins to work. Jenkins will do new build on every push and do syntax
check. Results of these can be viewed on a prject github page. Against every
commit You can see red cross, green mark, orange circle or what ever. This
is a brief build's result. You can pressit and view links to CI. In CI page
You can brows log messages. For example ansible-playbook's log can be found
there. Whatch CI pages and update Your PRs to correct errors.

## Docker builds

[Steps for builds of docker containers](DOCKER.md)

## Ansible

[Steps for provisioning using ansible (AWX)](ANSIBLE.md)

## HSS-U2 Staging

[Step for downstream of hss-u2 staging](HSS_U2_STAGE.md)

## CI staging related steps

Little helpers to construct your own complex CI staging such as
- GitHub and/or Slack notifications
- Dynamic creation and destruction of DO nodes.
- Generate node name based on JIRA ticket
- Provide a regression test

[CI tools](CI_TOOLS.md)

## shellcheck

An extremely simple check which runs the [shellcheck](https://www.shellcheck.net/) static analysis on all `.sh` scripts in the repo.
Depends on scripts having a `.sh` suffix.

## Testing and Development

Use a branch and test with something like the following.

```groovy
#!groovy

@Library('af@INFRA-3777-DRY-shared') _

pipeline {
    agent { label 'ubuntu16' }

    stages {
        stage('Build') {
            steps {
                dockerBuildTagPush()
            }
        }
    }
}
```
