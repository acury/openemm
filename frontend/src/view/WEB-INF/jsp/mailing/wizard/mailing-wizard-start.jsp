<%@ page language="java" contentType="text/html; charset=utf-8" errorPage="/error.do" %>
<%@ page import="com.agnitas.web.MailingBaseAction" %>
<%@ page import="com.agnitas.web.MailingWizardAction" %>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib prefix="emm" uri="https://emm.agnitas.de/jsp/jsp/common" %>

<%--@elvariable id="emmLayoutBase" type="org.agnitas.beans.EmmLayoutBase"--%>
<%--@elvariable id="workflowId" type="java.lang.Integer"--%>

<c:set var="ACTION_START" 				value="<%=MailingWizardAction.ACTION_START%>"/>
<c:set var="ACTION_NEW" 				value="<%= MailingBaseAction.ACTION_NEW %>"/>
<c:set var="ACTION_MAILING_TEMPLATES" 	value="<%= MailingBaseAction.ACTION_MAILING_TEMPLATES %>"/>
<c:set var="ACTION_MAILING_IMPORT" 		value="<%= MailingBaseAction.ACTION_MAILING_IMPORT %>" />
<emm:setAbsolutePath var="absoluteImagePath" path="${emmLayoutBase.imagesURL}"/>

<div class="col-md-offset-3 col-md-6">
    <div class="tile">
        <div class="tile-header">
            <h2 class="headline"><bean:message key="mailing.mode.select"/>:</h2>
        </div>

        <div class="tile-content">
            <ul class="link-list">
            <%@include file="mailing-wizard-start-grid.jspf" %>
            
            <emm:ShowByPermission token="mailing.classic">
                <li>
                    <emm:ShowByPermission token="mailing.create.classic.migration">
                        <c:set var="standardBtnRef" value="/mailing/templates.action?keepForward=${workflowId > 0}"/>                         
                    </emm:ShowByPermission>
                    <emm:HideByPermission token="mailing.create.classic.migration">
                        <c:set var="standardBtnRef" value="/mailingbase.do?action=${ACTION_MAILING_TEMPLATES}&mailingID=0&isTemplate=false&keepForward=${workflowId > 0}"/>
                    </emm:HideByPermission>
                    <html:link page="${standardBtnRef}" styleClass="link-list-item">
                        <div class="thumbnail">
                            <img alt="" class="media-object" src="${absoluteImagePath}/facelift/agn_mailing-new-standard.png">
                        </div>
                        <div class="media-body">
                            <p class="headline">
                                <bean:message key="mailing.wizard.Normal"/>
                            </p>
                            <p class="description">
                                <bean:message key="mailing.wizard.NormalDescription"/>
                            </p>
                        </div>
                        <i class="nav-arrow icon icon-angle-right"></i>
                    </html:link>
                </li>
                <emm:ShowByPermission token="mailing.wizard">
			    <li>
                    <html:link page="/mwStart.do?action=${ACTION_START}&keepForward=${workflowId > 0}" styleClass="link-list-item">
                        <div class="thumbnail">
                            <img alt=""  class="media-object" src="${absoluteImagePath}/facelift/agn_mailing-new-assistant.png">
                        </div>
                        <div class="media-body">
                            <p class="headline">
                                <bean:message key="mailing.Wizard"/>
                            </p>
                            <p class="description">
                                <bean:message key="mailing.wizard.WizardDescription"/>
                            </p>
                        </div>
                        <i class="nav-arrow icon icon-angle-right"></i>
                    </html:link>
                </li>
                </emm:ShowByPermission>
                </emm:ShowByPermission>
                <emm:ShowByPermission token="mailing.import">
                    <li>
	                    <html:link page="/mailingbase.do?action=${ACTION_MAILING_IMPORT}" styleClass="link-list-item">
                        <div class="thumbnail">
                            <img alt="" class="media-object" src="${absoluteImagePath}/file-download.png">
                        </div>
                        <div class="media-body">
                            <p class="headline">
                                <bean:message key="import.csv_upload"/>
                            </p>
                            <p class="description">
                                <bean:message key="mailing.import"/>
                            </p>
                        </div>
                        <i class="nav-arrow icon icon-angle-right"></i>
                    </html:link>
                    </li>
                </emm:ShowByPermission>
            </ul>
        </div>
    </div>
</div>
