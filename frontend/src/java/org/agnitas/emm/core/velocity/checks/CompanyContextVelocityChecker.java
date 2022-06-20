/*

    Copyright (C) 2022 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package org.agnitas.emm.core.velocity.checks;

import java.lang.reflect.Method;

import org.agnitas.emm.core.velocity.CheckType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implementation of {@link VelocityChecker} performing a check on company IDs
 * used in scripts.
 */
@Deprecated // After completion of EMM-8360, this class can be removed without replacement
public class CompanyContextVelocityChecker implements VelocityChecker {
	/** The logger. */
	private static final transient Logger logger = LogManager.getLogger(CompanyContextVelocityChecker.class);

	@Override
	public void performCheck(Method method, Object argument, CheckType checkType, int contextCompanyId) throws VelocityCheckerException {
		try {
			Integer companyValue = (Integer) argument;

			if (!isValidCompanyId(companyValue, contextCompanyId)) {
				throw new CompanyContextViolationException(companyValue, contextCompanyId);
			}
		} catch (ClassCastException e) {
			logger.info("Cannot perform company context check", e);
		}
	}

	/**
	 * Checks, if the company ID used in the script is valid.
	 * 
	 * @param scriptCompanyId
	 *            company ID used in script
	 * @param companyContext
	 *            ID of company executing the script
	 * @return
	 */
	protected boolean isValidCompanyId(int scriptCompanyId, int companyContext) {
		return scriptCompanyId == companyContext;
	}
}
