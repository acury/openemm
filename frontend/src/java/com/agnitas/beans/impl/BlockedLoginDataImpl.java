/*

    Copyright (C) 2022 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package com.agnitas.beans.impl;

import com.agnitas.beans.BlockedLoginData;

public class BlockedLoginDataImpl implements BlockedLoginData {

	private String ipAddress;
	
	private String username;
	
	public void setIpAddress( String ipAddress) {
		this.ipAddress = ipAddress;
	}
	
	public void setUsername( String username) {
		this.username = username;
	}
	
	@Override
	public String getIpAddress() {
		return this.ipAddress;
	}

	@Override
	public String getUsername() {
		return this.username;
	}
	
}
