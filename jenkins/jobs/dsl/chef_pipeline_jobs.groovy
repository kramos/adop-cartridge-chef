// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"
def projectFolderName = "${PROJECT_NAME}"

// Variables
def referenceAppgitRepo = "vim"
def referenceAppGitUrl = "ssh://jenkins@gerrit:29418/${PROJECT_NAME}/" + referenceAppgitRepo
//def regressionTestGitRepo = "adop-cartridge-java-regression-tests"
//def regressionTestGitUrl = "ssh://jenkins@gerrit:29418/${PROJECT_NAME}/" + regressionTestGitRepo

// Jobs
def cookbookPackage = freeStyleJob(projectFolderName + "/Cookbook_Package")
def chefSanityTest = freeStyleJob(projectFolderName + "/Sanity_Test")
def chefUnitTest = freeStyleJob(projectFolderName + "/Unit_Test")
def chefConvergeTest = freeStyleJob(projectFolderName + "/Converge_Test")
def chefPromoteNonProdChefServer = freeStyleJob(projectFolderName + "/Promote_NonProd_Chef_Server")

// Views
def pipelineView = buildPipelineView(projectFolderName + "/Chef_Pipeline")

pipelineView.with{
    title('Chef Pipeline')
    displayedBuilds(5)
    selectedJob(projectFolderName + "/Cookbook_Package")
    showPipelineParameters()
    showPipelineDefinitionHeader()
    refreshFrequency(5)
}

cookbookPackage.with{
  description("This job downloads dependenices")
  wrappers {
    preBuildCleanup()
    injectPasswords()
    maskPasswords()
    sshAgent("adop-jenkins-master")
  }
  scm{
    git{
      remote{
        url(referenceAppGitUrl)
        credentials("adop-jenkins-master")
      }
      branch("*/master")
    }
  }
  environmentVariables {
      env('WORKSPACE_NAME',workspaceFolderName)
      env('PROJECT_NAME',projectFolderName)
  }
  label("java8")
  triggers{
    gerrit{
      events{
        refUpdated()
      }
      configure { gerritxml ->
        gerritxml / 'gerritProjects' {
          'com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject' {
            compareType("PLAIN")
            pattern(projectFolderName + "/" + referenceAppgitRepo)
            'branches' {
              'com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.Branch' {
                compareType("PLAIN")
                pattern("master")
              }
            }
          }
        }
        gerritxml / serverName("ADOP Gerrit")
      }
    }
  }
  steps {
    maven{
      goals('clean install -DskipTests')
      mavenInstallation("ADOP Maven")
    }
  }
  publishers{
    archiveArtifacts("**/*")
    downstreamParameterized{
      trigger(projectFolderName + "/Sanity_Test"){
        condition("UNSTABLE_OR_BETTER")
        parameters{
          predefinedProp("B",'${BUILD_NUMBER}')
          predefinedProp("PARENT_BUILD", '${JOB_NAME}')
        }
      }
    }
  }
}

chefSanityTest.with{
  description("This job runs unit tests on Java Spring reference application.")
  parameters{
    stringParam("B",'',"Parent build number")
    stringParam("PARENT_BUILD","Cookbook_Package","Parent build name")
  }
  wrappers {
    preBuildCleanup()
    injectPasswords()
    maskPasswords()
    sshAgent("adop-jenkins-master")
  }
  environmentVariables {
      env('WORKSPACE_NAME',workspaceFolderName)
      env('PROJECT_NAME',projectFolderName)
  }
  label("java8")
  steps {
  }
  steps {
    copyArtifacts("Cookbook_Package") {
        buildSelector {
          buildNumber('${B}')
      }
    }
    maven{
      goals('clean test')
      mavenInstallation("ADOP Maven")
    }
  }
  publishers{
    downstreamParameterized{
      trigger(projectFolderName + "/Unit_Test"){
        condition("UNSTABLE_OR_BETTER")
        parameters{
          predefinedProp("B",'${B}')
          predefinedProp("PARENT_BUILD",'${PARENT_BUILD}')
        }
      }
    }
  }
}

chefUnitTest.with{
  description("This job runs code quality analysis for Java reference application using SonarQube.")
  parameters{
    stringParam("B",'',"Parent build number")
    stringParam("PARENT_BUILD","Cookbook_Package","Parent build name")
  }
  environmentVariables {
      env('WORKSPACE_NAME',workspaceFolderName)
      env('PROJECT_NAME',projectFolderName)
  }
  wrappers {
    preBuildCleanup()
    injectPasswords()
    maskPasswords()
    sshAgent("adop-jenkins-master")
  }
  label("java8")
  steps {
    copyArtifacts('Cookbook_Package') {
        buildSelector {
          buildNumber('${B}')
      }
    }
  }
  configure { myProject ->
    myProject / builders << 'hudson.plugins.sonar.SonarRunnerBuilder'(plugin:"sonar@2.2.1"){
      project('sonar-project.properties')
      properties('''sonar.projectKey=org.java.reference-application
sonar.projectName=Reference application
sonar.projectVersion=1.0.0
sonar.sources=src
sonar.language=java
sonar.sourceEncoding=UTF-8
sonar.scm.enabled=false''')
      javaOpts()
      jdk('(Inherit From Job)')
      task()
    }
  }
  publishers{
    downstreamParameterized{
      trigger(projectFolderName + "/Converge_Test"){
        condition("UNSTABLE_OR_BETTER")
        parameters{
          predefinedProp("B",'${B}')
          predefinedProp("PARENT_BUILD", '${PARENT_BUILD}')
        }
      }
    }
  }
}

