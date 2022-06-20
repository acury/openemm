/*

    Copyright (C) 2022 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package com.agnitas.emm.core.messages.beans;

import java.util.List;

public class MessagesDto {
    private List<MessageDto> successMessages;
    private List<MessageDto> warningMessages;
    private List<MessageDto> errorMessages;

    public MessagesDto(List<MessageDto> successMessages, List<MessageDto> warningMessages, List<MessageDto> errorMessages) {
        this.successMessages = successMessages;
        this.warningMessages = warningMessages;
        this.errorMessages = errorMessages;
    }

    public List<MessageDto> getSuccessMessages() {
        return successMessages;
    }

    public void setSuccessMessages(List<MessageDto> successMessages) {
        this.successMessages = successMessages;
    }

    public List<MessageDto> getWarningMessages() {
        return warningMessages;
    }

    public void setWarningMessages(List<MessageDto> warningMessages) {
        this.warningMessages = warningMessages;
    }

    public List<MessageDto> getErrorMessages() {
        return errorMessages;
    }

    public void setErrorMessages(List<MessageDto> errorMessages) {
        this.errorMessages = errorMessages;
    }
}
