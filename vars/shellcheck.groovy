
def call() {
    sh 'docker pull koalaman/shellcheck:latest'
    sh 'docker run -v "$PWD:/mnt" koalaman/shellcheck --color=always --check-sourced $(find . -path ./.git -prune -o -type f -regex .*\\.sh -print)'
}
