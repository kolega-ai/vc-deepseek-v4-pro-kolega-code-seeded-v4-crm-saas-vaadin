pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        timeout(time: 60, unit: 'MINUTES')
    }

    environment {
        // CI build of the Vaadin production frontend (node is auto-provisioned).
        ORG_GRADLE_PROJECT_vaadinProductionMode = 'true'
    }

    stages {
        stage('Build') {
            steps {
                sh './gradlew clean compileJava compileTestJava --no-daemon --stacktrace'
            }
        }

        stage('Test') {
            steps {
                sh './gradlew test --no-daemon'
            }
            post {
                always {
                    junit '**/build/test-results/test/*.xml'
                }
            }
        }

        stage('Package') {
            steps {
                sh './gradlew bootJar --no-daemon'
            }
            post {
                success {
                    archiveArtifacts artifacts: 'channel/build/libs/channel-*.jar, decision/build/libs/decision-*.jar, core-mock/build/libs/core-mock-*.jar, batch/build/libs/batch-*.jar',
                                     fingerprint: true,
                                     onlyIfSuccessful: true
                }
            }
        }

        stage('Compose smoke') {
            when {
                expression {
                    // Only on hosts with Docker available.
                    script {
                        def docker = sh(script: 'command -v docker || true', returnStdout: true).trim()
                        return !docker.isEmpty()
                    }
                }
            }
            steps {
                sh '''
                    docker compose up -d --build
                    # wait for the channel tier to become healthy (seed + migrations included)
                    for i in $(seq 1 60); do
                      if curl -fs http://localhost:8080/actuator/health > /dev/null 2>&1; then break; fi
                      sleep 5
                    done
                    # authorization service must answer a real login round-trip
                    curl -fs -X POST http://localhost:8081/api/auth/login \
                      -H "Content-Type: application/json" \
                      -H "X-Service-Token: local-channel-service-token" \
                      -d '{"username":"admin","password":"Admin!Crmops2026"}' | grep -q '"token"'
                '''
            }
            post {
                always {
                    sh 'docker compose down -v || true'
                }
            }
        }
    }

    post {
        always {
            cleanWs(cleanWhenNotBuilt: false)
        }
    }
}
