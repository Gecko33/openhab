/**
 * Copyright (c) 2010-2013, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.wmr100.internal;

import java.io.File;
import java.io.IOException;
import java.util.Dictionary;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.openhab.binding.wmr100.WMR100BindingProvider;
import org.openhab.binding.wmr100.utils.WxLogger;
import org.openhab.core.binding.AbstractActiveBinding;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codeminders.hidapi.HIDDevice;
import com.codeminders.hidapi.HIDManager;
	

/**
 * Implement this class if you are going create an actively polling service
 * like querying a Website/Device.
 * 
 * @author JeromeCourat
 * @since 1.8.0
 */
public class WMR100Binding extends AbstractActiveBinding<WMR100BindingProvider> implements ManagedService {

	private static final Logger logger = LoggerFactory.getLogger(WMR100Binding.class);
	
	/** 
	 * the refresh interval which is used to poll values from the WMR100
	 * server (optional, defaults to 10000ms)
	 */
	private long refreshInterval = 10;
	
	private int vendorId;
	
	private int productId;
	
	private int retryCounter;
	
	private int retryDelay = DEFAULT_RETRY_DELAY;
	
	private int retryCount = DEFAULT_RETRY_COUNT;
	
	private boolean logEnabled;
	
	private File logDirectory;
	
	/** Weather station USB default vendor identifier */
	private static final int DEFAULT_STATION_VENDOR = 0x0FDE;

	/** Weather station USB default product identifier */
	private static final int DEFAULT_STATION_PRODUCT = 0xCA01;
	
	private static final int DEFAULT_RETRY_DELAY = 1000;
	
	private static final int DEFAULT_RETRY_COUNT = 120;
	
	/** HID Device, aka. WMR100 handler */
	protected HIDDevice hidDevice;
	
	protected WxLogger wxLogger;
	
	
	public WMR100Binding() {
		wxLogger = new WxLogger();
	}
	
	/**
     * Called by the SCR to activate the component with its configuration read from CAS
     * 
     * @param bundleContext BundleContext of the Bundle that defines this component
     * @param configuration Configuration properties for this component obtained from the ConfigAdmin service
     */
    public void activate(final BundleContext bundleContext, final Map<String, Object> configuration) {
    	activate();
    }
		
	public void activate() {
		bindHid();
	}
	
