package org.cloudfoundry.promregator.cfaccessor

data class CacheKeyAppsInSpace(
        val api: String,
        val orgId: String?,
        val spaceId: String?)