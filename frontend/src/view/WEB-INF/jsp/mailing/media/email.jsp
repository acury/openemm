<%@page import="com.agnitas.emm.common.MailingType"%>
<%@ page language="java" contentType="text/html; charset=utf-8" buffer="32kb"  errorPage="/error.do" %>
<%@page import="com.agnitas.beans.MediatypeEmail"%>
<%@page import="com.agnitas.web.forms.ComMailingBaseForm"%>
<%@ taglib uri="https://emm.agnitas.de/jsp/jstl/tags" prefix="agn" %>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib prefix="emm" uri="https://emm.agnitas.de/jsp/jsp/common" %>

<%--@elvariable id="mailingBaseForm" type="com.agnitas.web.forms.ComMailingBaseForm"--%>
<c:set var="TEXTAREA_WIDTH" value="<%= ComMailingBaseForm.TEXTAREA_WIDTH%>" scope="page" />
<c:set var="TYPE_DATEBASED" value="<%= MailingType.DATE_BASED.getCode() %>"/>

<c:set var="isEmailSettingsEditable" value="${mailingBaseForm.canChangeEmailSettings}"/>

<c:set var="isEmailSettingsDisabled" value="${not IS_MAILING_EDITABLE or not isEmailSettingsEditable}"/>

<emm:ShowByPermission token="template.show">

    <div class="tile" data-action="scroll-to">
        <div class="tile-header">
            <a href="#" class="headline" data-toggle-tile="#tile-mailingTemplate">
                <i class="tile-toggle icon icon-angle-up"></i>
                <bean:message key="mailing.frame"/>
            </a>
            <logic:equal name="mailingBaseForm" property="isTemplate" value="false">
                <c:if test="${mailingBaseForm.mailingID ne 0}">
                    <ul class="tile-header-nav">
                        <li class="active">
                            <a href="#" data-toggle-tab="#tab-mailingTemplateBase"><bean:message key="default.basic"/></a>
                        </li>
                        <li>
                            <a href="#" data-toggle-tab="#tab-mailingTemplateAdvanced" data-extends-tab="#tab-mailingTemplateBase"><bean:message key="default.advanced"/></a>
                        </li>
                    </ul>
                </c:if>
            </logic:equal>
        </div>
        <div id="tile-mailingTemplate" class="tile-content tile-content-forms">
            <logic:equal name="mailingBaseForm" property="isTemplate" value="false">
                <div id="tab-mailingTemplateBase">
                    <logic:equal name="mailingBaseForm" property="mailingID" value="0">
                        <c:choose>
                            <c:when test="${mailingBaseForm.isMailingGrid}">
                                <div class="form-group">
                                    <div class="col-sm-4">
                                        <label class="control-label"><bean:message key="Layout"/></label>
                                    </div>
                                    <div class="col-sm-8">
                                        <input type="text" class="form-control" value="${mailingBaseForm.gridTemplateName}" readonly>
                                    </div>
                                </div>
                            </c:when>
                            <c:otherwise>
                                <c:if test="${mailingBaseForm.mailingID ne 0}">
                                    <logic:equal name="mailingBaseForm" property="copyFlag" value="false">
                                        <div class="form-group">
                                            <div class="col-sm-4">
                                                <label class="control-label" for="settingsGeneralTemplate"><bean:message key="Template"/>:</label>
                                            </div>
                                            <div class="col-sm-8">
                                                <agn:agnSelect styleId="settingsGeneralTemplate" property="templateID"
                                                               styleClass="form-control js-select" data-form-action="7" >
                                                    <html:option value="0"><bean:message key="mailing.No_Template"/></html:option>
                                                    <logic:iterate id="agntbl3" name="mailingBaseForm" property="templateMailingBases" length="500">
                                                        <html:option value="${agntbl3.id}">${agntbl3.shortname}</html:option>
                                                    </logic:iterate>
                                                </agn:agnSelect >
                                            </div>
                                        </div>
                                    </logic:equal>
                                </c:if>
                                <logic:equal name="mailingBaseForm" property="copyFlag" value="true">
                                    <div class="form-group">
                                        <div class="col-sm-4">
                                            <label class="control-label"><bean:message key="Template"/></label>
                                        </div>
                                        <div class="col-sm-8">
                                            <input type="text" class="form-control" value="${mailingBaseForm.templateShortname}" readonly>
                                        </div>
                                    </div>
                                </logic:equal>
                            </c:otherwise>
                        </c:choose>
                    </logic:equal>
                    <logic:notEqual name="mailingBaseForm" property="mailingID" value="0">
                        <c:choose>
                            <c:when test="${mailingBaseForm.isMailingGrid}">
                                <div class="form-group">
                                    <div class="col-sm-4">
                                        <label class="control-label"><bean:message key="Layout"/></label>
                                    </div>
                                    <div class="col-sm-8">
                                        <input type="text" class="form-control" value="${mailingBaseForm.gridTemplateName}" readonly>
                                    </div>
                                </div>
                            </c:when>
                            <c:otherwise>
                                <div class="form-group">
                                    <div class="col-sm-4">
                                        <label class="control-label"><bean:message key="Template"/></label>
                                    </div>
                                    <div class="col-sm-8">
                                        <input type="text" class="form-control" value="${mailingBaseForm.templateShortname}" readonly>
                                    </div>
                                </div>
                            </c:otherwise>
                        </c:choose>
                        <%--<c:if test="${mailingBaseForm.isMailingGrid}">
                            <div class="form-group">
                                <div class="col-sm-4">
                                    <label class="control-label"><bean:message key="Layout"/></label>
                                </div>
                                <div class="col-sm-8">
                                    <input type="text" class="form-control" value="${mailingBaseForm.gridTemplateName}" readonly>
                                </div>
                            </div>
                        </c:if>
                        <div class="form-group">
                            <div class="col-sm-4">
                                <label class="control-label"><bean:message key="Template"/></label>
                            </div>
                            <div class="col-sm-8">
                                <input type="text" class="form-control" value="${mailingBaseForm.templateShortname}" readonly>
                            </div>
                        </div>--%>
                    </logic:notEqual>
                </div>
                <div id="tab-mailingTemplateAdvanced" class="${mailingBaseForm.mailingID ne 0 ? 'hidden' : ''}">
            </logic:equal>
            <logic:equal name="mailingBaseForm" property="isTemplate" value="true">
                <logic:equal name="mailingBaseForm" property="copyFlag" value="false">
                    <html:hidden property="templateID" value="0"/>
                </logic:equal>
            </logic:equal>
                <div class="inline-tile form-group" data-field="validator">
                    <div class="inline-tile-header">
                        <h2 class="headline"><bean:message key="Text_Version"/></h2>
                        <ul class="inline-tile-header-actions">
                            <li>
                                <a href="#" data-modal="modal-editor" data-modal-set="title: <bean:message key="Text_Version"/>, target: textTemplate, id: textTemplateLarge, type: text" data-tooltip="<bean:message key='editor.enlargeEditor'/>">
                                    <i class="icon icon-arrows-alt"></i>
                                </a>
                            </li>
                        </ul>
                    </div>
                    <div class="inline-tile-content">
                        <div class="row">
                            <div class="col-sm-12">
                                <agn:agnTextarea styleId="textTemplate" property="textTemplate" rows="14" cols="${TEXTAREA_WIDTH}"
                                               data-field-validator="reject-script-element"
                                               styleClass="form-control js-editor-text" readonly="${not IS_MAILING_EDITABLE}"/>
                            </div>
                        </div>
                    </div>
                </div>

                <c:if test="${mailingBaseForm.mediaEmail.mailFormat != 0 and not mailingBaseForm.isMailingGrid}">
                    <div class="inline-tile form-group" data-field="validator">
                        <div class="inline-tile-header">
                            <h2 class="headline"><bean:message key="mailing.HTML_Version"/></h2>
                            <ul class="inline-tile-header-actions">
                                <li>
                                    <a href="#" data-modal="modal-editor" data-modal-set="title: <bean:message key="mailing.HTML_Version"/>, target: htmlTemplate, id: htmlTemplateLarge" data-tooltip="<bean:message key='editor.enlargeEditor'/>">
                                        <i class="icon icon-arrows-alt"></i>
                                    </a>
                                </li>
                            </ul>
                        </div>
                        <div class="inline-tile-content">
                            <div class="row">
                                <div class="col-sm-12">
                                    <agn:agnTextarea styleId="htmlTemplate" property="htmlTemplate" rows="14" cols="${TEXTAREA_WIDTH}"
                                                   data-field-validator="reject-script-element"
                                                   styleClass="form-control js-editor" readonly="${not IS_MAILING_EDITABLE}" />
                                </div>
                            </div>
                        </div>
                    </div>
                </c:if>
                <c:if test="${show_dynamic_template_checkbox}">
                    <div class="form-group checkbox">
                        <label class="toggle">
                            <html:checkbox property="dynamicTemplateString" />
                            <div class="toggle-control"></div>
                            <span class="text">
                                <c:if test="${mailingBaseForm.isTemplate}">
                                    <bean:message key="mailing.dynamic_template.preset" />
                                    <button class="icon icon-help" data-help="help_${helplanguage}/mailing/view_base/TemplateUpdateMailingMsg.xml" tabindex="-1" type="button"></button>
                                </c:if>
                                <c:if test="${not mailingBaseForm.isTemplate}">
                                    <bean:message key="mailing.dynamic_template" />
                                </c:if>
                            </span>
                        </label>
                    </div>
                </c:if>
            <logic:equal name="mailingBaseForm" property="isTemplate" value="false">
                </div>
            </logic:equal>
        </div>
    </div>

