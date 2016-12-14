#!/usr/bin/groovy

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def artifactId
    def releaseVersion
    def docgenScript

    kubernetes.pod('buildpod').withImage('fabric8/maven-builder:latest')
            .withPrivileged(true)
            .withHostPathMount('/var/run/docker.sock', '/var/run/docker.sock')
            .withEnvVar('DOCKER_CONFIG', '/root/.docker/')
            .withSecret('jenkins-maven-settings', '/root/.m2')
            .withSecret('jenkins-ssh-config', '/root/.ssh')
            .withSecret('jenkins-git-ssh', '/root/.ssh-git')
            .withSecret('jenkins-release-gpg', '/root/.gnupg')
            .withSecret('jenkins-docker-cfg', '/root/.docker')
            .inside {

        sh 'chmod 600 /root/.ssh-git/ssh-key'
        sh 'chmod 600 /root/.ssh-git/ssh-key.pub'
        sh 'chmod 700 /root/.ssh-git'
        sh 'chmod 600 /root/.gnupg/pubring.gpg'
        sh 'chmod 600 /root/.gnupg/secring.gpg'
        sh 'chmod 600 /root/.gnupg/trustdb.gpg'
        sh 'chmod 700 /root/.gnupg'

        def gitRepoUrl = "https://github.com/${config.project}.git"

        checkout scm: [$class          : 'GitSCM',
                       useRemoteConfigs: [[url: gitRepoUrl]],
                       branches        : [[name: 'refs/tags/v' + releaseVersion]]],
                changelog: false, poll: false

        if (docgenScript == null) {
            //FIXME - have the profiles passed as parameter
            sh 'mvn -Pdoc-html'
            sh 'mvn -Pdoc-pdf'
            //FIXME - check if gh-pages already exist if not create it
            sh 'git clone -b gh-pages' + gitRepoUrl + ' gh-pages'
            sh 'cp -rv target/generated-docs/* gh-pages/'
            sh 'cd gh-pages'
            sh 'mv index.pdf ' + artifactId + '.pdf'
            sh 'git add --ignore-errors *'
            sh 'git commit -m "generated documentation'
            sh 'git push origin gh-pages'
        } else {
            sh "${docgenScript}"
        }

    }

}
