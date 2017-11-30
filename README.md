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

For automatic syntax checking of ansible playbooks you can use method `ansibleCheckSyntax()`. Next arguments could be used:
- List of playbooks, which should be tested (`playbook.yml` by default);
- Inventory file which will be used for dry run (`inventory` by default);

#### Critical conditions!
- Before list you mast define type `(String[])` (see an example below) or use one string with `split()` method (see an example from [**Protected Docker Tags**](#protected-docker-tags));
- Host from inventory for dry run should be accessible by [_aftower_](https://github.com/AnchorFree/ansible-roles-common/pull/200) user with next public key:
```
from="45.55.29.214" ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAACAQDHNY3J4Xb1/Uny1gE5jPW5gT2yeYls7aFratbQTRNoWCuhQ1luLe2Mv5TCJIWjKYCli2EUOK8IDy1tTk5MiDVAD9+z5mPOaz2PLleRuNSMJBbmD+lnmeOOsv1pTXGXFooT1q6wXMHYmJCgOwSlBEI8RL072r5VyjV6gB7L1pZI2uui1fjDNwxJAsz5HTiuWEWm4w5hnRfq7dZkUu56fDJWHJVm0Y6GCX9F4zBa6s03JgcbgvIU2PF6kh6EzLYtuce8B2g2n9hUa35nxTuciEsdatR8wTIhpRBLZwWkDA2Xx4vpR9RSkhUe5XMGdNOVWvy+I9ldB49QheUSpPP+EYNOHwnNYVVNtyJ76/72TBHW9e+RMzg/t7aaOdYWETxhzIDirLJEY4h3FMVEx0EIZNq3UmXZwVt12ZncOHLEKVU33minNtkemclkvybh6+45VVr4AkdLDtyC1KrckJIgZbdS07d4va7xijig8hJkZXnI/DfJ1xGrIdu3GoAx06bG/lP+jYEGZFpRzObmAKQzSo3DTjNehqrb8DCrLjrfX1vR5AvDNrfGJ40VzTUXqIvM9O9rgiQgeEIU9h8v5sXh5YxbDiyRIpBSfcKqy5wLfFng8aoymNqCMsxEjlZUEFqxlcVHblLh2UW65w5hU++0k+lX9jwM0G6UG2/pkbJ8Mv6Duw== devops+aftower@anchorfree.com
```
- Your repo should be readable by [_aftower_](https://github.com/aftower) github user;
- If your repo is crypted, you should add `unlock.yml` playbook to your repo for unlocking.

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


Example of `Jenkins` file. In this case `playbook.yml` will be checked with `inventory` file.
```groovy
#!groovy

pipeline {
    agent { label 'ubuntu16' }

    stages {
        stage('Check') {
            steps {
                ansibleCheckSyntax()
            }
        }
    }
}
```

Example of `Jenkins` file with multiple playbooks and custom inventory.  
`inventory` is a inventory for syntax check, `playbook.yml` and `another_playbook.yml` is playbooks which will be checked.
```groovy
#!groovy

pipeline {
    agent { label 'ubuntu16' }

    stages {
        stage('Check') {
            steps {
                ansibleCheckSyntax((String[])["playbook.yml", "another_playbook.yml"], "inventory")
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
