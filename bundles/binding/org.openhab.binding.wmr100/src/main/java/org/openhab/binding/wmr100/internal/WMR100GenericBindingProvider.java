/**
 * Copyright (c) 2010-2013, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.wmr100.internal;

import org.openhab.binding.wmr100.WMR100BindingProvider;
import org.openhab.core.binding.BindingConfig;
import org.openhab.core.items.Item;
import org.openhab.model.item.binding.AbstractGenericBindingProvider;
import org.openhab.model.item.binding.BindingConfigParseException;


/**
 * This class is responsible for parsing the binding configuration.
 * 
 * @author JeromeCourat
 * @since 1.4.0
 */
public class WMR100GenericBindingProvider extends AbstractGenericBindingProvider implements WMR100BindingProvider {
	
	private static String[] dataNamesWithoutId = { "windDirection", "windGust", "windAverage", "pressureAbsolute" }; // more to come
	
	private static String[] dataNamesWithId = { "temperature", "humidity", "dewpoint" }; // more to come

	/**
	 * {@inheritDoc}
	 */
	public String getBindingType() {
		return "wmr100";
	}

	/**
	 * @{inheritDoc}
	 */
	public void validateItemType(Item item, String bindingConfig) throws BindingConfigParseException {
		//if (!(item instanceof SwitchItem || item instanceof DimmerItem)) {
		//	throw new BindingConfigParseException("item '" + item.getName()
		//			+ "' is of type '" + item.getClass().getSimpleName()
		//			+ "', only Switch- and DimmerItems are allowed - please check your *.items configuration");
		//}
	}
	
	public int getId(String itemName) {
		WMR100BindingConfig config = (WMR100BindingConfig) bindingConfigs.get(itemName);
		return config != null ? config.sensorId : 0;
	}
	
	public String getDataName(String itemName) {
		WMR100BindingConfig config = (WMR100BindingConfig) bindingConfigs.get(itemName);
		return config !=  null ? config.dataName : null;
	}
	
	public String getConfigString(String itemName) {
		return getDataName(itemName) +  ":" + getId(itemName);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void processBindingConfiguration(String context, Item item, String bindingConfig) throws BindingConfigParseException {
		super.processBindingConfiguration(context, item, bindingConfig);
		
		String[] configParts = bindingConfig.trim().split(":");
		WMR100BindingConfig config = new WMR100BindingConfig();
		if (configParts.length == 2) {
			config.dataName = configParts[0];
			config.sensorId = Integer.parseInt(configParts[1]);
		} else {
			throw new BindingConfigParseException("WMR100 binding configuration must contain exactly two parts");
		}
		addBindingConfig(item, config);		
	}
	
	
	/**
	 * This is an internal data structure to store information from the binding
	 * config strings and use it to answer the requests to the WMR100
	 * binding provider.
	 * Example configuration: { wmr100="pressureAbsolute" } or { wmr100="temperature:2" }
	 */
	class WMR100BindingConfig implements BindingConfig {
		// put member fields here which holds the parsed values
		public String dataName;
		public int sensorId;
	}
	
	
}
