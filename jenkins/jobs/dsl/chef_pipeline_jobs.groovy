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
     shell('''set +x
            |echo "=.=.=.=.=.=.=.=.=.=.=.=."
            |set -x'''.stripMargin()) 
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
  description("This job runs sanity tests of the cookbook.")
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
    copyArtifacts("Cookbook_Package") {
        buildSelector {
          buildNumber('${B}')
      }
    }
    shell('''set +x
            |echo "=.=.=.=.=.=.=.=.=.=.=.=."
            |set -x'''.stripMargin())
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
  description("This job runs unit tests of the cookbook.")
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
    shell('''set +x
            |echo "=.=.=.=.=.=.=.=.=.=.=.=."
            |set -x'''.stripMargin())
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
  description("This job tests a converge with the cookbook")
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
  description("This job uploads the cookbook to the non-production Chef Server")
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
  label("java8")
  steps {
    shell('''set +x
            |echo "=.=.=.=.=.=.=.=.=.=.=.=."
            |set -x'''.stripMargin())
  }

}

