# jenkins-pipeline-shared

Shared Pipeline Library for Jenkins builds at AF.

### Usage Example

```groovy
#!groovy

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

### Ansible "Dry Run"

For automatic syntax checking of ansible playbooks you can use method `ansibleCheckSyntax` with next arguments:
- List of playbooks, which should be tested;
- Inventory file which will be used for dry run;

There are several important things:
- Before list you mast define type `(String[])` (see an example below) or use one string with `split()` method (see an example from [**Protected Docker Tags**](#protected-docker-tags));
- Host from inventory for dry run should be accessible;
- Your repo should be readable by [aftower](https://github.com/aftower) user;
- If your repo is crypted, you should add `unlock.yml` playbook for unlocking

Example of `unlock.yml`:
```yml
- hosts: all
  gather_facts: False
  pre_tasks:
    - name: Unlock repository
      local_action: shell git-crypt unlock {{ git_crypt_key_path }}
    - name: Unlock repository common
      local_action: shell cd roles/common/ && git-crypt unlock {{ git_crypt_key_path }}
```

Example of `Jenkins` file. `inventory-dry` is a inventory for syntax check, `some_playbook.yml` and `another_playbook.yml` is playbooks which will be checked.
```groovy
#!groovy

pipeline {
    agent { label 'ubuntu16' }

    stages {
        stage('Check') {
            steps {
                ansibleCheckSyntax((String[])["some_playbook.yml", "another_playbook.yml"], "inventory-dry")
            }
        }
    }
}
```

### Testing and Development

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
