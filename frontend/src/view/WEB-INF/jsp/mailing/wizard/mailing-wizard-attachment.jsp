<%@page import="org.agnitas.beans.MailingComponentType"%>
<%@ page language="java" contentType="text/html; charset=utf-8" errorPage="/error.do" %>
<%@ page import="com.agnitas.web.MailingWizardAction" %>
<%@ page import="org.agnitas.beans.MailingComponent" %>
<%@ taglib uri="https://emm.agnitas.de/jsp/jstl/tags" prefix="agn" %>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib prefix="emm" uri="https://emm.agnitas.de/jsp/jsp/common" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>

<%--@elvariable id="mailingWizardForm" type="org.agnitas.web.MailingWizardForm"--%>

<c:set var="ACTION_FINISH" value="<%= MailingWizardAction.ACTION_FINISH %>"/>
<c:set var="ACTION_ATTACHMENT_DOWNLOAD" value="<%= MailingWizardAction.ACTION_ATTACHMENT_DOWNLOAD %>"/>

<c:set var="TYPE_ATTACHMENT" value="<%= MailingComponentType.Attachment %>"/>
<c:set var="TYPE_PERSONALIZED_ATTACHMENT" value="<%= MailingComponentType.PersonalizedAttachment %>"/>

<agn:agnForm action="/mwAttachment" id="wizard-step-10" enctype="multipart/form-data" data-form="resource">
    <html:hidden property="action" value="${ACTION_FINISH}"/>
    <html:hidden property="keepForward" value="${not empty workflowId and workflowId gt 0 ? true : false}"/>

    <div class="col-md-10 col-md-push-1 col-lg-8 col-lg-push-2">
        <div class="tile">
            <div class="tile-header">
                <h2 class="headline">
                    <i class="icon icon-file-o"></i>
                    <bean:message key="mailing.Wizard" />
                </h2>
                <ul class="tile-header-actions">
                    <li class="">
                        <ul class="pagination">
                            <li>
                                <a href="#" data-form-persist="action: previous" data-form-submit="">
                                    <i class="icon icon-angle-left"></i>
                                    <bean:message key="button.Back" />
                                </a>
                            </li>
                            <li class="disabled"><span>1</span></li>
                            <li class="disabled"><span>2</span></li>
                            <li class="disabled"><span>3</span></li>
                            <li class="disabled"><span>4</span></li>
                            <li class="disabled"><span>5</span></li>
                            <li class="disabled"><span>6</span></li>
                            <li class="disabled"><span>7</span></li>
                            <li class="disabled"><span>8</span></li>
                            <li class="disabled"><span>9</span></li>
                            <li class="active"><span>10</span></li>
                            <li class="disabled"><span>11</span></li>
                        </ul>
                    </li>
                </ul>
            </div>
            <div class="tile-notification tile-notification-info">
                <bean:message key="mailing.Attachments"/>
            </div>
            <div class="tile-content tile-content-forms">
                <emm:ShowByPermission token="mailing.attachments.show">
                    <div class="inline-tile">
                        <div class="inline-tile-header">
                            <h2 class="headline"><bean:message key="New_Attachment"/></h2>
                        </div>
                        <div class="inline-tile-content">
                            <div data-field="toggle-vis">
                                <%@include file="mailing-wizard-attachment-personalize.jspf" %>
                                <div class="form-group">
                                    <div class="col-sm-4">
                                        <label class="control-label" for="newAttachment"><bean:message key="mailing.Attachment"/></label>
                                    </div>
                                    <div class="col-sm-8">
                                        <html:file property="newAttachment" styleClass="form-control" styleId="newAttachment"/>
                                    </div>
                                </div>
                                <div class="form-group">
                                    <div class="col-sm-4">
                                        <label class="control-label" for="newAttachmentName"><bean:message key="attachment.name"/></label>
                                    </div>
                                    <div class="col-sm-8">
                                        <html:text property="newAttachmentName" styleId="newAttachmentName" styleClass="form-control"/>
                                    </div>
                                </div>
                                <%@include file="mailing-wizard-attachment-personalize2.jspf" %>
                                <div class="form-group">
                                    <div class="col-sm-4">
                                        <label class="control-label" for="attachmentTargetID"><bean:message key="Target"/></label>
                                    </div>
                                    <div class="col-sm-8">
                                        <html:select styleClass="form-control js-select" property="attachmentTargetID" size="1" styleId="attachmentTargetID">
                                            <html:option value="0"><bean:message key="statistic.all_subscribers"/></html:option>
                                            <c:forEach var="target" items="${targets}">
                                                <html:option value="${target.id}">
                                                    ${target.targetName}
                                                </html:option>
                                            </c:forEach>
                                        </html:select>
                                    </div>
                                </div>
                            </div>
                        </div>
                        <div class="inline-tile-footer">
                            <a href="#" class="btn btn-regular btn-primary" data-form-persist="action: attachment" data-form-submit="">
                                <i class="icon icon-plus-circle"></i>
                                <span class="text"><bean:message key="button.Add"/></span>
                            </a>
                        </div>
                    </div>

                    <c:forEach var="entry" items="${mailingWizardForm.mailing.components}" varStatus="loop">
                        <c:set var="component" value="${entry.value}"/>

                        <c:if test="${component.type eq TYPE_ATTACHMENT or component.type eq TYPE_PERSONALIZED_ATTACHMENT}">
                            <div class="tile-separator"></div>
                            <div class="inline-tile">
                                <div class="inline-tile-header">
                                    <h2 class="headline">${fn:escapeXml(component.componentName)}</h2>
                                </div>
                                <div class="inline-tile-content">
                                    <input type="hidden" name="compid${loop.index}" value="${component.id}"/>

                                    <div class="form-group">
                                        <div class="col-sm-offset-4 col-sm-8">
                                            <ul class="list-group">
                                                <c:choose>
                                                    <c:when test="${component.type eq TYPE_ATTACHMENT}">
                                                        <li class="list-group-item">
                                                            <span class="badge">${component.mimeType}</span>
                                                            <bean:message key="Mime_Type"/>
                                                        </li>
                                                        <li class="list-group-item">
                                                            <span class="badge">${fn:length(component.binaryBlock)} <bean:message key="default.KByte"/></span>
                                                            <bean:message key="Original_Size"/>
                                                        </li>
                                                        <li class="list-group-item">
                                                            <span class="badge">${fn:length(component.emmBlock)}  <bean:message key="default.KByte"/></span>
                                                            <bean:message key="default.Size_Mail"/>
                                                        </li>
                                                    </c:when>
                                                    <c:when test="${component.type eq TYPE_PERSONALIZED_ATTACHMENT}">
                                                        <li class="list-group-item"><bean:message key="attachment.type.personalized"/></li>
                                                    </c:when>
                                                </c:choose>
                                            </ul>
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <div class="col-sm-4">
                                            <label class="control-label" for="targetID${component.id}" size="1" value="${component.targetID}"><bean:message key="Target"/></label>
                                        </div>
                                        <div class="col-sm-8">
                                            <html:select styleClass="form-control js-select" property="targetID${component.id}" disabled="true" size="1" value="${component.targetID}" styleId="targetID${component.id}">
                                                <html:option value="0"><bean:message key="statistic.all_subscribers"/></html:option>
                                                <c:forEach var="target" items="${targets}">
                                                    <html:option value="${target.id}">${fn:escapeXml(target.targetName)}</html:option>
                                                </c:forEach>
                                            </html:select>
                                        </div>
                                    </div>
                                </div>
                                <div class="inline-tile-footer">
                                    <html:link styleClass="btn btn-primary btn-regular" page='/mwAttachmentDownload.do?action=${ACTION_ATTACHMENT_DOWNLOAD}&compName=${component.componentNameUrlEncoded}'>
                                        <i class="icon icon-download"></i>
                                        <bean:message key="button.Download"/>
                                    </html:link>
                                </div>
                            </div>
                        </c:if>
                    </c:forEach>
                </emm:ShowByPermission>
            </div>
            <div class="tile-footer">
                <a href="#" class="btn btn-large pull-left" data-form-persist="action: previous" data-form-submit="">
                    <i class="icon icon-angle-left"></i>
                    <span class="text"><bean:message key="button.Back"/></span>
                </a>
                <div class="btn-group pull-right">
                    <a href="#" class="btn btn-large btn-primary" data-form-persist="action: ${ACTION_FINISH}" data-form-submit="">
                        <span class="text"><bean:message key="button.Skip"/></span>
                    </a>
                    <a href="#" class="btn btn-large btn-primary" data-form-persist="action: ${ACTION_FINISH}" data-form-submit="">
                        <span class="text"><bean:message key="button.Finish"/></span>
                    </a>
                </div>
                <span class="clearfix"></span>
            </div>
        </div>
    </div>
</agn:agnForm>
