package org.cloudfoundry.promregator.lifecycle;

import org.apache.log4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.support.AbstractApplicationContext;

public class RestartHandler implements ApplicationContextAware {
	private static final Logger log = Logger.getLogger(RestartHandler.class);
	
	public static boolean restartFlag = true;
	public static Object restartLockObject = new Object();

	private ApplicationContext applicationContext;
	
	public void triggerApplicationRestart() {
		log.info("Triggering restart");
		restartFlag = true;
		synchronized(restartLockObject) {
			restartLockObject.notify();
		}
		((AbstractApplicationContext) applicationContext).close();
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}
	
}
