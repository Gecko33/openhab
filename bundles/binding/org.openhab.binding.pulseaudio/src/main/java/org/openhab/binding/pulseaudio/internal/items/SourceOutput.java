/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.pulseaudio.internal.items;

/**
 * A SourceOutput is the audio stream which is produced by a (@link Source}
 * 
 * @author Tobias Bräutigam
 * @since 1.2.0
 */
public class SourceOutput extends AbstractAudioDeviceConfig {
	
	private Source source;

	public SourceOutput(int id, String name, Module module) {
		super(id, name, module);
	}

	public Source getSource() {
		return source;
	}

	public void setSource(Source source) {
		this.source = source;
	}

}
