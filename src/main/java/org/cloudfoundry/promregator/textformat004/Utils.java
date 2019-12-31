package org.cloudfoundry.promregator.textformat004;

public class Utils {

	protected static String unescapeToken(String s) {
		if (s == null)
			return null;
		
		String sTemp = s.replace("\\\\", "\\");
		sTemp = sTemp.replace("\\\"", "\"");
		sTemp = sTemp.replace("\\n", "\n");
		
		return sTemp;
	}

}
