#!groovy

import groovy.xml.XmlUtil

properties([
  // see also https://docs.cloudbees.com/docs/cloudbees-ci-kb/latest/client-and-managed-masters/how-do-i-set-discard-old-builds-for-a-multi-branch-pipeline-job
  // only keep 25 builds to prevent disk usage from growing out of control
  buildDiscarder(
    logRotator(
      artifactDaysToKeepStr: '', 
      artifactNumToKeepStr: '', 
      daysToKeepStr: '', 
      numToKeepStr: '25',
    ),
  ),
])


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
		// For most recent version look at https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-cli
		def springBootCLIVersion = "2.7.18"
		// Warning! Bumping this to 3.0.x requires JDK19 or higher!
		
		// For most recent version see also https://mvnrepository.com/artifact/org.springframework.cloud/spring-cloud-cli
		def springCloudCLIVersion = "3.1.1"
	
		sh """
			wget -nv https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-cli/${springBootCLIVersion}/spring-boot-cli-${springBootCLIVersion}-bin.tar.gz
			tar xzvf spring-boot-cli-${springBootCLIVersion}-bin.tar.gz
			rm -f spring-boot-cli-${springBootCLIVersion}-bin.tar.gz
			cd spring-${springBootCLIVersion}/bin
			
			./spring install org.springframework.cloud:spring-cloud-cli:${springCloudCLIVersion}
		"""
		
		withCredentials([usernamePassword(credentialsId: 'bosh-lite-cf-platform', passwordVariable: 'CFPASSWORD', usernameVariable: 'CFUSER')]) {
			sh """#!/bin/bash -xe
				spring-${springBootCLIVersion}/bin/spring encrypt '${CFPASSWORD}' --key somekey > encrypted.txt
				
			"""
		}
		
		// prepare configuration file
		sh """
			cp ../build/test/integration/springCloudCliPassword/bosh-lite.yaml .
		"""
		
		sh """#!/bin/bash +xe
			CFPASSWORDENC=`cat encrypted.txt`
			sed -i -e "s/%%CRYPTEDPASSWORD%%/\$CFPASSWORDENC/g" bosh-lite.yaml
		"""
		
		sh """
			rm -f encrypted.txt
		"""
		
		// Run Test itself
		sh """#!/bin/bash -xe
			ls -al .
		
			# see also https://docs.spring.io/spring-boot/docs/2.0.5.RELEASE/reference/html/boot-features-external-config.html
			ENCRYPT_KEY=somekey java -jar ../build/target/promregator-${params.currentVersion}.jar --spring.config.name=bosh-lite &
			# on ENCRYPT_KEY see also https://cloud.spring.io/spring-cloud-config/reference/html/#_key_management
			
			export PROMREGATOR_PID=\$!
			
			echo "Promregator is running on \$PROMREGATOR_PID; giving it 30 seconds to start up"
			
			sleep 30
			
			curl -m 10 -u 'integrationtest:1ntegrat1ontest' http://localhost:8080/discovery > discovery.json
			cat discovery.json

			kill \$PROMREGATOR_PID
		"""
		
		// verify that the expected app could be discovered (i.e. the discovery file isn't empty)
		sh """#!/bin/bash -xe
			CHECKRESULT=`jq -r '.[] | select(.labels.__meta_promregator_target_applicationName=="testapp") | .labels.__meta_promregator_target_applicationName' discovery.json`
			if [ "\$CHECKRESULT" != "testapp" ]; then
				echo "Test has failed: Discovery response does not include the expected application name 'testapp'"
				exit 1
			fi
			
			rm -f discovery.json bosh-lite.yaml
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
								mvn -U -B -PwithTests -Prelease '-Dsonar.token=${sonarlogin}' \
									clean verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar
							"""
							/*
							 * For sonarCloud integration approach see also 
							 * https://docs.sonarsource.com/sonarqube-cloud/advanced-setup/ci-based-analysis/sonarscanner-for-maven/
							 *
							 * Warning! Since August 2025, the token at sonarcloud automatically expires after 60 days
							 * of inactivity!
							 * Create a new token at https://sonarcloud.io/account/security and store the credential at
							 * http://router1:8081/manage/credentials/store/system/domain/_/credential/promregator_sonarcloud/
							 */
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
					mvn -B -DsuppressionFiles=./secscan/owasp-suppression.xml -DossindexAnalyzerEnabled=false org.owasp:dependency-check-maven:12.1.1:check
				"""
				// see also https://rieckpil.de/fix-sonatype-oss-index-errors-for-owasp-maven-plugin/
				
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
					/* Link to documentation of publishing plugin:
					 * https://central.sonatype.org/publish/publish-portal-maven/
					 */
				
					// Problem: org.apache.maven.plugins:maven-gpg-plugin:sign and org.sonatype.central:central-publishing-maven-plugin
					// must be run within the same "package" call. Otherwise they don't do their job.
					// This means that we have to run a new build process. This build process will create
					// other CRC values than before. Unfortunately, we can't prevent this.
					// Yet, at the same time, we want to make sure that we will use the very same version
					// for building the docker image than for the version that we ship as jar (via github release page).
					
					/*
					 * Password management see https://central.sonatype.org/publish/generate-portal-token/
					 */
					withCredentials([usernamePassword(credentialsId: 'JIRA_SONARTYPE', passwordVariable: 'JIRA_PASSWORD', usernameVariable: 'JIRA_USERNAME')]) {
						jiraUsername = XmlUtil.escapeXml("${JIRA_USERNAME}")
						jiraPassword = XmlUtil.escapeXml("${JIRA_PASSWORD}")
	
					
						// see also https://central.sonatype.org/pages/apache-maven.html
						String settingsXML = """<settings>
  <servers>
    <server>
      <id>central</id>
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
								echo "Publishing of component can be followed at https://central.sonatype.com/publishing/deployments"
								mvn --settings ./settings.xml -U -B -DskipTests -Prelease -PwithDeploy package org.apache.maven.plugins:maven-gpg-plugin:sign org.sonatype.central:central-publishing-maven-plugin:publish
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
					cat >../promregator-${currentVersion}.hashsums.json <<EOT
{
	"commit" : "`git rev-parse HEAD`",
	"jar": {
		"sha256": "`../tools/hash.sh -sha256 promregator-${currentVersion}.jar`",
		"md5": "`../tools/hash.sh -md5 promregator-${currentVersion}.jar`"
	}
}
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
					cat >promregator-${currentVersion}-docker.hashsums.json <<EOT
{
	"docker-image" : {
		"repo-digest": "${dockerImageIdentifier}",
		"image-digest": "${dockerImageIdentifierCanonical}"
	}
}
EOT
					mv promregator-${currentVersion}.hashsums.json promregator-${currentVersion}-jar.hashsums.json
					jq -s '.[0] * .[1]' promregator-${currentVersion}-jar.hashsums.json promregator-${currentVersion}-docker.hashsums.json > promregator-${currentVersion}.hashsums.json
					rm -f promregator-${currentVersion}-jar.hashsums.json promregator-${currentVersion}-docker.hashsums.json
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
						gpg --clear-sign --personal-digest-preferences SHA512,SHA384,SHA256,SHA224,SHA1 promregator-${currentVersion}.hashsums.json
					"""
				}
				
				sh """
					cat promregator-${currentVersion}.hashsums.json.asc
				"""
				
				archiveArtifacts "promregator-${currentVersion}.hashsums.json"
				archiveArtifacts "promregator-${currentVersion}.hashsums.json.asc"
				
				archiveArtifacts 'target/promregator*.jar'
				
			}
		}
	}
}
