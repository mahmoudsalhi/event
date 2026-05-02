pipeline {
    agent any
    tools {
        maven 'Maven3'
        jdk 'JDK17'
    }
    environment {
        DOCKER_IMAGE = 'mahmoud3331/event-backend'
        DOCKER_TAG = "${BUILD_NUMBER}"
        CONTAINER_NAME = 'event-backend-app'
    }
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        stage('Build') {
            steps {
                sh 'mvn clean compile'
            }
        }
        
        stage('Package') {
            steps {
                sh 'mvn package -DskipTests'
            }
        }
        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('SonarQube') {
                    sh 'mvn sonar:sonar -Dsonar.projectKey=event-backend -Dsonar.projectName=event-backend'
                }
            }
        }
        stage('Docker Build') {
            steps {
                sh "docker build -t ${DOCKER_IMAGE}:${DOCKER_TAG} -t ${DOCKER_IMAGE}:latest ."
            }
        }
        stage('Docker Push') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub-creds', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                    sh 'echo $DOCKER_PASS | docker login -u $DOCKER_USER --password-stdin'
                    sh "docker push ${DOCKER_IMAGE}:${DOCKER_TAG}"
                    sh "docker push ${DOCKER_IMAGE}:latest"
                }
            }
        }
        stage('Deploy') {
            steps {
                sh "docker stop ${CONTAINER_NAME} || true"
                sh "docker rm ${CONTAINER_NAME} || true"
                sh """docker run -d \
                    --name ${CONTAINER_NAME} \
                    --network devops-net \
                    -p 8082:8082 \
                    -e SPRING_DATASOURCE_URL=jdbc:postgresql://51.255.203.187:5432/events \
                    -e SPRING_DATASOURCE_USERNAME=mahmoud \
                    -e SPRING_DATASOURCE_PASSWORD=mahmoud123 \
                    -e SPRING_JPA_HIBERNATE_DDL_AUTO=update \
                    -e EUREKA_CLIENT_REGISTER_WITH_EUREKA=false \
                    -e EUREKA_CLIENT_FETCH_REGISTRY=false \
                    ${DOCKER_IMAGE}:latest"""
            }
        }

    }

}