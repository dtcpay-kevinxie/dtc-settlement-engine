// Gitlab connection in Jenkins > Configuration > Gitlab
properties([gitLabConnection('Gitlab Connection')])

gitlabBuilds(builds: ['unit test','build','dockerize','deploy','integration test']){

    node(label: 'linux-builder'){

        checkout scm

        stage ('unit test') {
            gitlabCommitStatus (name: "unit test") {
                // TODO:
                // // Use the Maven configured in Jenkins (via Global Tool)
                // // TODO: Fix unit tests
                // withMaven(maven: 'maven', options: [artifactsPublisher(disabled: true)]) {
                //     sh "mvn clean verify -Pclover.all --update-snapshots -DskipTests"
                // }
                // // TODO: Add threshold for code coverage
                // step([
                //     $class: 'CloverPublisher',
                //     cloverReportDir: 'target/site',
                //     cloverReportFileName: 'clover.xml'
                //   ])
            }
        }

        stage ('build') {
            gitlabCommitStatus (name: "build") {
                // Use the Maven configured in Jenkins (via Global Tool)
                withMaven(maven: 'maven', options: [artifactsPublisher(disabled: true)]) {
                    sh "mvn clean deploy --update-snapshots -DskipTests"
                }
                // Get project details we gonna use later
                pom = readMavenPom file: 'pom.xml'
            }
        }

        stage ('dockerize') {
            gitlabCommitStatus (name: "dockerize") {

                // These environment variables were set in
                // Jenkins configuration > Global Properties
                def registryUrl = env.DOCKER_REGISTRY_URL
                def registryCredentials = env.DOCKER_REGISTRY_CREDENTIALS_ID
                def imageTag = pom.version.toLowerCase()

                // Build and push docker image to registry
                docker.withRegistry(registryUrl, registryCredentials) {
                    def customImage = docker.build("${pom.artifactId.toLowerCase()}:${imageTag}")
                    customImage.push()
                }
            }
        }

        stage('deploy') {
            gitlabCommitStatus (name: "deploy") {
                // Call deployer pipeline
                build job: 'devops/deploy',
                    parameters: [string(name: 'APP_NAME', value: pom.artifactId),
                                string(name: 'APP_VERSION', value: pom.version),
                                string(name: 'GIT_BRANCH', value: env.BRANCH_NAME)]
            }
        }

        stage('integration test') {
            gitlabCommitStatus (name: "integration test") {
                // TODO
                // // Call integration test pipeline
                // build job: 'qa/test-suite',
                //     parameters: [string(name: 'APP_NAME', value: pom.artifactId),
                //                 string(name: 'APP_VERSION', value: pom.version),
                //                 string(name: 'GIT_BRANCH', value: env.BRANCH_NAME)]
            }
        }
    }
}
