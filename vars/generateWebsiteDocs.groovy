#!/usr/bin/groovy

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def project = config.project
    def artifactId = config.artifactId
    def docVersion = config.docVersion
    def docgenScript = config.docgenScript ?: null

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
                checkout scm
                sh 'git clone -b gh-pages ' + gitRepoUrl + ' gh-pages'
                sh 'cp -rv target/generated-docs/* gh-pages/ && ' +
                        'cd gh-pages && mv gh-pages/index.pdf ' + 'gh-pages/' + artifactId + '.pdf' + '2>/dev/null'
                +' && git add --ignore-errors * && git commit -m "generated documentation" ' +
                        '&& git push origin gh-pages'
            } else {
                sh 'git checkout -b gh-pages'
                sh 'cp -rv target/generated-docs/* .'
                sh("(git add --ignore-errors * || true ) && git commit -m 'generated documentation' " +
                        "&& git push origin gh-pages")
            }

        } else {
            sh "${docgenScript}"
        }

    }

}
