#!groovy

def executeShell(command) {
	def result = sh returnStdout: true, script: command
	return result.trim()
}

def getVersion() {
	// for idea, see also https://stackoverflow.com/questions/3545292/how-to-get-maven-project-version-to-the-bash-command-line
	def mvnOutput = executeShell """
		printf 'VERSION=\${project.version}\n0\n' | mvn org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate | egrep '^VERSION'
	"""
	return mvnOutput.substring(8) // trim prefix "VERSION="
}

def runWithGPG(Closure job) {
	withCredentials([file(credentialsId: 'PROMREGATOR_GPG_KEY', variable: 'GPGKEYFILE')]) {
		try {
			sh """
				gpg --import ${GPGKEYFILE}
				echo "C66B4B348F6D4071047318C52483051C0D49EDA0:6:" | gpg --import-ownertrust
			"""
			
			job()
			
		} finally {
			// ensure that the valuable signing key is deleted again
			sh """
				gpg --batch --delete-secret-keys C66B4B348F6D4071047318C52483051C0D49EDA0
				gpg --batch --delete-keys C66B4B348F6D4071047318C52483051C0D49EDA0
			"""
		}
	}
	

}

timestamps {
	node("slave") {
	
		dir("build") {
			checkout scm
			
			stage("Build") {
				try {
					sh """#!/bin/bash -xe
						export CF_PASSWORD=dummypassword
						mvn -U -B -PwithTests clean verify
					"""
				} finally {
					junit 'target/surefire-reports/*.xml'
				}
			}
			
			stage("Post-process Jacoco") {
				
				step([
					$class: 'JacocoPublisher'
				])
			}
			
			stage("Static Code Checks") {
				recordIssues aggregatingResults: true, 
					enabledForFailure: true, 
					healthy: 10, 
					unhealthy: 20,
					ignoreQualityGate: true, 
					sourceCodeEncoding: 'UTF-8', 
					tools: [
						java(reportEncoding: 'UTF-8'),
						pmdParser(pattern: 'target/pmd.xml', reportEncoding: 'UTF-8'),
						findBugs(pattern: 'target/findbugsXml.xml', reportEncoding: 'UTF-8', useRankAsPriority: true),
						cpd(pattern: 'target/cpd.xml', reportEncoding: 'UTF-8'),
						javaDoc(reportEncoding: 'UTF-8'),
						mavenConsole(reportEncoding: 'UTF-8')
					]
			}
			
			stage("Tests for Docker Image") {
				sh """
					chmod +x docker/data/promregator.sh
					chmod +x test/docker/startscript/*.sh
					cd test/docker/startscript
					./runtests.sh
				"""
			}
			
			def currentVersion = getVersion()
			println "Current version is ${currentVersion}"
			
			def imageName = "promregator/promregator:${currentVersion}"
			
			stage("Create Docker Container") {
				
				dir("docker") {
				
					sh """
						ln ../target/promregator-${currentVersion}.jar data/promregator.jar
						
						# Necessary Preperation
						chmod 0750 data
						chmod 0640 data/*
						chmod 0770 data/promregator.sh 
						
						docker build --pull --compress -t ${imageName} .
						
						docker history ${imageName}
					"""
					
					if (!currentVersion.endsWith("-SNAPSHOT")) {
						withCredentials([usernamePassword(
							credentialsId: 'hub.github.com', 
							passwordVariable: 'DOCKER_PASSWORD', 
							usernameVariable: 'DOCKER_USER'
							)]) {
							
							sh """
							echo "$DOCKER_PASSWORD" | docker login -u promregator --password-stdin 
							"""
							
							sh """
							docker push ${imageName}
							"""
						}
					}
					

				}
			}
			
			stage("Generate hashsum file") {
				// determine jar file hash values
				sh """
					cd target
					cat >../promregator-${currentVersion}.hashsums <<EOT
commit(promregator.git)=`git rev-parse HEAD`
`openssl dgst -sha256 -hex promregator-${currentVersion}.jar`
`openssl dgst -md5 -hex promregator-${currentVersion}.jar`
EOT
				"""
			
				def dockerImageIdentifier = null
			
				// determine docker image version
				dockerImageIdentifier = executeShell """
					docker inspect --format='{{.RepoDigests}}' ${imageName}
				"""
				
				if (!dockerImageIdentifier.equals("[]")) {
					// the docker image has a sha256 (note: SNAPSHOT versions do not have one!)
					dockerImageIdentifier = executeShell """
						docker inspect --format='{{index .RepoDigests 0}}' ${imageName}
					"""
					
					def dockerImageIdentifierCanonical = executeShell """
						docker inspect --format='{{.Id}}' ${imageName}
					"""
					sh """
					cat >>promregator-${currentVersion}.hashsums <<EOT
Docker Image Repo Digest: ${dockerImageIdentifier}
Docker Image Id: ${dockerImageIdentifierCanonical}
EOT
					"""
				}
				
			}
			
			stage("Deploy to OSSRH") {
				withCredentials([usernamePassword(credentialsId: 'JIRA_SONARTYPE', passwordVariable: 'JIRA_PASSWORD', usernameVariable: 'JIRA_USERNAME')]) {
					assert !"${JIRA_USERNAME}".contains("<")
					assert !"${JIRA_USERNAME}".contains(">")
					assert !"${JIRA_PASSWORD}".contains("<")
					assert !"${JIRA_PASSWORD}".contains(">")
				
					// see also https://central.sonatype.org/pages/apache-maven.html
					String settingsXML = """
<settings>
  <servers>
    <server>
      <id>ossrh</id>
      <username>${JIRA_USERNAME}</username>
      <password>${JIRA_PASSWORD}</password>
    </server>
  </servers>
  <profiles>
    <profile>
      <id>release</id>
      <activation>
        <activeByDefault>false</activeByDefault>
      </activation>
      <properties>
        <gpg.executable>gpg</gpg.executable>
        <gpg.passphrase></gpg.passphrase>
      </properties>
    </profile>
  </profiles>
</settings>"""
					writeFile file : "~/.m2/settings.xml", text: settingsXML
				}
			

				String withDeploy = currentVersion.endsWith("-SNAPSHOT") ? "" : "-PwithDeploy"
				runWithGPG() {
					// unfortunately this also recreates the JAR files again (so we only can archive them afterwards)
					sh """
						mvn -U -B -DskipTests -Prelease ${withDeploy} verify
						
						ls -al target/*
					"""
				}
				
			}

			stage("Hashsumming/Archiving") {
				// show the current state
				sh "ls -al"
				
				archiveArtifacts "target/promregator-${currentVersion}*.asc"
				
				runWithGPG() {
					sh """
						gpg --clearsign --personal-digest-preferences SHA512,SHA384,SHA256,SHA224,SHA1 promregator-${currentVersion}.hashsums
					"""
				}
				
				sh """
					mv promregator-${currentVersion}.hashsums.asc promregator-${currentVersion}.hashsums
					cat promregator-${currentVersion}.hashsums
				"""
				
				archiveArtifacts "promregator-${currentVersion}.hashsums"
				
				archiveArtifacts 'target/promregator*.jar'
				
			}
		}
		
		
	}
}
