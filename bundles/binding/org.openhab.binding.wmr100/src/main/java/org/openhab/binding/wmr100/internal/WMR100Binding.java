/**
 * Copyright (c) 2010-2013, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.wmr100.internal;

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
 * @since 1.4.0
 */
public class WMR100Binding extends AbstractActiveBinding<WMR100BindingProvider> implements ManagedService, WxLogger.DataListener {

	private static final Logger logger = LoggerFactory.getLogger(WMR100Binding.class);
	
	private boolean stationInitialized = false;
	
	private boolean running = false;

	
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
	
	/** Weather station USB default vendor identifier */
	private static final int DEFAULT_STATION_VENDOR = 0x0FDE;

	/** Weather station USB default product identifier */
	private static final int DEFAULT_STATION_PRODUCT = 0xCA01;
	
	private static final int DEFAULT_RETRY_DELAY = 1000;
	
	private static final int DEFAULT_RETRY_COUNT = 120;
	
	/** HID Device, aka. WMR100 handler */
	protected HIDDevice hidDevice;
	
	
	public WMR100Binding() {
	}
		
	
	public void activate() {
		HIDManager hidManager = null;
		try {
			hidManager = HIDManager.getInstance();
		} catch (IOException e) {
			logger.error("Could not get HIDManager instance", e);
			return;
		}
		while (hidDevice == null) {
			try {
				hidDevice = hidManager.openById(DEFAULT_STATION_VENDOR, DEFAULT_STATION_PRODUCT, null);
				if (hidDevice != null) {
					logger.info("HID Device found:" + hidDevice.getManufacturerString());
					WxLogger.setDevice(hidDevice);
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
		
	}
	
	public void deactivate() {
		// deallocate resources here that are no longer needed and 
		// should be reset when activating this binding again
		if (hidDevice != null) {
			try {
				hidDevice.close();
				logger.debug("HID Device closed.");
			} catch (IOException e) {
				logger.error("Could not properly close HID Device", e);
			}
			WxLogger.setDevice(null);
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
		if (running) return;
		try {
			if (hidDevice == null) {
				logger.warn("HID Device not yet open.");
				return;
			}
			running = true;
			WxLogger.addDataListener(this);
			WxLogger.initialise();
			WxLogger.stationRead();
		} catch (IOException e) {
			logger.warn("WxLogger could not properly read data.", e);
			running = false;
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
	public void updated(Dictionary<String, ?> config) throws ConfigurationException {
		if (config != null) {
			
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
			
			
			// read further config parameters here ...
			setProperlyConfigured(true);
		}
	}
	

}
