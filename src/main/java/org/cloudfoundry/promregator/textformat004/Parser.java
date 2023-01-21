package org.cloudfoundry.promregator.textformat004;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.prometheus.client.Collector;
import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;
import io.prometheus.client.Collector.Type;

/* Unfortunately, there is nothing provided for this by Prometheus.io :-(
 * So, we have to do this ourselves.
 * Details of the format are described at https://prometheus.io/docs/instrumenting/exposition_formats/
 */
public class Parser {
	private static final Logger log = LoggerFactory.getLogger(Parser.class);
	
	private static final Pattern PATTERN_HELP = Pattern.compile("^#[ \t]+HELP[ \t]+");
	private static final Pattern PATTERN_TYPE = Pattern.compile("^#[ \t]+TYPE[ \t]+");
	// TODO UNIT support still missing
	private static final Pattern PATTERN_COMMENT = Pattern.compile("^#");
	private static final Pattern PATTERN_EMPTYLINE = Pattern.compile("^[ \t]*$");
	
	private static final Pattern PATTERN_PARSE_HELP = Pattern.compile("^#[ \t]+HELP[ \t]+([a-zA-Z0-9:_\\\"]+)[ \\t]+(.*+)$");
	/*
	 * Note: Originally this regex had been
	 * 
	 * ^#[ \t]+HELP[ \t]+([a-zA-Z0-9:_\\\"]+)[ \\t]+(.*)$
	 * 
	 * However this (.*)$ at the end poses a security risk due to exponential backtracking.
	 * The idea is to solve this issue by making the last star possessive.
	 * For a detailed discussion how this works, see https://www.regular-expressions.info/possessive.html
	 * 
	 * (.*)$ therefore becomes (.*+)$
	 * 
	 * Note that no backtracking is needed as . matches any char and the only 
	 * event that still may occur at the end is the end of the string.
	 */
	
	private static final Pattern PATTERN_PARSE_TYPE = Pattern.compile("^#[ \t]+TYPE[ \t]+([a-zA-Z0-9:_\\\"]+)[ \\t]+([a-zA-Z]*)$");
	
	private static final Pattern PATTERN_TOTAL_SUFFIX = Pattern.compile(".*_total$");
	private static final Pattern PATTERN_INFO_SUFFIX = Pattern.compile(".*_info$");

	/*
	 * For a list of reserved suffixes, see also 
	 * https://github.com/OpenObservability/OpenMetrics/blob/1386544931307dff279688f332890c31b6c5de36/specification/OpenMetrics.md#suffixes
	 */
	private static List<String> RESERVED_SUFFIXES = List.of("_total", "_created", "_count", 
			"_sum", "_bucket", "_gcount", "_gsum", "_info", 
			"_max" /* provided as additional metric by micrometer */
			);
	
	private String metricSetData;
	
	private HashMap<String, String> mapHelps = new HashMap<>();
	private HashMap<String, Collector.Type> mapTypes = new HashMap<>();
	
	private HashMap<String, Collector.MetricFamilySamples> mapMFS = new HashMap<>();

	private ParseMode parseMode;
	
	public Parser(String metricSetData, ParseMode parseMode) {
		this.metricSetData = metricSetData;
		this.parseMode = parseMode;
	}
	
	public Parser(String metricSetData) {
		this(metricSetData, ParseMode.CLASSIC_TEXT_004);
	}
	
	public HashMap<String, Collector.MetricFamilySamples> parse() {
		this.reset();
		
		StringTokenizer lines = new StringTokenizer(this.metricSetData, "\n");
		
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
		
		if (this.parseMode == ParseMode.CLASSIC_TEXT_004) {
			// we need to fix some things, which the simpleclient library broke with version 0.11.0...
			this.postProcessSpecialTypes();
		}
		
		return this.mapMFS;
	}

