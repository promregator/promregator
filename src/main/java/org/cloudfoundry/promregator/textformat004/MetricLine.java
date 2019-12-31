package org.cloudfoundry.promregator.textformat004;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import io.prometheus.client.Collector.MetricFamilySamples.Sample;

public class MetricLine {
	private static final Logger log = Logger.getLogger(MetricLine.class);
	
	private static final Pattern PATTERN_TOKEN_WITH_SPACE_SEPARATOR = Pattern.compile("^([a-zA-Z0-9:_\\\"]+)");
	private static final Pattern PATTERN_SKIP_SPACES = Pattern.compile("[ \\\\t]*");
	private static final Pattern PATTERN_PARSE_LABELBLOCK = Pattern.compile("^\\{(.+)\\}");
	private static final Pattern PATTERN_PARSE_VALUE = Pattern.compile("^([-+]?[0-9]*\\.?[0-9]+[eE]?[-+]?[0-9]*)[ \\\\t]*");
	private static final Pattern PATTERN_PARSE_VALUETEXT = Pattern.compile("^(NaN|Nan|\\+Inf|-Inf)[ \\\\t]*");

	private static final Pattern PATTERN_LABEL_WITH_STARTING_QUOTES = Pattern.compile("([a-zA-Z0-9:_\\\"]+)=\"");

	private String line;
	private String rest;

	public MetricLine(String line) {
		this.line = line;
	}

	private static class ParseException extends Exception {

		private static final long serialVersionUID = 291766462453425092L;

		public ParseException(String s) {
			super(s);
		}

		public ParseException(String message, Throwable cause) {
			super(message, cause);
		}
		
	}
	
	public Sample parse() {
		this.rest = line;

		try {
			final String metricName = this.parseMetricName();
			this.skipSpacesIfThereAre();
			
			// check if the metric has an optional block of labels
			final Labels labels = this.parseLabels();
			
			final double value = this.parseValue();
			/*
			 * The timestamp is optional.
			 * Note that timestamps for metrics are currently not supported by the java Simpleclient!
			 * Yet it is defined the official protocol specification. 
			 */
			final double timestamp = this.parseTimestamp();
			
			final List<String> labelNames = labels == null ? new LinkedList<>() : labels.getNames();
			final List<String> labelValues = labels == null ? new LinkedList<>() : labels.getValues();
			
			return new Sample(metricName, labelNames, labelValues, value);
		} catch (ParseException e) {
			return null;
		}
	}
	
	private String parseMetricName() throws ParseException {
		Matcher mMetricName = PATTERN_TOKEN_WITH_SPACE_SEPARATOR.matcher(this.rest);
		if (!mMetricName.find()) {
			String errorMsg = "Detected metric line without proper metric name: "+this.rest;
			log.warn(errorMsg);
			throw new ParseException(errorMsg);
		}
		final String metricName = mMetricName.group(1);
		
		this.rest = this.rest.substring(mMetricName.end());
		return metricName;
	}

	private void skipSpacesIfThereAre() {
		final Matcher mSkipSpaces = PATTERN_SKIP_SPACES.matcher(this.rest);
		if (mSkipSpaces.find()) {
			this.rest = this.rest.substring(mSkipSpaces.end());
		}
	}

	
	private Labels parseLabels() {
		Matcher mLabelBlock = PATTERN_PARSE_LABELBLOCK.matcher(this.rest);
		Labels labels = null;
		if (mLabelBlock.find()) {
			labels = this.parseLabelBlock(mLabelBlock.group(1));
			this.rest = this.rest.substring(mLabelBlock.end());
			
			this.skipSpacesIfThereAre();
		}
		
		return labels;
	}
	
