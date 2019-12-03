# General

We are open for accepting changes, be it for fixing bugs, improving our coding style and implementation of features. Use the [Github Pull Request](https://github.com/promregator/promregator/pulls) mechanism to send in your changes.

If you intend to start a larger change, it is recommended to briefly get in touch with us to ensure that there is no unnecessary double work (someone else could also be already working on the topic) and to verify that the concept/idea is acceptable from a conceptual point of view (we do not want you to make unnecessary work, forcing you to do rework in case there is a discrepancy in direction). 

Major bug fixes and/or new features require unit test coverage. Pull requests, which do not have unit test coverage, will be rejected/put on hold until this is fixed. 

# Style Guides

* We are using Java Code Conventions.
* The newline mode is "Unix" (character of newlines in source code files is "LF"). Special files (i.e. if a standard enforces a different newline) may have a deviating newline format. 
* When using the `equals()` and `hashcode()` generator of Eclipse ("Source"->"Generate hashcode() and equals()"), make sure that
  * you have set "generate method comments",
  * you have not set "use 'instanceof' to compare types", and
  * you have set "use blocks in 'if' statements" (required for [SonarQube compatibility](https://github.com/promregator/promregator/pull/129#discussion_r352373057)).


# Proviso

This list of guideline is incomplete and will be enhanced when need arises.
