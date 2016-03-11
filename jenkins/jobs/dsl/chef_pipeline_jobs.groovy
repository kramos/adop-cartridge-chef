// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"
def projectFolderName = "${PROJECT_NAME}"

// Variables
def referenceAppgitRepo = "vim"
def referenceAppGitUrl = "ssh://jenkins@gerrit:29418/${PROJECT_NAME}/" + referenceAppgitRepo
def chefUtilsRepo = "adop-cartridge-chef-scripts"
def chefUtilsGitUrl = "ssh://jenkins@gerrit:29418/${PROJECT_NAME}/" + chefUtilsRepo 

// Jobs
def chefGetCookboks = freeStyleJob(projectFolderName + "/Get_Cookbooks")
def chefSanityTest = freeStyleJob(projectFolderName + "/Sanity_Test")
def chefUnitTest = freeStyleJob(projectFolderName + "/Unit_Test")
def chefConvergeTest = freeStyleJob(projectFolderName + "/Converge_Test")
def chefPromoteNonProdChefServer = freeStyleJob(projectFolderName + "/Promote_NonProd_Chef_Server")

// Views
def pipelineView = buildPipelineView(projectFolderName + "/Chef_Pipeline")

pipelineView.with{
    title('Chef Pipeline')
    displayedBuilds(5)
    selectedJob(projectFolderName + "/Get_Cookbooks")
    showPipelineParameters()
    showPipelineDefinitionHeader()
    refreshFrequency(5)
}

chefGetCookboks.with{
  description("This job downloads the cookbook.")
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
  label("docker")
  steps {
    shell('''set +x
            |echo
            |set -x'''.stripMargin())
  }
  publishers{
    archiveArtifacts("**/*")
    downstreamParameterized{
      trigger(projectFolderName + "/Sanity_Test"){
        condition("UNSTABLE_OR_BETTER")
        parameters{
          predefinedProp("B",'${BUILD_NUMBER}')
          predefinedProp("PARENT_BUILD",'${JOB_NAME}')
        }
      }
    }
  }
}

chefSanityTest.with{
  description("This job runs sanity checks on the cookbook.")
  parameters{
    stringParam("B",'',"Parent build number")
    stringParam("PARENT_BUILD","Get_Cookbooks","Parent build name")
  }
  environmentVariables {
      env('WORKSPACE_NAME',workspaceFolderName)
      env('PROJECT_NAME',projectFolderName)
  }
  scm{
    git{
      remote{
        url(chefUtilsGitUrl)
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
  label("docker")
  steps {
    copyArtifacts('Get_Cookbooks') {
        buildSelector {
          buildNumber('${B}')
      }
    }
    shell('''set -x
            |docker run --rm -v jenkins_slave_home:/jenkins_slave_home/ kramos/adop-chef-test /jenkins_slave_home/$JOB_NAME/ChefCI/chef_sanity_test.sh /jenkins_slave_home/$JOB_NAME/
            |'''.stripMargin())
  }
  publishers{
    archiveArtifacts("ChefCI/**/*")
    downstreamParameterized{
      trigger(projectFolderName + "/Unit_Test"){
        condition("UNSTABLE_OR_BETTER")
        parameters{
          predefinedProp("B",'${BUILD_NUMBER}')
          predefinedProp("PARENT_BUILD", '${JOB_NAME}')
        }
      }
    }
  }
}


chefUnitTest.with{
  description("This job runs sanity tests of the cookbook.")
  parameters{
    stringParam("B",'',"Parent build number")
    stringParam("PARENT_BUILD","Sanity_Test","Parent build name")
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
  label("docker")
  steps {
    copyArtifacts('Sanity_Test') {
        buildSelector {
          buildNumber('${B}')
      }
    }
    shell('''set -x
            |docker run --rm -v jenkins_slave_home:/jenkins_slave_home/ kramos/adop-chef-test /jenkins_slave_home/$JOB_NAME/ChefCI/chef_unit_test.sh /jenkins_slave_home/$JOB_NAME/
            |'''.stripMargin())
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
    stringParam("PARENT_BUILD","Get_Cookbooks","Parent build name")
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
    shell('''set +x
            |echo TODO clean up
            |'''.stripMargin())
    copyArtifacts("Sanity_Test") {
        buildSelector {
          buildNumber('${B}')
      }
    }
    shell('''set +x
            |echo TODO run kitchen
            |'''.stripMargin())
  }
  publishers{
    downstreamParameterized{
      trigger(projectFolderName + "/Promote_NonProd_Chef_Server"){
        condition("UNSTABLE_OR_BETTER")
        parameters{
          predefinedProp("B",'${B}')
          predefinedProp("PARENT_BUILD", '${PARENT_BUILD}')
        }
      }
    }
  }
}

chefPromoteNonProdChefServer.with{
  description("This job uploads the cookbook to the non-production Chef Server")
  parameters{
    stringParam("B",'',"Parent build number")
    stringParam("PARENT_BUILD","Get_Cookbooks","Parent build name")
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
    shell('''set +x
            |echo TODO converge 
            |set -x'''.stripMargin())
  }

}

