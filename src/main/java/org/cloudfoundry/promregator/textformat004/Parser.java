package org.cloudfoundry.promregator.textformat004;

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
public class Parser {
	private static final Logger log = Logger.getLogger(Parser.class);
	
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
	
	public Parser(String textFormat004data) {
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
		final MetricLine ml = new MetricLine(line);
		Sample sample = ml.parse();
		
		final String metricName = sample.name;
		
		Collector.Type type = determineType(metricName);
		if (type == Type.UNTYPED) {
			log.info(String.format("Definition of metric %s without type information (assuming untyped)", metricName));
		}
		
		if (type.equals(Collector.Type.COUNTER) || type.equals(Collector.Type.GAUGE) || type.equals(Collector.Type.UNTYPED)) {
			this.storeSimpleType(sample, metricName, type);
		} else if (type.equals(Collector.Type.HISTOGRAM) || type.equals(Collector.Type.SUMMARY)) {
			this.storeComplexType(sample, metricName, type);
		} else {
			log.warn(String.format("Unknown type %s; unclear how to handle this; skipping", type.toString()));
			return;
		}
	}

	private void storeSimpleType(Sample sample, final String metricName, Collector.Type type) {
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
	}

	private void storeComplexType(Sample sample, final String metricName, Collector.Type type) {
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
	
	private void parseTypeLine(String line) {
		Matcher m = PATTERN_PARSE_TYPE.matcher(line);
		if (!m.matches()) {
			log.warn("TYPE line could not be properly matched: "+line);
			return;
		}
		
		String metricName = Utils.unescapeToken(m.group(1));
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
		
		String metricName = Utils.unescapeToken(m.group(1));
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
	
	private void reset() {
		this.mapHelps.clear();
		this.mapTypes.clear();
		this.mapMFS.clear();
	}
	
}