chefConvergeTest.with{
  description("This job deploys the java reference application to the CI environment")
  parameters{
    stringParam("B",'',"Parent build number")
    stringParam("PARENT_BUILD","Cookbook_Package","Parent build name")
    stringParam("ENVIRONMENT_NAME","CI","Name of the environment.")
  }
  wrappers {
    preBuildCleanup()
    injectPasswords()
    maskPasswords()
    sshAgent("adop-jenkins-master")
  }
  environmentVariables {
      env('WORKSPACE_NAME',workspaceFolderName)
      env('PROJECT_NAME',projectFolderName)
  }
  label("docker")
  steps {
    copyArtifacts("Cookbook_Package") {
        buildSelector {
          buildNumber('${B}')
      }
    }
    shell('''set +x
            |export SERVICE_NAME="$(echo ${PROJECT_NAME} | tr '/' '_')_${ENVIRONMENT_NAME}"
            |docker cp ${WORKSPACE}/target/petclinic.war  ${SERVICE_NAME}:/usr/local/tomcat/webapps/
            |docker restart ${SERVICE_NAME}
            |COUNT=1
            |while ! curl -q http://${SERVICE_NAME}:8080/petclinic -o /dev/null 
            |do
            |  if [ ${COUNT} -gt 10 ]; then
            |      echo "Docker build failed even after ${COUNT}. Please investigate."
            |      exit 1
            |  fi
            |  echo "Application is not up yet. Retrying ..Attempt (${COUNT})"
            |  sleep 5  
            |  COUNT=$((COUNT+1))
            |done
            |echo "=.=.=.=.=.=.=.=.=.=.=.=."
            |echo "=.=.=.=.=.=.=.=.=.=.=.=."
            |echo "Environment URL (replace PUBLIC_IP with your public ip address where you access jenkins from) : http://${SERVICE_NAME}.PUBLIC_IP.xip.io/petclinic"
            |echo "=.=.=.=.=.=.=.=.=.=.=.=."
            |echo "=.=.=.=.=.=.=.=.=.=.=.=."
            |set -x'''.stripMargin())
  }
  publishers{
    downstreamParameterized{
      trigger(projectFolderName + "/Promote_NonProd_Chef_Server"){
        condition("UNSTABLE_OR_BETTER")
        parameters{
          predefinedProp("B",'${B}')
          predefinedProp("PARENT_BUILD", '${PARENT_BUILD}')
          predefinedProp("ENVIRONMENT_NAME", '${ENVIRONMENT_NAME}')
        }
      }
    }
  }
}

chefPromoteNonProdChefServer.with{
  description("This job runs regression tests on deployed java application")
  parameters{
    stringParam("B",'',"Parent build number")
    stringParam("PARENT_BUILD","Cookbook_Package","Parent build name")
    stringParam("ENVIRONMENT_NAME","CI","Name of the environment.")
  }
  scm{
    git{
      remote{
        url(regressionTestGitUrl)
        credentials("adop-jenkins-master")
      }
      branch("*/master")
    }
  }
  wrappers {
    preBuildCleanup()
    injectPasswords()
    maskPasswords()
    sshAgent("adop-jenkins-master")
  }
  environmentVariables {
      env('WORKSPACE_NAME',workspaceFolderName)
      env('PROJECT_NAME',projectFolderName)
  }
  label("java8")
  steps {
    shell('''set +x
            |export SERVICE_NAME="$(echo ${PROJECT_NAME} | tr '/' '_')_${ENVIRONMENT_NAME}"
            |echo "SERVICE_NAME=${SERVICE_NAME}" > env.properties
            |set -x'''.stripMargin())
    environmentVariables {
      propertiesFile('env.properties')
    }
    maven{
      goals('clean test -B -DPETCLINIC_URL=http://${SERVICE_NAME}:8080/petclinic')
      mavenInstallation("ADOP Maven")
    }
  }
  configure{myProject ->
    myProject / 'publishers' << 'net.masterthought.jenkins.CucumberReportPublisher'(plugin:'cucumber-reports@0.1.0'){
      jsonReportDirectory("")
      pluginUrlPath("")
      fileIncludePattern("")
      fileExcludePattern("")
      skippedFails("false")
      pendingFails("false")
      undefinedFails("false")
      missingFails("false")
      noFlashCharts("false")
      ignoreFailedTests("false")
      parallelTesting("false")
    }
  }
}

