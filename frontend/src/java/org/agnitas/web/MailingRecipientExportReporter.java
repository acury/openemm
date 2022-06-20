/*

    Copyright (C) 2022 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package org.agnitas.web;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;

import org.agnitas.emm.core.commons.util.ConfigService;
import org.agnitas.emm.core.commons.util.ConfigValue;
import org.agnitas.service.MailingRecipientExportWorker;
import org.agnitas.util.AgnUtils;
import org.agnitas.util.DateUtilities;
import org.agnitas.util.importvalues.Separator;
import org.agnitas.util.importvalues.TextRecognitionChar;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Required;

import com.agnitas.beans.ComAdmin;
import com.agnitas.beans.Company;
import com.agnitas.beans.Mailing;
import com.agnitas.dao.ComCompanyDao;
import com.agnitas.dao.ComMailingDao;
import com.agnitas.emm.core.JavaMailService;
import com.agnitas.emm.core.admin.service.AdminService;
import com.agnitas.emm.core.recipientsreport.service.RecipientsReportService;
import com.agnitas.messages.I18nString;

public class MailingRecipientExportReporter {
	
	private JavaMailService javaMailService;
	
	private ComCompanyDao companyDao;
	
	private ComMailingDao mailingDao;

	private AdminService adminService;

	private RecipientsReportService recipientsReportService;
	
	private ConfigService configService;

	@Required
	public void setJavaMailService(JavaMailService javaMailService) {
		this.javaMailService = javaMailService;
	}

	@Required
	public void setCompanyDao(ComCompanyDao companyDao) {
		this.companyDao = companyDao;
	}

	@Required
	public void setMailingDao(ComMailingDao mailingDao) {
		this.mailingDao = mailingDao;
	}
	
	@Required
	public void setAdminService(final AdminService service) {
		this.adminService = Objects.requireNonNull(service, "Admin service is null");
	}

	@Required
	public void setRecipientsReportService(RecipientsReportService recipientsReportService) {
		this.recipientsReportService = recipientsReportService;
	}

	@Required
	public void setConfigService(ConfigService configService) {
		this.configService = configService;
	}
	
	/**
	 * Send a report email about this export to the executing GUI-admin or the creator of the autoexport
	 * 
	 * @param exportWorker
	 * @param admin
	 * @throws Exception
	 */
	public void sendExportReportMail(MailingRecipientExportWorker exportWorker, ComAdmin admin) throws Exception {
		Set<String> emailRecipients = new HashSet<>();
		
		String additionalContent = "";
		Company comp = companyDao.getCompany(admin.getCompanyID());
		if (comp.getExportNotifyAdmin() > 0) {
			final ComAdmin notifyAdmin = adminService.getAdmin(comp.getExportNotifyAdmin(), admin.getCompanyID());

			if (notifyAdmin != null && StringUtils.isNotBlank(notifyAdmin.getEmail())) {
				emailRecipients.add(AgnUtils.normalizeEmail(notifyAdmin.getEmail()));
			} else {
				emailRecipients.add(AgnUtils.normalizeEmail(configService.getValue(ConfigValue.Mailaddress_Error)));
				additionalContent = "Admin User or Email for this export was not available: CompanyID: " + admin.getCompanyID() + " / AdminID: " + comp.getExportNotifyAdmin() + " \n\n";
			}
		}
		
		if (StringUtils.isNotBlank(configService.getValue(ConfigValue.ExportAlwaysInformEmail, admin.getCompanyID()))) {
			if (StringUtils.isNotBlank(configService.getValue(ConfigValue.ExportAlwaysInformEmail, admin.getCompanyID()))) {
				for (String email : AgnUtils.splitAndTrimList(configService.getValue(ConfigValue.ExportAlwaysInformEmail, admin.getCompanyID()))) {
					emailRecipients.add(AgnUtils.normalizeEmail(email));
				}
			}
		}

		if (exportWorker.getAutoExport() != null && StringUtils.isNotBlank(exportWorker.getAutoExport().getEmailForReport())) {
			for (String email : AgnUtils.splitAndTrimList(exportWorker.getAutoExport().getEmailForReport())) {
				emailRecipients.add(AgnUtils.normalizeEmail(email));
			}
		}

		if (!emailRecipients.isEmpty()) {
			Locale locale = admin.getLocale();
			Company company = companyDao.getCompany(exportWorker.getCompanyID());
			
			String subject = I18nString.getLocaleString("ResultMsg", locale) + " \"" + I18nString.getLocaleString("Mailing", locale) + "\" (" + I18nString.getLocaleString("Company", locale) + ": " + company.getShortname() + ")";
			String bodyHtml = generateLocalizedExportHtmlReport(exportWorker, admin) + "\n" + additionalContent;
			String bodyText = generateLocalizedExportTextReport(exportWorker, locale) + "\n" + additionalContent;
			
			javaMailService.sendEmail(exportWorker.getCompanyID(), StringUtils.join(emailRecipients, ", "), subject, bodyText, bodyHtml);
		}
	}

	private String generateLocalizedExportTextReport(MailingRecipientExportWorker exportWorker, Locale locale) throws Exception {
		String reportContent = I18nString.getLocaleString("ResultMsg", locale) + " \"" + I18nString.getLocaleString("Mailing", locale) + "\":\n\n";

		reportContent += I18nString.getLocaleString("decode.licenseID", locale) + ": " + configService.getValue(ConfigValue.System_Licence) + "\n";
		
		Company company = companyDao.getCompany(exportWorker.getCompanyID());
		reportContent += I18nString.getLocaleString("Company", locale) + ": " + (company == null ? "Unknown" : company.getShortname() + " (ID: " + company.getId() + ")") + "\n";
		
		Mailing mailing = mailingDao.getMailing(exportWorker.getMailingID(), exportWorker.getCompanyID());
		reportContent += I18nString.getLocaleString("Mailing", locale) + ": " + (mailing == null ? "Unknown" : mailing.getShortname() + " (ID: " + mailing.getId() + ")") + "\n";
		
		String filterTypeText = "Unknown";
		if (exportWorker.getFilterType() == MailingRecipientExportWorker.MAILING_RECIPIENTS_ALL) {
			filterTypeText = I18nString.getLocaleString("default.All", locale);
		} else if (exportWorker.getFilterType() == MailingRecipientExportWorker.MAILING_RECIPIENTS_OPENED) {
			filterTypeText = I18nString.getLocaleString("mailing.recipients.mailing_opened", locale);
		} else if (exportWorker.getFilterType() == MailingRecipientExportWorker.MAILING_RECIPIENTS_CLICKED) {
			filterTypeText = I18nString.getLocaleString("mailing.recipients.mailing_clicked", locale);
		} else if (exportWorker.getFilterType() == MailingRecipientExportWorker.MAILING_RECIPIENTS_BOUNCED) {
			filterTypeText = I18nString.getLocaleString("mailing.recipients.mailing_bounced", locale);
		} else if (exportWorker.getFilterType() == MailingRecipientExportWorker.MAILING_RECIPIENTS_UNSUBSCRIBED) {
			filterTypeText = I18nString.getLocaleString("mailing.recipients.mailing_unsubscribed", locale);
		}
		reportContent += I18nString.getLocaleString("report.mailing.filter", locale) + ": " + filterTypeText + "\n";
		
		if (exportWorker.getAutoExport() != null) {
			reportContent += "AutoExport: " + exportWorker.getAutoExport().getShortname() + " (ID: " + exportWorker.getAutoExport().getAutoExportId() + ")" + "\n";
			reportContent += I18nString.getLocaleString("error.import.profile.email", locale) + ": " + (StringUtils.isBlank(exportWorker.getAutoExport().getEmailOnError()) ? I18nString.getLocaleString("default.none", locale) : exportWorker.getAutoExport().getEmailOnError());
			reportContent += I18nString.getLocaleString("autoImport.fileServer", locale) + ": " + exportWorker.getAutoExport().getFileServerWithoutCredentials();
			reportContent += I18nString.getLocaleString("autoImport.filePath", locale) + ": " + exportWorker.getAutoExport().getFilePath();
			reportContent += I18nString.getLocaleString("settings.FileName", locale) + ": " + exportWorker.getAutoExport().getFileNameWithPatterns();
		}
		
		if (exportWorker.getUsername() != null) {
			reportContent += "User: " + exportWorker.getUsername();
		}
		
		reportContent += I18nString.getLocaleString("export.type", locale) + ": " + I18nString.getLocaleString("Mailing", locale) + "\n";
		
		String profileContent = "";

		profileContent += I18nString.getLocaleString("Charset", locale) + ": " + exportWorker.getEncoding() + "\n";

		try {
			profileContent += I18nString.getLocaleString("csv.Delimiter", locale) + ": " + Character.toString(Separator.getSeparatorByChar(exportWorker.getDelimiter()).getValueChar()) + "\n";
		} catch (Exception e) {
			profileContent += I18nString.getLocaleString("csv.Delimiter", locale) + ": " + "Invalid (\"" + e.getMessage() + "\")" + "\n";
		}
		
		try {
			profileContent += I18nString.getLocaleString("csv.StringQuote", locale) + ": " + I18nString.getLocaleString(TextRecognitionChar.getTextRecognitionCharByChar(exportWorker.getStringQuote()).getPublicValue(), locale) + "\n";
		} catch (Exception e) {
			profileContent += I18nString.getLocaleString("csv.StringQuote", locale) + ": " + "Invalid (\"" + e.getMessage() + "\")" + "\n";
		}
		profileContent += I18nString.getLocaleString("csv.alwaysQuote", locale) + ": " + I18nString.getLocaleString(exportWorker.getAlwaysQuote() ? "delimiter.always" : "delimiter.ifneeded", locale) + "\n";
		
		profileContent += I18nString.getLocaleString("htmled.columns", locale) + ": " + StringUtils.join(AgnUtils.removeEmptyFromStringlist(exportWorker.getColumns()), ", ") + "\n";

		reportContent += "\t" + profileContent.replace("\n", "\n\t") + "\n";
		
		reportContent += I18nString.getLocaleString("StartTime", locale) + ": " + (exportWorker.getStartTime() == null ? I18nString.getLocaleString("Unknown", locale) : new SimpleDateFormat(DateUtilities.DD_MM_YYYY_HH_MM_SS).format(exportWorker.getStartTime())) + "\n";
		reportContent += I18nString.getLocaleString("EndTime", locale) + ": " + (exportWorker.getEndTime() == null ? I18nString.getLocaleString("Unknown", locale) : new SimpleDateFormat(DateUtilities.DD_MM_YYYY_HH_MM_SS).format(exportWorker.getEndTime())) + "\n";
		
		if (exportWorker.getRemoteFile() != null) {
			reportContent += I18nString.getLocaleString("settings.FileName", locale) + ": " + (StringUtils.isBlank(exportWorker.getRemoteFile().getRemoteFilePath()) ? I18nString.getLocaleString("Unknown", locale) : exportWorker.getRemoteFile().getRemoteFilePath()) + "\n";
			if (exportWorker.getRemoteFile().getDownloadDurationMillis() > -1) {
				reportContent += I18nString.getLocaleString("UploadDuration", locale) + ": " + AgnUtils.getHumanReadableDurationFromMillis(exportWorker.getRemoteFile().getDownloadDurationMillis()) + "\n";
			}
		}
		
		if (exportWorker.getError() != null) {
			reportContent += I18nString.getLocaleString("import.csv_fatal_error", locale) + ": " + exportWorker.getError().getClass().getSimpleName() + ": \n" + exportWorker.getError().getMessage() + "\n";
		}
		
		if (new File(exportWorker.getExportFile()).length() >= 0) {
			reportContent += I18nString.getLocaleString("mailing.Graphics_Component.FileSize", locale) + ": " + AgnUtils.getHumanReadableNumber(new File(exportWorker.getExportFile()).length(), "Byte", false, locale) + "\n";
		}
		
		reportContent += I18nString.getLocaleString("import.result.csvlines", locale) + ": " + AgnUtils.getHumanReadableNumber(exportWorker.getExportedLines(), locale) + " (+1 " + I18nString.getLocaleString("csv.ContainsHeaders", locale) + ")" + "\n";
		
		return reportContent;
	}

	private String generateLocalizedExportHtmlReport(MailingRecipientExportWorker exportWorker, ComAdmin admin) throws Exception {
		Locale locale = admin.getLocale();
		String title;
		if (exportWorker.getAutoExport() != null) {
			title = "AutoExport: " + exportWorker.getAutoExport().getShortname() + " (ID: " + exportWorker.getAutoExport().getAutoExportId() + ")";
		} else {
			title = "Export: " + I18nString.getLocaleString("Mailing", locale);
		}
		
		StringBuilder htmlContent = new StringBuilder(HtmlReporterHelper.getHtmlPrefixWithCssStyles(title));
		
		htmlContent.append(HtmlReporterHelper.getHeader(title, I18nString.getLocaleString("default.version", locale)));
		
		// Fatal Error
		if (exportWorker.getError() != null) {
			htmlContent.append(HtmlReporterHelper.getOutputTableStart());
			htmlContent.append(HtmlReporterHelper.getOutputTableHeader(I18nString.getLocaleString("import.csv_fatal_error", locale)));
			htmlContent.append(HtmlReporterHelper.getOutputTableContentStart(false));
			
			htmlContent.append(HtmlReporterHelper.getOutputTableErrorContentLine(exportWorker.getError().getClass().getSimpleName() + ": \n" + exportWorker.getError().getMessage()));

			htmlContent.append(HtmlReporterHelper.getOutputTableContentEnd());
			htmlContent.append(HtmlReporterHelper.getOutputTableEnd());
		}

		// Results
		htmlContent.append(HtmlReporterHelper.getOutputTableStart());
		htmlContent.append(HtmlReporterHelper.getOutputTableHeader(I18nString.getLocaleString("ResultMsg", locale)));
		htmlContent.append(HtmlReporterHelper.getOutputTableContentStart(true));

		if (new File(exportWorker.getExportFile()).length() >= 0) {
			htmlContent.append(HtmlReporterHelper.getOutputTableResultContentLine(I18nString.getLocaleString("mailing.Graphics_Component.FileSize", locale), AgnUtils.getHumanReadableNumber(new File(exportWorker.getExportFile()).length(), "Byte", false, locale)));
		}

		htmlContent.append(HtmlReporterHelper.getOutputTableResultContentLine(I18nString.getLocaleString("import.result.csvlines", locale), AgnUtils.getHumanReadableNumber(exportWorker.getExportedLines(), locale) + " (+1 " + I18nString.getLocaleString("csv.ContainsHeaders", locale) + ")"));

		htmlContent.append(HtmlReporterHelper.getOutputTableContentEnd());
		htmlContent.append(HtmlReporterHelper.getOutputTableEnd());

		// Informations
		htmlContent.append(HtmlReporterHelper.getOutputTableStart());
		htmlContent.append(HtmlReporterHelper.getOutputTableHeader(I18nString.getLocaleString("Info", locale)));
		htmlContent.append(HtmlReporterHelper.getOutputTableContentStart(false));
		
		htmlContent.append(HtmlReporterHelper.getOutputTableInfoContentLine(I18nString.getLocaleString("decode.licenseID", locale), configService.getValue(ConfigValue.System_Licence)));

		Company company = companyDao.getCompany(exportWorker.getCompanyID());
		htmlContent.append(HtmlReporterHelper.getOutputTableInfoContentLine(I18nString.getLocaleString("Company", locale), (company == null ? "Unknown" : company.getShortname() + " (ID: " + company.getId() + ")")));
		
		Mailing mailing = mailingDao.getMailing(exportWorker.getMailingID(), exportWorker.getCompanyID());
		htmlContent.append(HtmlReporterHelper.getOutputTableInfoContentLine(I18nString.getLocaleString("Mailing", locale), (mailing == null ? "Unknown" : mailing.getShortname() + " (ID: " + mailing.getId() + ")")));
		
		String filterTypeText = "Unknown";
		if (exportWorker.getFilterType() == MailingRecipientExportWorker.MAILING_RECIPIENTS_ALL) {
			filterTypeText = I18nString.getLocaleString("default.All", locale);
		} else if (exportWorker.getFilterType() == MailingRecipientExportWorker.MAILING_RECIPIENTS_OPENED) {
			filterTypeText = I18nString.getLocaleString("mailing.recipients.mailing_opened", locale);
		} else if (exportWorker.getFilterType() == MailingRecipientExportWorker.MAILING_RECIPIENTS_CLICKED) {
			filterTypeText = I18nString.getLocaleString("mailing.recipients.mailing_clicked", locale);
		} else if (exportWorker.getFilterType() == MailingRecipientExportWorker.MAILING_RECIPIENTS_BOUNCED) {
			filterTypeText = I18nString.getLocaleString("mailing.recipients.mailing_bounced", locale);
		} else if (exportWorker.getFilterType() == MailingRecipientExportWorker.MAILING_RECIPIENTS_UNSUBSCRIBED) {
			filterTypeText = I18nString.getLocaleString("mailing.recipients.mailing_unsubscribed", locale);
		}
		htmlContent.append(HtmlReporterHelper.getOutputTableInfoContentLine(I18nString.getLocaleString("report.mailing.filter", locale), filterTypeText));
		
		if (exportWorker.getAutoExport() != null) {
			htmlContent.append(HtmlReporterHelper.getOutputTableInfoContentLine("AutoExport", exportWorker.getAutoExport().getShortname() + " (ID: " + exportWorker.getAutoExport().getAutoExportId() + ")"));
			htmlContent.append(HtmlReporterHelper.getOutputTableInfoContentLine(I18nString.getLocaleString("error.import.profile.email", locale), (StringUtils.isBlank(exportWorker.getAutoExport().getEmailOnError()) ? I18nString.getLocaleString("default.none", locale) : exportWorker.getAutoExport().getEmailOnError())));
			htmlContent.append(HtmlReporterHelper.getOutputTableInfoContentLine(I18nString.getLocaleString("autoImport.fileServer", locale), exportWorker.getAutoExport().getFileServerWithoutCredentials()));
			htmlContent.append(HtmlReporterHelper.getOutputTableInfoContentLine(I18nString.getLocaleString("autoImport.filePath", locale), exportWorker.getAutoExport().getFilePath()));
			htmlContent.append(HtmlReporterHelper.getOutputTableInfoContentLine(I18nString.getLocaleString("settings.FileName", locale), exportWorker.getAutoExport().getFileNameWithPatterns()));
		}
		
		if (exportWorker.getUsername() != null) {
			htmlContent.append(HtmlReporterHelper.getOutputTableInfoContentLine("User", exportWorker.getUsername()));
		}
		
		htmlContent.append(HtmlReporterHelper.getOutputTableInfoContentLine(I18nString.getLocaleString("export.type", locale), I18nString.getLocaleString("Mailing", locale)));
		
		htmlContent.append(HtmlReporterHelper.getOutputTableInfoContentLine(I18nString.getLocaleString("Charset", locale), exportWorker.getEncoding()));

		try {
			htmlContent.append(HtmlReporterHelper.getOutputTableInfoContentLine(I18nString.getLocaleString("csv.Delimiter", locale), Character.toString(Separator.getSeparatorByChar(exportWorker.getDelimiter()).getValueChar())));
		} catch (Exception e) {
			htmlContent.append(HtmlReporterHelper.getOutputTableInfoContentLine(I18nString.getLocaleString("csv.Delimiter", locale), "Invalid (\"" + e.getMessage() + "\")"));
		}
		
		try {
			htmlContent.append(HtmlReporterHelper.getOutputTableInfoContentLine(I18nString.getLocaleString("csv.StringQuote", locale), I18nString.getLocaleString(TextRecognitionChar.getTextRecognitionCharByChar(exportWorker.getStringQuote()).getPublicValue(), locale)));
		} catch (Exception e) {
			htmlContent.append(HtmlReporterHelper.getOutputTableInfoContentLine(I18nString.getLocaleString("csv.StringQuote", locale), "Invalid (\"" + e.getMessage() + "\")"));
		}
		htmlContent.append(HtmlReporterHelper.getOutputTableInfoContentLine(I18nString.getLocaleString("csv.alwaysQuote", locale), I18nString.getLocaleString(exportWorker.getAlwaysQuote() ? "delimiter.always" : "delimiter.ifneeded", locale)));
		
		htmlContent.append(HtmlReporterHelper.getOutputTableSubHeader(I18nString.getLocaleString("htmled.columns", locale), false));
		for (String columnName : AgnUtils.removeEmptyFromStringlist(exportWorker.getColumns())) {
			htmlContent.append(HtmlReporterHelper.getOutputTableInfoContentLine("", columnName));
		}
		
		htmlContent.append(HtmlReporterHelper.getOutputTableInfoContentLine(I18nString.getLocaleString("StartTime", locale), (exportWorker.getStartTime() == null ? I18nString.getLocaleString("Unknown", locale) : DateUtilities.getDateTimeString(exportWorker.getStartTime(), TimeZone.getTimeZone(admin.getAdminTimezone()).toZoneId(), admin.getDateTimeFormatter()))));
		htmlContent.append(HtmlReporterHelper.getOutputTableInfoContentLine(I18nString.getLocaleString("EndTime", locale), (exportWorker.getEndTime() == null ? I18nString.getLocaleString("Unknown", locale) : DateUtilities.getDateTimeString(exportWorker.getEndTime(), TimeZone.getTimeZone(admin.getAdminTimezone()).toZoneId(), admin.getDateTimeFormatter()))));

		if (exportWorker.getRemoteFile() != null) {
			htmlContent.append(HtmlReporterHelper.getOutputTableInfoContentLine(I18nString.getLocaleString("settings.FileName", locale), StringUtils.isBlank(exportWorker.getRemoteFile().getRemoteFilePath()) ? I18nString.getLocaleString("Unknown", locale) : exportWorker.getRemoteFile().getRemoteFilePath()));
			if (exportWorker.getRemoteFile().getDownloadDurationMillis() > -1) {
				htmlContent.append(HtmlReporterHelper.getOutputTableInfoContentLine(I18nString.getLocaleString("UploadDuration", locale), AgnUtils.getHumanReadableDurationFromMillis(exportWorker.getRemoteFile().getDownloadDurationMillis())));
			}
		}

		htmlContent.append(HtmlReporterHelper.getOutputTableContentEnd());
		htmlContent.append(HtmlReporterHelper.getOutputTableEnd());
		
		htmlContent.append(HtmlReporterHelper.getFooter(AgnUtils.getHostName(), configService.getValue(ConfigValue.ApplicationVersion)));
		
		return htmlContent.toString();
	}

	public void sendExportErrorMail(MailingRecipientExportWorker exportWorker, ComAdmin admin) throws Exception {
		Set<String> emailRecipients = new HashSet<>();

		String additionalContent = "";
		Company comp = companyDao.getCompany(admin.getCompanyID());
		if (comp.getExportNotifyAdmin() > 0) {
			final ComAdmin notifyAdmin = adminService.getAdmin(comp.getExportNotifyAdmin(), admin.getCompanyID());

			if (notifyAdmin != null && StringUtils.isNotBlank(notifyAdmin.getEmail())) {
				emailRecipients.add(AgnUtils.normalizeEmail(notifyAdmin.getEmail()));
			} else {
				emailRecipients.add(AgnUtils.normalizeEmail(configService.getValue(ConfigValue.Mailaddress_Error)));
				additionalContent = "Admin User or Email for this export was not available: CompanyID: " + admin.getCompanyID() + " / AdminID: " + comp.getExportNotifyAdmin() + " \n\n";
			}
		}

		if (StringUtils.isNotBlank(configService.getValue(ConfigValue.ExportAlwaysInformEmail, admin.getCompanyID()))) {
			for (String email : AgnUtils.splitAndTrimList(configService.getValue(ConfigValue.ExportAlwaysInformEmail, admin.getCompanyID()))) {
				emailRecipients.add(AgnUtils.normalizeEmail(email));
			}
		}
		
		if (StringUtils.isNotBlank(configService.getValue(ConfigValue.Mailaddress_Error))) {
			emailRecipients.add(configService.getValue(ConfigValue.Mailaddress_Error).toLowerCase());
		}

		if (exportWorker.getAutoExport() != null && StringUtils.isNotBlank(exportWorker.getAutoExport().getEmailForReport())) {
			for (String email : AgnUtils.splitAndTrimList(exportWorker.getAutoExport().getEmailForReport())) {
				emailRecipients.add(AgnUtils.normalizeEmail(email));
			}
		}

		if (exportWorker.getAutoExport() != null && StringUtils.isNotBlank(exportWorker.getAutoExport().getEmailOnError())) {
			for (String email : AgnUtils.splitAndTrimList(exportWorker.getAutoExport().getEmailOnError())) {
				emailRecipients.add(AgnUtils.normalizeEmail(email));
			}
		}

		if (!emailRecipients.isEmpty()) {
			Locale locale = admin.getLocale();
			Company company = companyDao.getCompany(exportWorker.getCompanyID());
			
			String subject = "Export-ERROR: " + I18nString.getLocaleString("ResultMsg", locale) + ": " + " \"" + I18nString.getLocaleString("Mailing", locale) + "\" (" + I18nString.getLocaleString("Company", locale) + ": " + company.getShortname() + ")";
			String bodyHtml = generateLocalizedExportHtmlReport(exportWorker, admin) + "\n" + additionalContent;
			String bodyText = "Export-ERROR:\n" + generateLocalizedExportTextReport(exportWorker, locale) + "\n" + additionalContent;
						
			javaMailService.sendEmail(exportWorker.getCompanyID(), StringUtils.join(emailRecipients, ", "), subject, bodyText, bodyHtml);
		}
	}

	public void createAndSaveExportReport(MailingRecipientExportWorker exportWorker, ComAdmin admin, boolean isError) throws Exception {
		String fileToShow;
		if (exportWorker.getRemoteFile() != null && StringUtils.isNotBlank(exportWorker.getRemoteFile().getRemoteFilePath())) {
			// Remote file on ftp or sftp server
			fileToShow = exportWorker.getRemoteFile().getRemoteFilePath();
		} else {
			//Local exported File
			fileToShow = exportWorker.getExportFile();
		}
		recipientsReportService.createAndSaveExportReport(admin, fileToShow, exportWorker.getEndTime(), generateLocalizedExportHtmlReport(exportWorker, admin), isError);
	}
}
