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
 DOCKER_CREDENTIAL_ID = 'alibaba-dockerhub-id'
 GITEE_CREDENTIAL_ID = 'gitee'
 KUBECONFIG_CREDENTIAL_ID = 'demo-kubeconfig'
 REGISTRY = 'registry.cn-beijing.aliyuncs.com'
 DOCKERHUB_NAMESPACE = 'pretty-devops'
 GITEE_ACCOUNT = 'wangpo1991'
 SONAR_CREDENTIAL_ID = 'sonar-token'
 BRANCH_NAME='master'
}


  stages {
    stage('拉取代码&编译代码') {
      steps {
        git(credentialsId: 'gitee', url: 'https://gitee.com/wangpo1991/cat-mall.git', branch: 'master', changelog: true, poll: false)
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
       sh "mvn sonar:sonar  -gs `pwd`/settings.xml -Dsonar.branch=$BRANCH_NAME -Dsonar.login=$SONAR_TOKEN"
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
          sh 'mvn -o -Dmaven.test.skip=true -gs `pwd`/settings.xml clean package'
            withCredentials([usernamePassword(passwordVariable : 'DOCKER_PASSWORD' ,usernameVariable : 'DOCKER_USERNAME' ,credentialsId : "$DOCKER_CREDENTIAL_ID" ,)]) {
                sh 'echo  "$DOCKER_PASSWORD" | docker login  --username="$DOCKER_USERNAME" $REGISTRY --password-stdin'
                sh 'cd $PROJECT_NAME  &&  docker build -f Dockerfile -t $REGISTRY/$DOCKERHUB_NAMESPACE/$PROJECT_NAME:SNAPSHOT-$BRANCH_NAME-$BUILD_NUMBER .'
                sh 'docker push  $REGISTRY/$DOCKERHUB_NAMESPACE/$PROJECT_NAME:SNAPSHOT-$BRANCH_NAME-$BUILD_NUMBER'
            }
        }
    }
}
stage('推送最新镜像'){
     when{
       branch 'master'
     }
     steps{
          container ('maven') {
            sh 'docker tag  $REGISTRY/$DOCKERHUB_NAMESPACE/$PROJECT_NAME:SNAPSHOT-$BRANCH_NAME-$BUILD_NUMBER $REGISTRY/$DOCKERHUB_NAMESPACE/$PROJECT_NAME:latest '
            sh 'docker push  $REGISTRY/$DOCKERHUB_NAMESPACE/$PROJECT_NAME:latest '
          }
     }
  }
stage('部署开发环境') {
when{
  branch 'master'
}
steps {
  input(id: "deploy-to-dev-$PROJECT_NAME", message: "是否部署 $PROJECT_NAME 到开发环境?")
    sh "echo 当前目录是 `pwd`"
  kubernetesDeploy(configs: "$PROJECT_NAME/deploy/**", enableConfigSubstitution: true, kubeconfigId: "$KUBECONFIG_CREDENTIAL_ID")
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
          withCredentials([usernamePassword(credentialsId: "$GITEE_CREDENTIAL_ID", passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
            sh 'git config --global user.email "powang2020@gmail.com" '
            sh 'git config --global user.name "powang2020" '
            sh 'git tag -a $PROJECT_VERSION -m "$PROJECT_VERSION" '
            sh 'git push http://$GIT_USERNAME:$GIT_PASSWORD@gitee.com/$GITEE_ACCOUNT/cat-mall.git --tags --ipv4'
          }
        sh 'docker tag  $REGISTRY/$DOCKERHUB_NAMESPACE/$PROJECT_NAME:SNAPSHOT-$BRANCH_NAME-$BUILD_NUMBER $REGISTRY/$DOCKERHUB_NAMESPACE/$PROJECT_NAME:$PROJECT_VERSION '
        sh 'docker push  $REGISTRY/$DOCKERHUB_NAMESPACE/$PROJECT_NAME:$PROJECT_VERSION '
  }
  }
}



  }
}