/**
 * Copyright (c) 2010-2013, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.wmr100;

import org.openhab.core.binding.BindingProvider;

/**
 * @author JeromeCourat
 * @since 1.4.0
 */
public interface WMR100BindingProvider extends BindingProvider {
	
	int getId(String itemName);
	
	String getDataName(String itemName);
	
	String getConfigString(String itemName);
	
}