</emm:ShowByPermission>

<div class="tile" data-action="scroll-to">
    <div class="tile-header">
        <a href="#" class="headline" data-toggle-tile="#tile-mediaEmail">
            <i class="tile-toggle icon icon-angle-up"></i>
            <bean:message key="mailing.MediaType.0"/>
        </a>
        <ul class="tile-header-nav">
            <li class="active">
                <a href="#" data-toggle-tab="#tab-mailingMediaEmailBase"><bean:message key="default.basic"/></a>
            </li>
            <li>
                <a href="#" data-toggle-tab="#tab-mailingMediaEmailAdvanced" data-extends-tab="#tab-mailingMediaEmailBase"><bean:message key="default.advanced"/></a>
            </li>
        </ul>
    </div>

    <div id="tile-mediaEmail" class="tile-content tile-content-forms">
        <div id="tab-mailingMediaEmailBase" >
            <div class="form-group" data-field="validator">
                <div class="col-sm-4">
                    <label class="control-label" for="emailSubject">
                        <bean:message key="mailing.Subject"/>*
                    </label>
                </div>
                <div class="col-sm-8">
                    <agn:agnText styleId="emailSubject" property="emailSubject"
                           styleClass="form-control" readonly="${isEmailSettingsDisabled}" data-field-validator="length"
                           data-validator-options="required: true, min: 2" />
                </div>
            </div>

            <div class="form-group">
                <div class="col-sm-4">
                    <label class="control-label" for="emailMailFormat">
                        <bean:message key="action.Format"/>
                    </label>
                </div>
                <div class="col-sm-8">
                    <html:select styleId="emailMailFormat" property="mediaEmail.mailFormat"
                                 styleClass="form-control" disabled="${isEmailSettingsDisabled}">
                        <html:option value="0"><bean:message key="only_Text"/></html:option>
                        <html:option value="1"><bean:message key="Text_HTML"/></html:option>
                        <html:option value="2"><bean:message key="Text_HTML_OfflineHTML"/></html:option>
                    </html:select>
                </div>
            </div>

            <div class="form-group" data-field="validator">
                <div class="col-sm-4">
                    <label class="control-label" for="emailSenderMail">
                        <bean:message key="mailing.SenderEmail"/>*
                    </label>
                </div>
                <div class="col-sm-8">
                    <agn:agnText styleId="emailSenderMail" property="media[0].fromEmail"
                               styleClass="form-control" readonly="${isEmailSettingsDisabled}" data-field-validator="length"
                               data-validator-options="required: true"/>
               </div>
            </div>

            <div class="form-group">
                <div class="col-sm-4">
                    <label class="control-label" for="emailSenderName">
                        <bean:message key="mailing.SenderFullname"/>
                    </label>
                </div>
                <div class="col-sm-8">
                    <html:text styleId="emailSenderName" property="media[0].fromFullname"
                           styleClass="form-control" readonly="${isEmailSettingsDisabled}" />
                </div>
            </div>

            <div class="form-group" data-field="validator">
                <div class="col-sm-4">
                    <label class="control-label" for="emailReplyEmail">
                        <bean:message key="mailing.ReplyEmail"/>*
                    </label>
                </div>
                <div class="col-sm-8">
                    <agn:agnText styleId="emailReplyEmail" property="media[0].replyEmail"
                           styleClass="form-control" readonly="${isEmailSettingsDisabled}" data-field-validator="length"
                               data-validator-options="required: true"/>
                </div>
            </div>

            <div class="form-group">
                <div class="col-sm-4">
                    <label class="control-label" for="emailReplyName">
                        <bean:message key="mailing.ReplyFullName"/>
                    </label>
                </div>
                <div class="col-sm-8">
                    <html:text styleId="emailReplyName" property="media[0].replyFullname"
                           styleClass="form-control" readonly="${isEmailSettingsDisabled}" />
                </div>
            </div>
        </div>

        <div id="tab-mailingMediaEmailAdvanced" class="hidden">

            <emm:ShowByPermission token="mailing.envelope_address">
                <div class="form-group">
                    <div class="col-sm-4">
                        <label class="control-label" for="emailEnvelopeEmail">
                            <bean:message key="EnvelopeEmail"/>
	                        <button class="icon icon-help" data-help="help_${helplanguage}/mailing/view_base/EnvelopeAddress.xml" tabindex="-1" type="button"></button>
	                    </label>
                    </div>
                    <div class="col-sm-8">
                        <html:text styleId="emailEnvelopeEmail" property="media[0].envelopeEmail"
                                   styleClass="form-control" readonly="${isEmailSettingsDisabled}" />
                    </div>
                </div>
            </emm:ShowByPermission>

            <div id="mailing-bcc-recipients" class="form-group ${mailingBaseForm.mailingType == TYPE_DATEBASED ? '' : 'hidden'}">
               <div class="col-sm-4">
                   <label class="control-label" for="bccRecipientEmails">
                       <bean:message key="action.address.bcc"/>
                   </label>
               </div>
               <div class="col-sm-8">
                   <html:text styleId="bccRecipientEmails" property="media[0].bccRecipients" styleClass="form-control" />
               </div>
            </div>

            <div class="form-group">
                <div class="col-sm-4">
                    <label class="control-label" for="emailCharset">
                        <bean:message key="mailing.Charset"/>
                    </label>
                </div>
                <div class="col-sm-8">
                	<%@include file="email-charsets.jspf" %>
                </div>
            </div>
			<emm:ShowByPermission token="mailing.linefeed">
	            <div class="form-group">
	                <div class="col-sm-4">
	                    <label class="control-label" for="emailLinefeed">
	                        <bean:message key="Linefeed_After"/>
	                    </label>
	                </div>
	                <div class="col-sm-8">
	                    <html:select styleId="emailLinefeed" property="emailLinefeed"
	                                 styleClass="form-control" disabled="${isEmailSettingsDisabled}">
	                        <html:option value="0"><bean:message key="mailing.No_Linefeed"/></html:option>
	                        <c:forEach begin="60" end="80" step="1" var="a">
	                            <html:option value="${a}">${a} <bean:message key="Characters"/></html:option>
	                        </c:forEach>
	                    </html:select>
	                </div>
	            </div>
			</emm:ShowByPermission>
           <jsp:include page="email-onepixel.jsp">
                <jsp:param name="isEmailSettingsDisabled" value="${isEmailSettingsDisabled}"/>
           </jsp:include>

        </div>
    </div>
