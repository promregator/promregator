#!groovy

timestamps {
	node("slave") {
		sh "echo test"
		
		dir("build") {
			git url: 'https://github.com/promregator/promregator.git'
			
			# build the artifacts
			sh "mvn -B clean package"
			
			# store the result for later retrieval on releasing
			archiveArtifacts 'target/promregator*.jar'
		}
	}
}