/*

    Copyright (C) 2022 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package org.agnitas.beans.impl;

public enum CompanyStatus {
	ACTIVE("active"),
	LOCKED("locked"),
	TODELETE("todelete"),
	DELETION_IN_PROGRESS("deletion in progress"),
	DELETED("deleted"),
	TORESET("toreset");

	public final String dbValue;
	
	CompanyStatus(String dbValue) {
		this.dbValue = dbValue;
	}
	
	public String getDbValue() {
		return dbValue;
	}
	
	public static CompanyStatus getCompanyStatus(String dbValue) throws Exception {
		if (dbValue == null) {
			throw new Exception("Invalid CompanyStatus dbValue: null");
		} else {
			for (CompanyStatus status : values()) {
				if (status.getDbValue().replace(" ", "").replace("_", "").replace("-", "").equalsIgnoreCase(dbValue.replace(" ", "").replace("_", "").replace("-", ""))) {
					return status;
				}
			}
			throw new Exception("Invalid CompanyStatus dbValue: " + dbValue);
		}
	}
}
