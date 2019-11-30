package org.cloudfoundry.promregator.fetcher;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import io.prometheus.client.Collector;
import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;
import io.prometheus.client.Collector.Type;

/* Unfortunately, there is nothing provided for this by Prometheus.io :-(
 * So, we have to do this ourselves.
 * Details of the format are described at https://prometheus.io/docs/instrumenting/exposition_formats/
 */
public class TextFormat004Parser {
	private static final Logger log = Logger.getLogger(TextFormat004Parser.class);
	
	private String textFormat004data;
	
	private HashMap<String, String> mapHelps = new HashMap<>();
	private HashMap<String, Collector.Type> mapTypes = new HashMap<>();
	
	private HashMap<String, Collector.MetricFamilySamples> mapMFS = new HashMap<>();
	
	private static final Pattern PATTERN_HELP = Pattern.compile("^#[ \t]+HELP[ \t]+");
	private static final Pattern PATTERN_TYPE = Pattern.compile("^#[ \t]+TYPE[ \t]+");
	private static final Pattern PATTERN_COMMENT = Pattern.compile("^#");
	private static final Pattern PATTERN_EMPTYLINE = Pattern.compile("^[ \t]*$");
	
	private static final Pattern PATTERN_PARSE_HELP = Pattern.compile("^#[ \t]+HELP[ \t]+([a-zA-Z0-9:_\\\"]+)[ \\t]+(.*)$");
	private static final Pattern PATTERN_PARSE_TYPE = Pattern.compile("^#[ \t]+TYPE[ \t]+([a-zA-Z0-9:_\\\"]+)[ \\t]+([a-zA-Z]*)$");
	
	private static final Pattern PATTERN_TOKEN_WITH_SPACE_SEPARATOR = Pattern.compile("^([a-zA-Z0-9:_\\\"]+)");
	private static final Pattern PATTERN_SKIP_SPACES = Pattern.compile("[ \\\\t]*");
	private static final Pattern PATTERN_PARSE_LABELBLOCK = Pattern.compile("^\\{(.+)\\}");
	private static final Pattern PATTERN_PARSE_VALUE = Pattern.compile("^([-+]?[0-9]*\\.?[0-9]+[eE]?[-+]?[0-9]*)[ \\\\t]*");
	private static final Pattern PATTERN_PARSE_VALUETEXT = Pattern.compile("^(NaN|Nan|\\+Inf|-Inf)[ \\\\t]*");

	private static final Pattern PATTERN_LABEL_WITH_STARTING_QUOTES = Pattern.compile("([a-zA-Z0-9:_\\\"]+)=\"");

	
	public TextFormat004Parser(String textFormat004data) {
		this.textFormat004data = textFormat004data;
	}
	
	public HashMap<String, Collector.MetricFamilySamples> parse() {
		this.reset();
		
		StringTokenizer lines = new StringTokenizer(this.textFormat004data, "\n");
		
		while(lines.hasMoreTokens()) {
			String line = lines.nextToken();
			
			// warning! Order of IF tests matter!
			if (this.isEmptyLine(line)) {
				continue;
			} else if (this.isHelpLine(line)) {
				this.parseHelpLine(line);
				continue;
			} else if (this.isTypeLine(line)) {
				this.parseTypeLine(line);
				continue;
			} else if (this.isCommentLine(line)) {
				continue;
			}
			
			// we need to assume that this is a metric line
			this.parseMetric(line);
		}
		
		return this.mapMFS;
	}
	
	private void parseMetric(String line) {
		Matcher mMetricName = PATTERN_TOKEN_WITH_SPACE_SEPARATOR.matcher(line);
		if (!mMetricName.find()) {
			log.warn("Detected metric line without proper metric name: "+line);
			return;
		}
		String metricName = mMetricName.group(1);
		
		String rest = line.substring(mMetricName.end());
		
		// skip spaces if there...
		Matcher mSkipSpaces = PATTERN_SKIP_SPACES.matcher(rest);
		if (mSkipSpaces.find()) {
			rest = rest.substring(mSkipSpaces.end());
		}
		
		// check if the metric has an optional block of labels
		Matcher mLabelBlock = PATTERN_PARSE_LABELBLOCK.matcher(rest);
		Labels labels = null;
		if (mLabelBlock.find()) {
			labels = this.parseLabelBlock(mLabelBlock.group(1));
			rest = rest.substring(mLabelBlock.end());
			
			mSkipSpaces = PATTERN_SKIP_SPACES.matcher(rest);
			if (mSkipSpaces.find()) {
				rest = rest.substring(mSkipSpaces.end());
			}
		}
		
		double value = 0.0f;
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
				log.warn(String.format("Unable to parse value in metric line: %s", line));
				return;
			}
			valueString = mValue.group(1);
			
