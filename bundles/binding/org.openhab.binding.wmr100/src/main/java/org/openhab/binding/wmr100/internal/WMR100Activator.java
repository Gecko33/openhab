/**
 * Copyright (c) 2010-2013, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.wmr100.internal;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Extension of the default OSGi bundle activator
 * 
 * @author JeromeCourat
 * @since 1.4.0
 */
public final class WMR100Activator implements BundleActivator {

	private static Logger logger = LoggerFactory.getLogger(WMR100Activator.class); 
	
	private static BundleContext context;
	
	/**
	 * Called whenever the OSGi framework starts our bundle
	 */
	public void start(BundleContext bc) throws Exception {
		context = bc;
		logger.debug("WMR100 binding has been started.");
		// use system properties: osgi.os and osg.arch
		String osgiArch = System.getProperty("osgi.arch");
		String osgiOs = System.getProperty("osgi.os");
		String libName = null;
		if ("arm".equalsIgnoreCase(osgiArch)) {
			// then we probably are running on Raspberry Pi!
			libName = "hidapi-jni-arm";
		} else if ("x86".equals(osgiArch)) {
			// 32bits architecture
			libName = "hidapi-jni-32";
		} else {
			// 64bits architecture
			libName = "hidapi-jni-64";
		}
		System.loadLibrary(libName);
		logger.debug("HID Library loaded.");
	}

	/**
	 * Called whenever the OSGi framework stops our bundle
	 */
	public void stop(BundleContext bc) throws Exception {
		context = null;
		logger.debug("WMR100 binding has been stopped.");
	}
	
	/**
	 * Returns the bundle context of this bundle
	 * @return the bundle context
	 */
	public static BundleContext getContext() {
		return context;
	}
	
}
