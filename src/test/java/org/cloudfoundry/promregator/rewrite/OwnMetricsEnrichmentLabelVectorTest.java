package org.cloudfoundry.promregator.rewrite;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class OwnMetricsEnrichmentLabelVectorTest {

	@Test
	void testWithLabelRewrite() {
		OwnMetricsEnrichmentLabelVector subject = new OwnMetricsEnrichmentLabelVector("prefix_", "orgNameValue", "spaceNameValue", "appNameValue", "instanceIdValue:42");
		String[] labelNames = subject.getEnrichingLabelNames();
		
		Assertions.assertEquals(5, labelNames.length);
		
		Assertions.assertEquals("prefix_org_name", labelNames[0]);
		Assertions.assertEquals("prefix_space_name", labelNames[1]);
		Assertions.assertEquals("prefix_app_name", labelNames[2]);
		Assertions.assertEquals("prefix_cf_instance_id", labelNames[3]);
		Assertions.assertEquals("prefix_cf_instance_number", labelNames[4]);
		
		List<String> labelValues = subject.getEnrichedLabelValues();
		
		Assertions.assertEquals(labelValues.size(), 5);
		
		Assertions.assertEquals("orgNameValue", labelValues.get(0));
		Assertions.assertEquals("spaceNameValue", labelValues.get(1));
		Assertions.assertEquals("appNameValue", labelValues.get(2));
		Assertions.assertEquals("instanceIdValue:42", labelValues.get(3));
		Assertions.assertEquals("42", labelValues.get(4));
	}
	
	@Test
	void testWithoutLabelRewrite() {
		OwnMetricsEnrichmentLabelVector subject = new OwnMetricsEnrichmentLabelVector(null, "orgNameValue", "spaceNameValue", "appNameValue", "instanceIdValue:42");
		String[] labelNames = subject.getEnrichingLabelNames();
		
		Assertions.assertEquals(5, labelNames.length);
		
		Assertions.assertEquals("org_name", labelNames[0]);
		Assertions.assertEquals("space_name", labelNames[1]);
		Assertions.assertEquals("app_name", labelNames[2]);
		Assertions.assertEquals("cf_instance_id", labelNames[3]);
		Assertions.assertEquals("cf_instance_number", labelNames[4]);
		
		List<String> labelValues = subject.getEnrichedLabelValues();
		
		Assertions.assertEquals(labelValues.size(), 5);
		
		Assertions.assertEquals("orgNameValue", labelValues.get(0));
		Assertions.assertEquals("spaceNameValue", labelValues.get(1));
		Assertions.assertEquals("appNameValue", labelValues.get(2));
		Assertions.assertEquals("instanceIdValue:42", labelValues.get(3));
		Assertions.assertEquals("42", labelValues.get(4));
	}

}