			try {
				value = this.parseGoDouble(valueString);
			} catch (NumberFormatException nfe) {
				log.warn(String.format("Unable to parse value in metrics line properly: %s", line), nfe);
				return;
			}
			end = mValue.end();
		}

		/*
		 * currently not supported in java simpleclient!
		 */
		// optional timestamp
		/*
		double timestamp = 0.0;
		if (!"".equals(rest)) {
			try {
				timestamp = this.parseGoDouble(rest);
			} catch (NumberFormatException nfe) {
				log.warn("Unable to parse timestamp in metrics line properly: "+line, nfe);
				return;
			}
		}*/
		
		Collector.Type type = determineType(metricName);
		if (type == Type.UNTYPED) {
			log.info(String.format("Definition of metric %s without type information (assuming untyped)", metricName));
		}

		List<String> labelNames = labels == null ? new LinkedList<>() : labels.getNames();
		List<String> labelValues = labels == null ? new LinkedList<>() : labels.getValues();
		
		Sample sample = new Sample(metricName, labelNames, labelValues, value);

		if (type.equals(Collector.Type.COUNTER) || type.equals(Collector.Type.GAUGE) || type.equals(Collector.Type.UNTYPED)) {
			MetricFamilySamples mfsStored = this.mapMFS.get(metricName);
			if (mfsStored != null) {
				// we already have created a metric for this line; we just have to add the sample
				mfsStored.samples.add(sample);
			} else {
				// there is no such MFS entry yet; we have to create one
				List<Sample> samples = new LinkedList<>();
				samples.add(sample);

				String docString = this.mapHelps.get(metricName);
				/*
				 * mfs.help must not be empty - see also  https://github.com/promregator/promregator/issues/73
				 */
				if (docString == null) {
					docString = "";
				}

				Collector.MetricFamilySamples mfs = new Collector.MetricFamilySamples(metricName, type, docString, samples);
				this.mapMFS.put(metricName, mfs);
			}
		} else if (type.equals(Collector.Type.HISTOGRAM)) {
			String baseMetricName = determineBaseMetricName(metricName);
			
			// is this already in our Map?
			Collector.MetricFamilySamples mfs = this.mapMFS.get(baseMetricName);
			if (mfs == null) {
				// no, we have to create a new one

				String docString = this.mapHelps.get(baseMetricName);
				/*
				 * mfs.help must not be empty - see also  https://github.com/promregator/promregator/issues/73
				 */
				if (docString == null) {
					docString = "";
				}
				
				mfs = new Collector.MetricFamilySamples(baseMetricName, type, docString, new LinkedList<>());
				this.mapMFS.put(baseMetricName, mfs);
			}
			
			mfs.samples.add(sample);
			
		} else if (type.equals(Collector.Type.SUMMARY)) {
			String baseMetricName = determineBaseMetricName(metricName);
			

			// is this already in our Map?
			Collector.MetricFamilySamples mfs = this.mapMFS.get(baseMetricName);
			if (mfs == null) {
				// no, we have to create a new one
				
				String docString = this.mapHelps.get(baseMetricName);
				/*
				 * mfs.help must not be empty - see also  https://github.com/promregator/promregator/issues/73
				 */
				if (docString == null) {
					docString = "";
				}
				
				mfs = new Collector.MetricFamilySamples(baseMetricName, type, docString, new LinkedList<>());
				this.mapMFS.put(baseMetricName, mfs);
			}
			
			mfs.samples.add(sample);
		} else {
			log.warn(String.format("Unknown type %s; unclear how to handle this; skipping", type.toString()));
			return;
		}
	}

	private Type determineType(String metricName) {
		Collector.Type type = null;
		// first check if the metric is typed natively.
		type = this.mapTypes.get(metricName);
		if (type != null) {
			return type;
		}
		
		// try to get the baseMetricName
		String baseMetricName = determineBaseMetricName(metricName);
		type = this.mapTypes.get(baseMetricName);
		// check that this also really makes sense and is a type, which requires baseMetricNames
		if (type == Type.HISTOGRAM || type == Type.SUMMARY) {
			return type;
		}
		
		// we have no clue what this metric is all about
		return Collector.Type.UNTYPED;
	}

	private static String determineBaseMetricName(String metricName) {
		if (metricName.endsWith("_bucket")) {
			return metricName.substring(0, metricName.length()-7);
		} else if (metricName.endsWith("_sum")) {
			return metricName.substring(0, metricName.length()-4);
		} else if (metricName.endsWith("_count")) {
			return metricName.substring(0, metricName.length()-6);
		} else if (metricName.endsWith("_max")) {
			// provided as additional metric by micrometer
			return metricName.substring(0, metricName.length()-4);
		}
		
		return metricName;
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
	
	private Labels parseLabelBlock(String block) {
		String buffer = block;
		
		Labels l = new Labels();
		
		Matcher mLabelName = PATTERN_LABEL_WITH_STARTING_QUOTES.matcher(buffer);
		while (mLabelName.find()) {
			String labelName = unescapeToken(mLabelName.group(1));
			
			buffer = buffer.substring(mLabelName.end());
			int endOfValue = indexEndOfValue(buffer);
			if (endOfValue == -1) {
				log.warn("Missing termination of value in label block: "+ block);
				return l;
			}
			
			String labelValue = buffer.substring(0, endOfValue-1);
			
			buffer = buffer.substring(endOfValue);

			labelValue = unescapeToken(labelValue);
			l.addNameValuePair(labelName, labelValue);
			
			while (buffer.startsWith(",")) {
				buffer = buffer.substring(1);
			}
			
			mLabelName = PATTERN_LABEL_WITH_STARTING_QUOTES.matcher(buffer);
		}
		
		return l;
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

	private double parseGoDouble(String goDouble) {
		double value = 0.0;
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
	
	private void parseTypeLine(String line) {
		Matcher m = PATTERN_PARSE_TYPE.matcher(line);
		if (!m.matches()) {
			log.warn("TYPE line could not be properly matched: "+line);
			return;
		}
		
		String metricName = unescapeToken(m.group(1));
		String typeString = m.group(2);
		
		Collector.Type type = null;
		if (typeString.equalsIgnoreCase("gauge")) {
			type = Collector.Type.GAUGE;
		} else if (typeString.equalsIgnoreCase("counter")) {
			type = Collector.Type.COUNTER;
		} else if (typeString.equalsIgnoreCase("summary")) {
			type = Collector.Type.SUMMARY;
		} else if (typeString.equalsIgnoreCase("histogram")) {
			type = Collector.Type.HISTOGRAM;
		} else if (typeString.equalsIgnoreCase("untyped")) {
			type = Collector.Type.UNTYPED;
		} else {
			log.warn("Unable to parse type from TYPE line: "+line);
			return;
		}
		
		this.mapTypes.put(metricName, type);
	}

	private void parseHelpLine(String line) {
		Matcher m = PATTERN_PARSE_HELP.matcher(line);
		if (!m.matches()) {
			log.warn("HELP line could not be properly matched: "+line);
			return;
		}
		
		String metricName = unescapeToken(m.group(1));
		String docString = unescapeDocString(m.group(2));
		
		this.mapHelps.put(metricName, docString);
	}

	private boolean isHelpLine(String line) {
		return PATTERN_HELP.matcher(line).find();
	}
	
	private boolean isTypeLine(String line) {
		return PATTERN_TYPE.matcher(line).find();
	}

	private boolean isCommentLine(String line) {
		return PATTERN_COMMENT.matcher(line).find();
	}

	private boolean isEmptyLine(String line) {
		return PATTERN_EMPTYLINE.matcher(line).find();
	}
	
	private String unescapeDocString(String s) {
		if (s == null)
			return null;
		
		String sTemp = s.replace("\\\\", "\\");
		sTemp = sTemp.replace("\\n", "\n");
		
		return sTemp;
	}
	
	private String unescapeToken(String s) {
		if (s == null)
			return null;
		
		String sTemp = s.replace("\\\\", "\\");
		sTemp = sTemp.replace("\\\"", "\"");
		sTemp = sTemp.replace("\\n", "\n");
		
		return sTemp;
	}
	
	private void reset() {
		this.mapHelps.clear();
		this.mapTypes.clear();
		this.mapMFS.clear();
	}
	
}
