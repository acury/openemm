/*

    Copyright (C) 2022 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package com.agnitas.emm.core.departments.dao;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.jdbc.core.RowMapper;

import com.agnitas.emm.core.departments.beans.Department;

/**
 * Rowmapper for table <i>department_tbl</i>.
 */
public final class DepartmentRowMapper implements RowMapper<Department> {

	@Override
	public final Department mapRow(final ResultSet rs, final int row) throws SQLException {
		final int id = rs.getInt("department_id");
		final String slug = rs.getString("slug");
		
		final boolean cid0Allowed = rs.getBoolean("cid_0_allowed");
		final boolean loginWithoutPermissionAllowed = rs.getBoolean("no_permission_required");
		
		return new Department(id, slug, cid0Allowed, loginWithoutPermissionAllowed);
	}

}
