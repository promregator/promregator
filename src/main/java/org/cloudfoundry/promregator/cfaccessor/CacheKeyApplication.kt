package org.cloudfoundry.promregator.cfaccessor

data class CacheKeyApplication(
        val api: String,
        val orgId: String?,
        val spaceId: String?,
        val applicationName: String?)