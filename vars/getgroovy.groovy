import hudson.tools.InstallSourceProperty;
import hudson.tools.ToolProperty;
import hudson.tools.ToolPropertyDescriptor;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import hudson.util.DescribableList;
import hudson.plugins.groovy.GroovyInstaller;
import hudson.plugins.groovy.GroovyInstallation;
/* 
  Installs Groovy on the node.
  The idea was taken from: https://devops.lv/2016/12/05/jenkins-groovy-auto-installer/
  and https://github.com/jenkinsci/jenkins-scripts/blob/master/scriptler/configMavenAutoInstaller.groovy

  COMMENT 1: If we use this code directly (not as a separate method) then we get
  java.io.NotSerializableException: hudson.plugins.groovy.GroovyInstaller

  COMMENT 2: For some reason inst.getExecutable(channel) returns null. I use inst.forNode(node, null).getExecutable(channel) instead.
  
  TODO: Check if https://jenkinsci.github.io/job-dsl-plugin/#method/javaposse.jobdsl.dsl.helpers.step.MultiJobStepContext.groovyCommand
  works better.
 */
@NonCPS
def installGroovyOnSlave(String version) {

    if ((version == null) || (version == "")) {
        version = "2.4.7" // some default should be
    }
    
    /* Set up properties for our new Groovy installation */
    def node = Jenkins.getInstance().slaves.find({it.name == env.NODE_NAME})
    def proplist = new DescribableList<ToolProperty<?>, ToolPropertyDescriptor>()
    def installers = new ArrayList<GroovyInstaller>()
    def autoInstaller = new GroovyInstaller(version)
    installers.add(autoInstaller)
    def InstallSourceProperty isp = new InstallSourceProperty(installers)
    proplist.add(isp)
    def inst = new GroovyInstallation("Groovy", "", proplist)
 
    /* Download and install */
    autoInstaller.performInstallation(inst, node, null)

    /* Define and add our Groovy installation to Jenkins */
    def descriptor = Jenkins.getInstance().getDescriptor("hudson.plugins.groovy.Groovy")
    descriptor.setInstallations(inst)
    descriptor.save()
    
    /* Output the current Groovy installation's path, to verify that it is ready for use */
    def groovyInstPath = getGroovyExecutable(version)
    println("Groovy " + version + " is installed in the node " + node.getDisplayName())
}

/* Returns the groovy executable path on the current node
   If version is specified tries to find the specified version of groovy,
   otherwise returns the first groovy installation that was found.
 */
@NonCPS
def getGroovyExecutable(String version=null) {
    
    def node = Jenkins.getInstance().slaves.find({it.name == env.NODE_NAME})
    def channel = node.getComputer().getChannel()
    
    for (ToolInstallation tInstallation : Jenkins.getInstance().getDescriptor("hudson.plugins.groovy.Groovy").getInstallations()) {
        if (tInstallation instanceof GroovyInstallation) {
            if ((version == null) || (version == "")) {
                // any version is appropriate for us
                return tInstallation.forNode(node, null).getExecutable(channel)
            }
            // otherwise check for version
            for (ToolProperty prop in tInstallation.getProperties()) {
                if (prop instanceof InstallSourceProperty) {
                    for (ToolInstaller tInstaller: prop.installers) {
                        if (
                            (tInstaller instanceof GroovyInstaller) &&
                            (tInstaller.id.equals(version))
                        )
                        return tInstallation.forNode(node, null).getExecutable(channel)
                    }
                }
            }
        }
    }
    
    return null
}

/* Wrapper function. Returns the groovy executable path as getGroovyExecutable()
   but additionally tries to install if the groovy installation was not found.
 */
def getGroovy(String version=null) {
    def installedGroovy = getGroovyExecutable(version)
    if (installedGroovy != null) {
        return installedGroovy
    } else {
        installGroovyOnSlave(version)
    }
    return getGroovyExecutable(version)
}