	public void bindHid() {
		logger.debug("Binding to HID device...");
		HIDManager hidManager = null;
		try {
			hidManager = HIDManager.getInstance();
		} catch (IOException e) {
			logger.error("Could not get HIDManager instance", e);
			return;
		}
		while (hidDevice == null && retryCounter < retryCount) {
			try {
				hidDevice = hidManager.openById(vendorId, productId, null);
				if (hidDevice != null) {
					logger.info("HID Device found:" + hidDevice.getManufacturerString());
					wxLogger.setDevice(hidDevice);
					retryCounter = 0;
					return; // we're finished with startup!
				} else {
					logger.error("No HID Device found!!");
				}
				
			} catch (IOException e) {
				logger.error("Could not open HIDDevice.");
				return;
			}
			// pause for <retryDelay> milliseconds.
			try {
				Thread.sleep(retryDelay);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			retryCounter++;
		}
		if (hidDevice == null && retryCounter >= retryCount) {
			logger.error("Could not open HID Device.");
		}
	}
	
	public void deactivate() {
		unbindHid();
	}
	
	/**
     * Called by the SCR to deactivate the component when either the configuration is removed or
     * mandatory references are no longer satisfied or the component has simply been stopped.
     * @param reason Reason code for the deactivation:<br>
     * <ul>
     * <li> 0 – Unspecified
     * <li> 1 – The component was disabled
     * <li> 2 – A reference became unsatisfied
     * <li> 3 – A configuration was changed
     * <li> 4 – A configuration was deleted
     * <li> 5 – The component was disposed
     * <li> 6 – The bundle was stopped
     * </ul>
     */
    public void deactivate(final int reason) {
//        this.bundleContext = null;
        // deallocate resources here that are no longer needed and 
        // should be reset when activating this binding again
    	String strReason = "unknown";
    	switch(reason) {
    	case 0: 
    		strReason = "unspecified";
    		break;
    	case 1: 
    		strReason = "The component was disabled";
    		break;
    	case 2: 
    		strReason = "A reference became unsatisfied";
    		break;
    	case 3:
    		strReason = "A configuration was changed";
    		break;
    	case 4:
    		strReason = "A configuration was deleted";
    		break;
    	case 5:
    		strReason = "The component was disposed";
    		break;
    	case 6:
    		strReason = "The bundle was stopped";
    		break;
    	}
    	logger.info("Deactivate with reason: " + strReason);
    	deactivate();
    }
	
	public void unbindHid() {
		if (hidDevice != null) {
			try {
				hidDevice.close();
				logger.debug("HID Device closed.");
			} catch (IOException e) {
				logger.error("Could not properly close HID Device", e);
			}
			wxLogger.setDevice(null);
		}
	}
	
	/**
	 * @{inheritDoc}
	 */
	@Override
	protected long getRefreshInterval() {
		return refreshInterval;
	}

	/**
	 * @{inheritDoc}
	 */
	@Override
	protected String getName() {
		return "WMR100 Refresh Service";
	}
	
	/**
	 * @{inheritDoc}
	 */
	@Override
	protected void execute() {
		logger.debug("WMR100Binding#execute()");
		try {
			if (hidDevice == null) {
				logger.warn("HID Device not yet open.");
				return;
			}
			wxLogger.singleStationRead();
			processData(wxLogger.getData());
			wxLogger.clearData();
		} catch (IOException e) {
			logger.warn("WxLogger could not properly read data.", e);
		}
	}
	
	private boolean isReferencedItem(String itemName) {
		if (itemName == null) {
			return false;
		}
		for (WMR100BindingProvider provider : providers) {
			for (String aName : provider.getItemNames()) {
				if (itemName.equals(provider.getConfigString(aName))) {
					return true;
				}
			}
		}
		return false;
	}
	
	protected State convertToState(Object value) {
		if (value instanceof Double) {
			return new DecimalType((Double) value);
		} else if (value instanceof Long) {
			return new DecimalType((Long) value);
		} else if (value instanceof Integer) {
			return new DecimalType((Integer) value);
		} else if (value instanceof Float) {
			return new DecimalType(Double.parseDouble(value.toString()));
		} else {
			if (value != null) {
				logger.warn(String.format("Cannot infer openHAB type from %s ", value.getClass().getSimpleName()));
			} else {
				logger.warn("Cannot generate openHAB type from null value");
			}
			return null;
		}
	}
	
	private String findItemNameForDataKey(String dataKey) {
		if (dataKey == null) {
			return null;
		}
		for (WMR100BindingProvider provider : providers) {
			for (String itemName : provider.getItemNames()) {
				if (dataKey.equals(provider.getConfigString(itemName))) {
					return itemName;
				}
			}
		}
		return null;
	}
	
	public void processData(Map<String, Object> data) {
		Set<String> keys = data.keySet();
		logger.debug(keys.size() + " data entries extracted from frame");
		
		for (String aKey : keys) {
			if (isReferencedItem(aKey)) {
				State newState = convertToState(data.get(aKey));
				String itemName = findItemNameForDataKey(aKey);
				if (newState != null && itemName != null) {
					eventPublisher.postUpdate(itemName, newState);
				}
			}
		}
		
	}

	/**
	 * @{inheritDoc}
	 */
	@Override
	protected void internalReceiveCommand(String itemName, Command command) {
		// the code being executed when a command was sent on the openHAB
		// event bus goes here. This method is only called if one of the 
		// BindingProviders provide a binding for the given 'itemName'.
		logger.debug("internalReceiveCommand() is called!");
	}
	
	/**
	 * @{inheritDoc}
	 */
	@Override
	protected void internalReceiveUpdate(String itemName, State newState) {
		// the code being executed when a state was sent on the openHAB
		// event bus goes here. This method is only called if one of the 
		// BindingProviders provide a binding for the given 'itemName'.
		logger.debug("internalReceiveCommand() is called!");
	}
		
	/**
	 * @{inheritDoc}
	 */
	@SuppressWarnings("rawtypes")
	public void updated(Dictionary config) throws ConfigurationException {
		setProperlyConfigured(false);
		logger.debug("updated!!");
		if (config != null) {
			logger.debug("updated with non-null config!");
			// to override the default refresh interval one has to add a 
			// parameter to openhab.cfg like <bindingName>:refresh=<intervalInMs>
			String refreshIntervalString = (String) config.get("refresh");
			if (StringUtils.isNotBlank(refreshIntervalString)) {
				refreshInterval = Long.parseLong(refreshIntervalString);
			}
			String strVendorId = (String) config.get("vendor");
			if (StringUtils.isNotBlank(strVendorId)) {
				logger.info("Vendor ID: " + strVendorId);
				try {
					vendorId = Integer.parseInt(strVendorId.replace("0x",""), 16);
				} catch(Exception e) {
					logger.error("Could not parse properly vendor Id: " + strVendorId);
					return;
				}
			}
			
			String strProductId = (String) config.get("product");
			if (StringUtils.isNotBlank(strProductId)) {
				logger.info("Product ID: " + strProductId);
				try {
					productId = Integer.parseInt(strProductId.replace("0x", ""), 16);
				} catch(Exception e) {
					logger.error("Could not parse properly product Id: " + strProductId);
					return;
				}
			}
			
			String strRetryDelay = (String) config.get("retryDelay");
			if (StringUtils.isNotBlank(strRetryDelay)) {
				try {
					retryDelay = Integer.parseInt(strRetryDelay);
				} catch(Exception e) {
					logger.error("Invalid parameter value for {}: {}", "retrydelay", strRetryDelay);
					return;
				}
			}
			
			String strRetryCount = (String) config.get("retrycount");
			if (StringUtils.isNotBlank(strRetryDelay)) {
				try {
					retryCount = Integer.parseInt(strRetryCount);
				} catch(Exception e) {
					logger.error("Invalid parameter value for {}: {}", "retrycount", strRetryCount);
					return;
				}
			}
			
			String strEnableLog = (String) config.get("enableLog");
			if (StringUtils.isNotBlank(strEnableLog)) {
				logEnabled = Boolean.parseBoolean(strEnableLog);
				wxLogger.setLogEnabled(logEnabled);
			}
			
			String strLogDir = (String) config.get("logDirectory");
			if (StringUtils.isNotBlank(strLogDir)) {
				File logDir = new File(strLogDir);
				if (logDir.exists() && logDir.isDirectory()) {
					logDirectory = logDir;
					wxLogger.setLogDirectory(logDirectory);
				} else {
					logger.warn("Log directory '{}' seems not to be a valid directory. Does it exist?");
					logEnabled = false;
				}
			}
			
			if (vendorId != 0 && productId != 0 && refreshInterval != -1) {
				logger.debug("Configuration is complete. Ready to start.");
				if (hidDevice == null) {
					bindHid();
					wxLogger.initialize();
				}
				setProperlyConfigured(true);
			} else {
				logger.debug("Configuration not complete yet. Won't start.");
			}
		}
	}
	
}
