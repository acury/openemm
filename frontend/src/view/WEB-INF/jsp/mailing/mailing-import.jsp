<%@ page language="java" contentType="text/html; charset=utf-8" errorPage="/error.do" %>
<%@ page import="com.agnitas.web.MailingBaseAction"%>
<%@ taglib uri="https://emm.agnitas.de/jsp/jstl/tags" prefix="agn" %>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<c:set var="ACTION_MAILING_IMPORT" value="<%= MailingBaseAction.ACTION_MAILING_IMPORT %>"/>
<c:set var="ACTION_IMPORT_TEMPLATES" value="<%= MailingBaseAction.ACTION_IMPORT_TEMPLATES %>"/>
<c:set var="importAction" value="${ACTION_MAILING_IMPORT}"/>
<c:if test="${not empty param.importFromTemplates and param.importFromTemplates}">
	<c:set var="importAction" value="${ACTION_IMPORT_TEMPLATES}"/>
</c:if>

<agn:agnForm action="/mailingbase" id="mailingImport" enctype="multipart/form-data" data-form="static">
	<html:hidden property="action"/>
	<html:hidden property="isGrid" value="false"/>
	<html:hidden property="isTemplate" value="false"/>
	
	<div class="tile">
		<div class="tile-header">
			<h2 class="headline">
				<bean:message key="mailing.import" />
			</h2>
		</div>
		<div class="tile-content">
			<div class="tile-content-forms form-vertical">
				<div class="form-group">
					<div class="input-group">
						<div class="input-group-controls">
							<html:file property="uploadFile" styleId="uploadFile" styleClass="form-control" />
						</div>
						<div class="input-group-btn">
							<button type="button" class="btn btn-regular btn-primary" data-form-persist="upload_file: 'upload_file', action: ${importAction}" data-form-submit>
								<i class="icon icon-cloud-upload"></i>
								<span class="text">
									<bean:message key="mailing.import"/>
								</span>
							</button>
						</div>
					</div>
				</div>
			</div>
		</div>
	</div>
</agn:agnForm>
