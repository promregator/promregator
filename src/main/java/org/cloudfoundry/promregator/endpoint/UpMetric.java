package org.cloudfoundry.promregator.endpoint;

import io.prometheus.client.Gauge;
import io.prometheus.client.Gauge.Child;

public class UpMetric {
	private final Gauge gauge;
	private final Gauge.Child gaugeChild;
	
	public UpMetric(Gauge gauge) {
		super();
		this.gauge = gauge;
		this.gaugeChild = null;
	}

	public UpMetric(Child gaugeChild) {
		super();
		this.gaugeChild = gaugeChild;
		this.gauge = null;
	}
	
	private void setValue(double value) {
		if (this.gauge != null) {
			this.gauge.set(value);
			return;
		}
		
		if (this.gaugeChild != null) {
			this.gaugeChild.set(value);
			return;
		}
	}
	
	public void setUp() {
		this.setValue(1.0);
	}
	
	public void setDown() {
		this.setValue(0.0);
	}
}
