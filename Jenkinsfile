#!groovy

timestamps {
	node("slave") {
		dir("build") {
			git url: 'https://github.com/promregator/promregator.git'
			
			stage("Build") {
				sh """
				cf.password=dummypassword
				export cf.password
				mvn -B clean package
				"""
			}
			
			stage("Archive") {
				archiveArtifacts 'target/promregator*.jar'
			}
			
			stage("Post-processing quality data") {
				step([
					class: 'JacocoPublisher'
				])
			}
		}
	}
}