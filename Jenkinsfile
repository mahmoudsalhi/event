pipeline {
    agent any
    tools {
        maven 'Maven3'
        jdk 'JDK17'
    }
    stages {
        stage('Checkout') {
            steps { git branch: 'main', url: 'https://github.com/mahmoudsalhi/event.git' }
        }
        stage('Build') {
            steps { sh 'mvn clean compile' }
        }
        
        
    }
}