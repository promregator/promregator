#!groovy

timestamps {
	node("slave") {
		sh "echo test"
		
		dir("build") {
			git url: 'https://github.com/promregator/promregator.git'
			
			stage("Build") {
				sh "mvn -B clean package"
			}
			
			stage("Archive") {
				archiveArtifacts 'target/promregator*.jar'
			}
		}
	}
}