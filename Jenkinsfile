#!groovy

import groovy.xml.XmlUtil

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
				export GPG_TTY=/dev/null # see also https://wiki.archlinux.org/index.php/GnuPG#Invalid_IPC_response_and_Inappropriate_ioctl_for_device
				gpg --import "${GPGKEYFILE}"
				echo "C66B4B348F6D4071047318C52483051C0D49EDA0:6:" | gpg --import-ownertrust
			"""
			
			job()
			
		} finally {
			// ensure that the valuable signing key is deleted again
			sh """
				export GPG_TTY=/dev/null # see also https://wiki.archlinux.org/index.php/GnuPG#Invalid_IPC_response_and_Inappropriate_ioctl_for_device
				gpg --batch --yes --delete-secret-keys C66B4B348F6D4071047318C52483051C0D49EDA0
				gpg --batch --yes --delete-keys C66B4B348F6D4071047318C52483051C0D49EDA0
			"""
		}
	}
	

}

def springCloudCliPasswordTest(params) {
	assert params.currentVersion != null : "Current Version at springCloudCliPasswordTest not set"

	dir("../springCloudTest") {
		sh """
			wget -nv https://repo.spring.io/release/org/springframework/boot/spring-boot-cli/2.5.4/spring-boot-cli-2.5.4-bin.tar.gz
			tar xzvf spring-boot-cli-2.5.4-bin.tar.gz
			rm -f spring-boot-cli-2.5.4-bin.tar.gz
			cd spring-2.5.4/bin
			
			./spring install org.springframework.cloud:spring-cloud-cli:2.2.4.RELEASE
		"""
		
		withCredentials([usernamePassword(credentialsId: 'bluemix-ibm-cf-platform', passwordVariable: 'CFPASSWORD', usernameVariable: 'CFUSER')]) {
			sh """#!/bin/bash -xe
				spring-2.5.4/bin/spring encrypt '${CFPASSWORD}' --key somekey > encrypted.txt
				
			"""
		}
		
		// prepare configuration file
		sh """
			cp ../build/test/integration/springCloudCliPassword/bluemix.yaml .
		"""
		
		sh """#!/bin/bash +xe
			cat encrypted.txt
			CFPASSWORDENC=`cat encrypted.txt`
			sed -i -e 's/%%CRYPTEDPASSWORD%%/\${CFPASSWORDENC}/g' bluemix.yaml
		"""
		
		sh """
			cat bluemix.yaml
			rm -f encrypted.txt
		"""
		
		// Run Test itself
		sh """#!/bin/bash -xe
			ls -al .
		
			# see also https://docs.spring.io/spring-boot/docs/2.0.5.RELEASE/reference/html/boot-features-external-config.html
			ENCRYPT_KEY=somekey java -jar ../build/target/promregator-${params.currentVersion}.jar --spring.config.name=bluemix &
			# on ENCRYPT_KEY see also https://cloud.spring.io/spring-cloud-config/reference/html/#_key_management
			
			export PROMREGATOR_PID=\$!
			
			echo "Promregator is running on \$PROMREGATOR_PID; giving it 20 seconds to start up"
			
			sleep 20
			
			curl -m 10 http://localhost:8080/discovery > discovery.json
			cat discovery.json

			kill \$PROMREGATOR_PID
		"""
		
		// verify that the expected app could be discovered (i.e. the discovery file isn't empty)
		sh """#!/bin/bash +xe
			CHECKRESULT=`jq -r '.[] | select(.labels.__meta_promregator_target_applicationName=="testapp2") | .labels.__meta_promregator_target_applicationName' discovery.json`
			if [ "\$CHECKRESULT" != "testapp2" ]; then
				echo "Test has failed: Discovery response does not include the expected application name 'testapp2'"
				exit 1
			fi
			
			rm -f discovery.json bluemix.yaml
		"""
	}
}

timestamps {
	node("slave") {
		def checkoutBranchName = env.BRANCH_NAME // see also https://stackoverflow.com/a/36332154
	
		dir("build") {
			checkout scm
			
			sh """
				echo Building with Java version
				javac -version
				java -version
				javadoc --version # Warning! Yes, at javadoc it's a double-hyphen!
			"""
			
			def currentVersion = getVersion()
			println "Current version is ${currentVersion}"
			
			stage("Build") {
				try {
					boolean withSigning = !currentVersion.endsWith("-SNAPSHOT")
				
					if (checkoutBranchName.equals("master")) {
						withCredentials([string(credentialsId: 'promregator_sonarcloud', variable: 'sonarlogin')]) {
							sh """#!/bin/bash -xe
								export CF_PASSWORD=dummypassword
								mvn -U -B -PwithTests -Prelease '-Dsonar.login=${sonarlogin}' \
									clean verify sonar:sonar
		
							"""
						}
					} else {
						sh """#!/bin/bash -xe
							export CF_PASSWORD=dummypassword
							mvn -U -B -PwithTests -Prelease clean verify
						"""
					}


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
			
			stage("Integration Test") {
				springCloudCliPasswordTest currentVersion: currentVersion
			}
			
			stage("SecDependency Scan") {
				sh """
					mvn -B -DsuppressionFiles=./secscan/owasp-suppression.xml org.owasp:dependency-check-maven:6.3.1:check
				"""
				
				archiveArtifacts "target/dependency-check-report.html"
			}

			
			stage("Tests for Docker Image") {
				sh """
					chmod +x docker/data/promregator.sh
					chmod +x test/docker/startscript/*.sh
					cd test/docker/startscript
					./runtests.sh
				"""
			}
			
			stage("Official Build / OSSRH") {
				if (!currentVersion.endsWith("-SNAPSHOT")) {
					// Problem: org.apache.maven.plugins:maven-gpg-plugin:sign org.sonatype.plugins:nexus-staging-maven-plugin:deploy
					// must be run within the same "package" call. Otherwise they don't do their job.
					// This means that we have to run a new build process. This build process will create
					// other CRC values than before. Unfortunately, we can't prevent this.
					// Yet, at the same time, we want to make sure that we will use the very same version
					// for building the docker image than for the version that we ship as jar (via github release page).
					withCredentials([usernamePassword(credentialsId: 'JIRA_SONARTYPE', passwordVariable: 'JIRA_PASSWORD', usernameVariable: 'JIRA_USERNAME')]) {
						jiraUsername = XmlUtil.escapeXml("${JIRA_USERNAME}")
						jiraPassword = XmlUtil.escapeXml("${JIRA_PASSWORD}")
	
					
						// see also https://central.sonatype.org/pages/apache-maven.html
						String settingsXML = """<settings>
  <servers>
    <server>
      <id>ossrh</id>
      <username>${jiraUsername}</username>
      <password>${jiraPassword}</password>
    </server>
  </servers>
  <profiles>
    <profile>
      <id>withDeploy</id>
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
						writeFile file : "settings.xml", text: settingsXML
					}
				
					try {
						runWithGPG() {
							sh """
								mvn --settings ./settings.xml -U -B -DskipTests -Prelease -PwithDeploy package org.apache.maven.plugins:maven-gpg-plugin:sign org.sonatype.plugins:nexus-staging-maven-plugin:deploy
							"""
						}
					} finally {
						sh """
							rm -f ./settings.xml
						"""
					}
				}
			}

			
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

			stage("Hashsumming/Archiving") {
				// show the current state
				sh "ls -al"
				
				if (!currentVersion.endsWith("-SNAPSHOT")) {
					// signing only happens, if deployment is in place
					archiveArtifacts "target/promregator-${currentVersion}*.asc"
				}
				
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