	private void parseMetric(String line) {
		final MetricLine ml = new MetricLine(line);
		
		Sample sample = null;
		try {
			sample = ml.parse();
		} catch (MetricLine.ParseException e) {
			log.warn("Detected non-parsable metric line '{}'", line, e);
			return;
		}
		
		final String sampleName = sample.name;
		
		Collector.Type type = determineType(sampleName);
		if (type == Type.UNKNOWN) {
			log.info("Definition of metric {} without type information (assuming unknown)", sampleName);
		}
		
		if (type.equals(Collector.Type.COUNTER) || type.equals(Collector.Type.GAUGE) || type.equals(Collector.Type.UNKNOWN) || type.equals(Collector.Type.INFO) || type.equals(Collector.Type.STATE_SET)) {
			this.storeSimpleType(sample, sampleName, type);
		} else if (type.equals(Collector.Type.HISTOGRAM) || type.equals(Collector.Type.SUMMARY)) {
			this.storeComplexType(sample, sampleName, type);
		} else {
			log.warn("Unknown type {}; unclear how to handle this; skipping", type);
			// return; can be skipped here
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
		return Collector.Type.UNKNOWN;
	}

	private static String determineBaseMetricName(String metricName) {
		for (String suffix : RESERVED_SUFFIXES) {
			if (metricName.endsWith(suffix)) {
				return metricName.substring(0, metricName.length()-suffix.length());
			}
		}
		
		return metricName;
	}
	
	private void parseTypeLine(String line) {
		Matcher m = PATTERN_PARSE_TYPE.matcher(line);
		if (!m.matches()) {
			log.warn("TYPE line could not be properly matched: {}", line);
			return;
		}
		
		String metricName = Utils.unescapeToken(m.group(1));
		String typeString = m.group(2);
		
		Collector.Type type = null;
		if (typeString.equalsIgnoreCase("gauge")) {
			type = Collector.Type.GAUGE;
		} else if (typeString.equalsIgnoreCase("counter")) {
			type = Collector.Type.COUNTER;
		} else if (typeString.equalsIgnoreCase("info")) {
			type = Collector.Type.INFO;
		} else if (typeString.equalsIgnoreCase("stateset")) { // see also https://github.com/OpenObservability/OpenMetrics/blob/111feb202360b8650092f7de15a600e34a4ce0ba/specification/OpenMetrics.md#type
			type = Collector.Type.STATE_SET;
		} else if (typeString.equalsIgnoreCase("summary")) {
			type = Collector.Type.SUMMARY;
		} else if (typeString.equalsIgnoreCase("histogram")) {
			type = Collector.Type.HISTOGRAM;
		} else if (typeString.equalsIgnoreCase("untyped")) {
			type = Collector.Type.UNKNOWN;
		} else {
			log.warn("Unable to parse type from TYPE line: {}", line);
			return;
		}
		
		this.mapTypes.put(metricName, type);
	}

	private void parseHelpLine(String line) {
		Matcher m = PATTERN_PARSE_HELP.matcher(line);
		if (!m.matches()) {
			log.warn("HELP line could not be properly matched: {}", line);
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
	
	private void postProcessSpecialTypes() {
		/*
		 * Newer versions of the format automatically generate
		 * "_total" suffixes to COUNTER-typed metrics. Note that we will
		 * have to deal with both types of input data properly.
		 * The metric still internally is called with its base name only.
		 * 
		 * The same also applies to INFO-typed metrics, which may have
		 * a "_info" suffix at the end.
		 * 
		 * Note that this only applies to the metric's name - the sample's
		 * name are still kept *with* the suffix!
		 */
		
		
		// handling of COUNTER-typed metrics
		final List<String> keysToChangeCounter = this.mapTypes.entrySet().stream()
			.filter(e -> e.getValue() == Type.COUNTER)
			.map(Entry::getKey)
			.filter(k -> PATTERN_TOTAL_SUFFIX.matcher(k).matches())
			.toList();

		keysToChangeCounter.forEach(key -> {
			final String keyStripped = key.substring(0, key.length() - "_total".length());
			
			final String help = this.mapHelps.get(key);
			this.mapHelps.put(keyStripped, help);
			this.mapHelps.remove(key);
			
			Type type = this.mapTypes.get(key);
			this.mapTypes.put(keyStripped, type);
			this.mapTypes.remove(key);
			
			MetricFamilySamples mfsOld = this.mapMFS.get(key);
			this.mapMFS.put(keyStripped, new MetricFamilySamples(keyStripped, mfsOld.unit, mfsOld.type, mfsOld.help, mfsOld.samples));
			this.mapMFS.remove(key);
			// no need to dig into the sample's name in variable mfs
		});
		
		// handling of INFO-typed metrics
		final List<String> keysToChangeInfo = this.mapTypes.entrySet().stream()
			.filter(e -> e.getValue() == Type.INFO)
			.map(Entry::getKey)
			.filter(k -> PATTERN_INFO_SUFFIX.matcher(k).matches())
			.toList();

		keysToChangeInfo.forEach(key -> {
			final String keyStripped = key.substring(0, key.length() - "_info".length());
			
			final String help = this.mapHelps.get(key);
			this.mapHelps.put(keyStripped, help);
			this.mapHelps.remove(key);
			
			Type type = this.mapTypes.get(key);
			this.mapTypes.put(keyStripped, type);
			this.mapTypes.remove(key);
			
			MetricFamilySamples mfsOld = this.mapMFS.get(key);
			this.mapMFS.put(keyStripped, new MetricFamilySamples(keyStripped, mfsOld.unit, mfsOld.type, mfsOld.help, mfsOld.samples));
			this.mapMFS.remove(key);
			// no need to dig into the sample's name in variable mfs
		});
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
