pipeline {
agent {
  node {
    label 'maven'
  }

}
parameters {
string(name: 'TAG_NAME', defaultValue: 'v0.0Beta', description: '')
string(name: 'PROJECT_NAME', defaultValue: 'mall-cart', description: '')
string(name: 'PROJECT_VERSION', defaultValue: 'v0.1', description: '')
}
environment {
 GITEE_CREDENTIAL_ID = 'gitee-id'
 KUBECONFIG_CREDENTIAL_ID = 'demo-kubeconfig'
 REGISTRY = 'docker.io'
 DOCKERHUB_NAMESPACE = 'powang'
 GITHUB_ACCOUNT = 'wangpo1991'
 SONAR_CREDENTIAL_ID = 'sonar-token-x'
 BRANCH_NAME='master'
}
environment {
 DOCKER_CREDENTIAL_ID = 'alibaba-dockerhub-id'
 GITEE_CREDENTIAL_ID = 'gitee-id'
 KUBECONFIG_CREDENTIAL_ID = 'demo-kubeconfig'
 REGISTRY = 'registry.cn-hangzhou.aliyuncs.com'
 DOCKERHUB_NAMESPACE = 'pretty-devops'
 GITEE_ACCOUNT = 'wangpo1991'
 SONAR_CREDENTIAL_ID = 'sonar-token'
 BRANCH_NAME='master'
}
stages {
  stage('拉取代码') {
    steps {
      git(credentialsId: 'dockerhub-id-devops', url: 'https://github.com/wangpo1991/cat-mall.git', branch: 'master', changelog: true, poll: false)
      sh 'echo 正在构建服务 $PROJECT_NAME 版本号 $PROJECT_VERSION'
     container ('maven') {
      sh 'echo 正在编译打包服务 $PROJECT_NAME 版本号 $PROJECT_VERSION'
      sh "mvn  clean install  -Dmaven.test.skip=true  -gs `pwd`/settings.xml "
   }
    }
  }
 stage('代码扫描分析') {
   steps {
      container ('maven') {
      withCredentials([string(credentialsId: "$SONAR_CREDENTIAL_ID", variable: 'SONAR_TOKEN')]) {
      withSonarQubeEnv('sonar') {
        sh "echo 当前目录是 `pwd`"
       sh "mvn sonar:sonar -o -gs `pwd`/settings.xml -Dsonar.branch=$BRANCH_NAME -Dsonar.login=$SONAR_TOKEN"
      }
    }
    timeout(time: 1, unit: 'HOURS') {
      waitForQualityGate abortPipeline: true
    }
  }
}
}

stage ('代码构建并推送快照') {
    steps {
        container ('maven') {
//             sh 'mvn -o -Dmaven.test.skip=true -gs `pwd`/settings.xml clean package'
            sh 'echo  "$DOCKER_PASSWORD" | docker login  --username="$DOCKER_USERNAME" $REGISTRY --password-stdin'
            sh 'cd $PROJECT_NAME  &&  docker build -f Dockerfile -t $REGISTRY/$DOCKERHUB_NAMESPACE/$PROJECT_NAME:SNAPSHOT-$BRANCH_NAME-$BUILD_NUMBER .'
            withCredentials([usernamePassword(passwordVariable : 'DOCKER_PASSWORD' ,usernameVariable : 'DOCKER_USERNAME' ,credentialsId : "$DOCKER_CREDENTIAL_ID" ,)]) {
//                 sh 'echo "$DOCKER_PASSWORD" | docker login $REGISTRY -u "$DOCKER_USERNAME" --password-stdin'
                sh 'docker push  $REGISTRY/$DOCKERHUB_NAMESPACE/$PROJECT_NAME:SNAPSHOT-$BRANCH_NAME-$BUILD_NUMBER'
            }
        }
    }
}
stage('推送镜像'){
     when{
       branch 'master'
     }
     steps{
          container ('maven') {
            sh 'docker tag  $REGISTRY/$DOCKERHUB_NAMESPACE/$APP_NAME:SNAPSHOT-$BRANCH_NAME-$BUILD_NUMBER $REGISTRY/$DOCKERHUB_NAMESPACE/$PROJECT_NAME:latest '
            sh 'docker push  $REGISTRY/$DOCKERHUB_NAMESPACE/$PROJECT_NAME:latest '
          }
     }
  }

stage('部署开发环境') {
  when{
    branch 'master'
  }
  steps {
    input(id: 'deploy-to-dev-$PROJECT_NAME', message: '是否部署$PROJECT_NAME到开发环境?')
    kubernetesDeploy(configs: 'deploy/dev-ol/**', enableConfigSubstitution: true, kubeconfigId: "$KUBECONFIG_CREDENTIAL_ID")
  }
}
stage('发布版本'){
  when{
    expression{
      return params.PROJECT_VERSION =~ /v.*/
    }
  }
  steps {
      container ('maven') {
        input(id: 'release-image-with-tag', message: '是否发布镜像?')
          withCredentials([usernamePassword(credentialsId: "$GITHUB_CREDENTIAL_ID", passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
            sh 'git config --global user.email "powang2020@gmail.com" '
            sh 'git config --global user.name "powang2020" '
            sh 'git tag -a $PROJECT_VERSION -m "$PROJECT_VERSION" '
            sh 'git push http://$GIT_USERNAME:$GIT_PASSWORD@github.com/$GITEE_ACCOUNT/cat-mall.git --tags --ipv4'
          }
        sh 'docker tag  $REGISTRY/$DOCKERHUB_NAMESPACE/$APP_NAME:SNAPSHOT-$BRANCH_NAME-$BUILD_NUMBER $REGISTRY/$DOCKERHUB_NAMESPACE/$PROJECT_NAME:$PROJECT_VERSIONE '
        sh 'docker push  $REGISTRY/$DOCKERHUB_NAMESPACE/$PROJECT_NAME:$PROJECT_VERSION '
  }
  }
}
}

}