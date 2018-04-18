# Docker builds

## dockerBuildTagPush

Run docker build, tag and push for containers.

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
    }
}
```

We follow the CI/CD "build once" best practice.
So, if a docker container has already been built
(Jenkins tries to pull using the SHA tag)
then it will not be built.
However, additional tags (based on both the git branch and git tags) will be applied.

### Protected Docker Tags

There are a number of tags which are "special" in git and docker.
By default, we will not build code pushed to branches with these names.
This is explicitly to encourage "build once" behavior
(build, test then deploy the same binary).
However, to support some legacy workflows, this can be worked around.
The default protected branches are in
[jenkins-pipeline-shared](https://github.com/AnchorFree/jenkins-pipeline-shared/blob/master/vars/dockerBuildTagPush.groovy#L3).

```groovy
#!groovy

pipeline {
    agent { label 'ubuntu16' }

    stages {
        stage('Build') {
            steps {
                dockerBuildTagPush("protected other_protected".split())
            }
        }
    }
}
```

The above will protect only the branches with the name `protected` and `other_protected`.
Note that the `.split()` is critical.
I have not seen a clean way of declaring a string array in groovy that acutally works for this.

### Set build-time variables

To set a [build-time variable](https://docs.docker.com/engine/reference/commandline/build/#set-build-time-variables-build-arg) pass it a second argument:

```groovy
#!groovy

pipeline {
    agent { label 'ubuntu16' }

    stages {
        stage('Build') {
            steps {
                dockerBuildTagPush("protected other_protected".split(), "GIT_COMMIT=${ env.GIT_COMMIT } VAR=example".split())
            }
        }
    }   
}
```
