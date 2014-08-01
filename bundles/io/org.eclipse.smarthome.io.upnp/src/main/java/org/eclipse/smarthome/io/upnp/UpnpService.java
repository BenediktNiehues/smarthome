/**
 * Copyright (c) 2014 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.smarthome.io.upnp;



/**
 * 
 * 
 * @author Andre Fuechsel - Initial contribution and API
 */
public interface UpnpService {
	
    void addUpnpListener(UpnpFilter upnpFilter, UpnpListener upnpListener);

    void removeUpnpListener(UpnpListener upnpListener);
    
    void triggerSearch();
    
}
