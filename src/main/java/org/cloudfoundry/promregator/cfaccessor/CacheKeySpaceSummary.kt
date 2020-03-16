package org.cloudfoundry.promregator.cfaccessor

data class CacheKeySpaceSummary(
        val api: String,
        val spaceId: String
)