	private Labels parseLabelBlock(String block) {
		String buffer = block;
		
		Labels l = new Labels();
		
		Matcher mLabelName = PATTERN_LABEL_WITH_STARTING_QUOTES.matcher(buffer);
		while (mLabelName.find()) {
			String labelName = Utils.unescapeToken(mLabelName.group(1));
			
			buffer = buffer.substring(mLabelName.end());
			int endOfValue = indexEndOfValue(buffer);
			if (endOfValue == -1) {
				log.warn("Missing termination of value in label block: "+ block);
				return l;
			}
			
			String labelValue = buffer.substring(0, endOfValue-1);
			
			buffer = buffer.substring(endOfValue);

			labelValue = Utils.unescapeToken(labelValue);
			l.addNameValuePair(labelName, labelValue);
			
			while (buffer.startsWith(",")) {
				buffer = buffer.substring(1);
			}
			
			mLabelName = PATTERN_LABEL_WITH_STARTING_QUOTES.matcher(buffer);
		}
		
		return l;
	}
	
	private static class Labels {
		private List<String> names = new LinkedList<>();
		private List<String> values = new LinkedList<>();
		
		public Labels() {
			super();
		}

		public List<String> getNames() {
			return names;
		}

		public List<String> getValues() {
			return values;
		}
		
		public void addNameValuePair(String name, String value) {
			this.names.add(name);
			this.values.add(value);
		}
	}
	
	private double parseValue() throws ParseException {
		double value;
		String valueString = null;
		Matcher mValueText = PATTERN_PARSE_VALUETEXT.matcher(rest);
		int end = 0;
		if (mValueText.find()) {
			valueString = mValueText.group(1);
			value = this.parseGoDouble(valueString); // NB: an exception cannot be thrown here (and the exception in fact is an Error)
			end = mValueText.end();
		} else {
			Matcher mValue = PATTERN_PARSE_VALUE.matcher(rest);
			if (!mValue.find()) {
				final String errorMsg = String.format("Unable to parse value in metric line: %s", line);
				log.warn(errorMsg);
				throw new ParseException(errorMsg);
			}
			valueString = mValue.group(1);
			
			try {
				value = this.parseGoDouble(valueString);
			} catch (NumberFormatException nfe) {
				final String errorMsg = String.format("Unable to parse value in metrics line properly: %s", line);
				log.warn(errorMsg, nfe);
				throw new ParseException(errorMsg, nfe);
			}
			end = mValue.end();
		}
		
		this.rest = this.rest.substring(end);
		
		return value;
	}
	
	private double parseGoDouble(String goDouble) {
		double value;
		if (goDouble.startsWith("Nan")) {
			value = Double.NaN;
		} else if (goDouble.startsWith("NaN")) {
			value = Double.NaN;
		} else if (goDouble.startsWith("+Inf")) {
			value = Double.POSITIVE_INFINITY;
		} else if (goDouble.startsWith("-Inf")) {
			value = Double.NEGATIVE_INFINITY;
		} else {
			// Let's try to parse it
			value = Double.parseDouble(goDouble);
		}
		return value;
	}

	private double parseTimestamp() throws ParseException {
		double timestamp = 0.0;
		if (!"".equals(rest)) {
			try {
				timestamp = this.parseGoDouble(rest);
			} catch (NumberFormatException nfe) {
				final String errorMsg = String.format("Unable to parse timestamp in metrics line properly: %s", line);
				log.warn(errorMsg, nfe);
				throw new ParseException(errorMsg, nfe);
			}
		}
		
		return timestamp;
	}
	
	private static int indexEndOfValue(String buffer) {
		boolean escaped = false;
		
		for (int i = 0; i< buffer.length(); i++) {
			char c = buffer.charAt(i);
			if (c == '\\') {
				escaped = !escaped;
			} else if (c == '\"') {
				if (escaped) {
					// that's a double-quote which we need to ignore
					escaped = false;
					continue;
				} else {
					// we found the end of the string
					return i + 1; // skip the terminating quote
				}
			}
			
			if (c != '\\' && escaped) {
				escaped = false;
			}
		}
		
		// missing termination of the value!
		return -1;
	}
	
}
