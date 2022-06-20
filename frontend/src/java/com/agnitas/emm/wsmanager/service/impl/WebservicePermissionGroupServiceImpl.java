/*

    Copyright (C) 2022 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package com.agnitas.emm.wsmanager.service.impl;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Required;

import com.agnitas.emm.wsmanager.bean.WebservicePermissionGroups;
import com.agnitas.emm.wsmanager.dao.WebservicePermissionGroupDao;
import com.agnitas.emm.wsmanager.service.WebservicePermissionGroupService;

public final class WebservicePermissionGroupServiceImpl implements WebservicePermissionGroupService {
	
	private WebservicePermissionGroupDao permissionGroupDao;

	@Override
	public final WebservicePermissionGroups listAllPermissionGroups() {
		return new WebservicePermissionGroups(this.permissionGroupDao.listAllPermissionGroups());
	}

	@Required
	public final void setWebservicePermissionGroupDao(final WebservicePermissionGroupDao dao) {
		this.permissionGroupDao = Objects.requireNonNull(dao, "WebservicePermissionGroupDao is null");
	}
}
