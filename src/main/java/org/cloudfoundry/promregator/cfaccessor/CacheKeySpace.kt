package org.cloudfoundry.promregator.cfaccessor

data class CacheKeySpace(
        val api: String,
        val orgId: String?,
        val spaceName: String?)