</div>


<script id="modal-editor" type="text/x-mustache-template">
    <div class="modal modal-editor">
        <div class="modal-dialog">
            <div class="modal-content">
                <div class="modal-header">
                    <button type="button" class="close-icon close" data-dismiss="modal">
                        <i aria-hidden="true" class="icon icon-times-circle"></i>
                    </button>
                    <h4 class="modal-title">{{= title }}</h4>
                </div>
                <div class="modal-body">
                    <textarea id="{{= id }}" data-sync="\#{{= target}}" class="form-control js-editor{{- (typeof(type) == 'undefined') ? '' : '-' + type }}"></textarea>
                </div>
                <div class="modal-footer">
                    <div class="btn-group">
                        <button type="button" class="btn btn-default btn-large" data-dismiss="modal">
                            <i class="icon icon-times"></i>
                            <span class="text"><bean:message key="button.Cancel"/></span>
                        </button>

                        <c:choose>
                            <c:when test="${mailingBaseForm.isTemplate}">
                                <c:set var="permToken" value="template.change" />
                            </c:when>
                            <c:otherwise>
                                <c:set var="permToken" value="mailing.change" />
                            </c:otherwise>
                        </c:choose>
                        <emm:ShowByPermission token="${permToken}">
                            <button type="button" class="btn btn-primary btn-large" data-sync-from="\#{{= id }}" data-sync-to="\#{{= target }}" data-dismiss="modal" data-form-target="#mailingBaseForm" data-form-set="save: save" data-form-submit="">
                                <i class="icon icon-save"></i>
                                <span class="text"><bean:message key="button.Save"/></span>
                            </button>
                        </emm:ShowByPermission>
                    </div>
                </div>
            </div>
        </div>
    </div>
</script>
