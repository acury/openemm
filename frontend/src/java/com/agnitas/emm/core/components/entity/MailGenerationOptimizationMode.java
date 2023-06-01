/*

    Copyright (C) 2022 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package com.agnitas.emm.core.components.entity;

public enum MailGenerationOptimizationMode {

    NONE(0, null),
    DAY(1, "day"),
    NEXT_24h(2, "24h");

    private final int code;
    private final String maildropCode;

    MailGenerationOptimizationMode(final int code, final String maildropCode) {
        this.code = code;
        this.maildropCode = maildropCode;
    }

    public static MailGenerationOptimizationMode fromCode(final int code) {
        for (MailGenerationOptimizationMode mode : values()) {
            if (mode.code == code) {
                return mode;
            }
        }

        return null;
    }

    public int getCode() {
        return code;
    }

    public String getMaildropCode() {
        return maildropCode;
    }
}