# Downstream staging of HSS-U2

## hss_u2_stage

Run a downstream build of hss-u2 without awaiting.
It means that right after starting of this step your build will be proceeded and a result of staging doesn't affect status of your build.

### Usage Example

```groovy
#!groovy

pipeline {
    agent { label 'ubuntu16' }

    stages {
        stage('Shellcheck') {
            steps {
                shellcheck()
            }
        }
        stage('Build') {
            steps {
                dockerBuildTagPush()
            }
        }
        stage('HSS-U2 Stage') {
            steps {
                hss_u2_stage()
            }
        }
    }
}
```

Be note that staging procedure could take a time (~30 minutes) and give a load for Jenkins and AWX. In order to reduce unwanted load and get more effective results over time you should put this step after your unit tests.

### Notifications

The notifications for staging is separated from notification of your build since a staging procedure is quite complex and long.

#### GitHub

In GitHUb UI you will get additional github checks which will be placed next to traditional github check of your jenkins build.

#### Slack

Also all hss-u2 staging notifications is announced in Slack channel **#hss-u2-ci** with thread feature.

### Changes for staging testing

Obviously you are interesting to provide this staging for your repository if your project is involved to hss-u2 and can affect it.
In this case probably you want to pass your changes to a staging. In order to that the method take name of your repo, replaces `-` by `_` and add `_version`. This string will be used as ansible variable with value of your commit SHA.

For example for repo `some-service` and commit's SHA `abcd` the method will trigger a staging with ansible extra variable `"some_service_version": "abcd"`

It means that for successful processing of this parameter your part of the service in hss-u2 repo should be ready to be configured by such variable.
