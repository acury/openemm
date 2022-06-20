/*

    Copyright (C) 2022 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package com.agnitas.dao.impl;

import java.util.Date;

import org.agnitas.dao.impl.BaseDaoImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.agnitas.dao.ScripthelperEmailLogDao;

public class ScripthelperEmailLogDaoImpl extends BaseDaoImpl implements ScripthelperEmailLogDao {
	/** The logger. */
	private static final transient Logger logger = LogManager.getLogger(ScripthelperEmailLogDaoImpl.class);

	@Override
	public void writeLogEntry(int companyID, Integer mailingID, Integer formID, String fromAddress, String toAddress, String ccAddress, String subject) {
		update(logger, "INSERT INTO scripthelper_email_log_tbl (company_id, from_email, to_email, cc_email, subject, send_date, mailing_id, form_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
			companyID,
			fromAddress,
			toAddress,
			ccAddress,
			subject,
			new Date(),
			mailingID,
			formID);
	}
}
