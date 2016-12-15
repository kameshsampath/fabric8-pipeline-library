#!/usr/bin/groovy

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def project = config.project[0]
    def artifactId = config.artifactId
    def docVersion = config.project[1]
    def docgenScript = config.docgenScript ?: null
    def gitUser = config.gitUser
    def gitEmail = config.gitEmail

    //Array of Maven Profiles
    def profiles

    def gitRepoUrl = "https://github.com/${project}.git"

    container(name: 'maven') {

        checkout scm: [$class          : 'GitSCM',
                       useRemoteConfigs: [[url: gitRepoUrl]],
                       branches        : [[name: "refs/tags/v${docVersion}"]]],
                changelog: false, poll: false

        if (docgenScript == null) {
            //if no profiles are passed we will try running doc-html, doc-pdf
            if (profiles == null) {
                sh('mvn -Pdoc-html && mvn -Pdoc-pdf')
            } else {
                def mvnCmd = 'mvn -P' + profiles.join(" && mvn -P")
                sh("mvn ${mvnCmd}")
            }

            def refGHPages = sh(script: 'git rev-parse --abbrev-ref --glob=\'refs/remotes/origin/gh-pages*\'',
                    returnStdout: true).toString().trim()

            if (refGHPages?.trim()) {

                sh "git clone -b gh-pages  ${gitRepoUrl} gh-pages"

                sh 'cp -rv target/generated-docs/* gh-pages/ && ' +
                        'cd gh-pages && mv index.pdf ' + artifactId + '.pdf'

                sh "cd gh-pages && git config user.email ${gitEmail} && git config user.name ${gitUser} " +
                        "&& (git add --ignore-errors * || true ) && git commit -m 'generated documentation' "

                retry(3) {
                    sh "cd gh-pages && git push origin gh-pages"
                }

            } else {

                sh 'git checkout -b gh-pages'

                sh 'cp -rv target/generated-docs/* .'

                sh "git config user.email ${gitEmail} && git config user.name ${gitUser} && " +
                        "(git add --ignore-errors * || true ) && git commit -m 'generated documentation' " +
                        "&& git push origin gh-pages"
            }

        } else {
            sh "${docgenScript}"
        }

    }

}
