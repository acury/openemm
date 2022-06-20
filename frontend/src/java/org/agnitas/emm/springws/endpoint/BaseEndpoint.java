/*

    Copyright (C) 2022 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package org.agnitas.emm.springws.endpoint;

import jakarta.annotation.Resource;

import org.agnitas.service.UserActivityLogService;
import org.springframework.context.annotation.Lazy;

public abstract class BaseEndpoint {

    @Resource
    @Lazy
	protected org.agnitas.emm.springws.jaxb.ObjectFactory objectFactory;

    @Resource
    @Lazy
	protected org.agnitas.emm.springws.jaxb.ObjectFactory comObjectFactory;
	
    @Resource
    @Lazy
	protected UserActivityLogService userActivityLogService;
    
    public final void setObjectFactory(final org.agnitas.emm.springws.jaxb.ObjectFactory objectFactory) {
    	this.objectFactory = objectFactory;
    }
    
}
