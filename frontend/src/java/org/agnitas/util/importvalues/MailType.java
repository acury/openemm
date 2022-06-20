/*

    Copyright (C) 2022 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package org.agnitas.util.importvalues;

public enum MailType {
	TEXT("MailType.0", 0),
	HTML("MailType.1", 1),
	HTML_OFFLINE("MailType.2", 2),
	
	/**
	 * Historical Mailtype, used in old mailingaccount data. Use HTML_OFFLINE for new data instead
	 */
	@Deprecated
	HTML_OFFLINE_HST("recipient.mailingtype.htmloffline", 3),
	
	/**
	 * Historical Mailtype
	 * Text-, HTML-, MobileHTML- and Offline-HTML-Mail
	 */
	@Deprecated
	MHTML_OFFLINE_HST("recipient.mailingtype.mhtml_and_offline", 4);

	private String messageKey;
	
	private int storageInt;

	public String getMessageKey() {
		return messageKey;
	}

	public int getIntValue() {
		return storageInt;
	}

	private MailType(String messageKey, int storageInt) {
		this.messageKey = messageKey;
		this.storageInt = storageInt;
	}

	public static MailType getMailTypeByMessageKey(String messageKey) throws Exception {
		for (MailType item : MailType.values()) {
			if (item.messageKey.equalsIgnoreCase(messageKey)) {
				return item;
			}
		}
		
		throw new Exception(String.format("Invalid messageKey value '%s' for %s", messageKey, MailType.class.getSimpleName()));
	}

	public static MailType getFromString(String valueString) throws Exception {
		for (MailType item : MailType.values()) {
			if (item.name().equalsIgnoreCase(valueString)) {
				return item;
			}
		}
		
		throw new Exception(String.format("Invalid string value '%s' for %s", valueString, MailType.class.getSimpleName()));
	}

	public static MailType getFromInt(int intValue) throws Exception {
		for (MailType item : MailType.values()) {
			if (item.getIntValue() == intValue) {
				return item;
			}
		}
		
		throw new Exception(String.format("Invalid int value %d for %s", intValue, MailType.class.getSimpleName()));
	}
	
	public static MailType getMailTypeByDefaultMapping(String value) {
		switch (value.toLowerCase().trim()) {
			case "txt":
			case "text":
				return TEXT;
			case "html":
				return HTML;
			case "offline":
			case "htmloffline":
				return HTML_OFFLINE;
			default:
				return HTML;
		}
	}
}
