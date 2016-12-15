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
    //Array of Maven Profiles
    def profiles

    def gitRepoUrl = "https://github.com/${config.project}.git"

    checkout scm: [$class          : 'GitSCM',
                   useRemoteConfigs: [[url: gitRepoUrl]],
                   branches        : [[name: "refs/tags/v${releaseVersion}"]]],
            changelog: false, poll: false

    if (docgenScript == null) {
        if (profiles == null) {
            sh 'mvn -Pdoc-html,doc-pdf'
        } else {
            def argProfile = '-P' + profiles.join(",")
            sh "mvn ${argProfile}"
        }
        //TODO - check if gh-pages already exist if not create it ??
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
