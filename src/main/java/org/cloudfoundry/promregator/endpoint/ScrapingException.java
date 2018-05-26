package org.cloudfoundry.promregator.endpoint;

public class ScrapingException extends Exception {

	private static final long serialVersionUID = -1373107601840419517L;

	public ScrapingException(String arg0, Throwable arg1) {
		super(arg0, arg1);
	}

	public ScrapingException(String arg0) {
		super(arg0);
	}

}
