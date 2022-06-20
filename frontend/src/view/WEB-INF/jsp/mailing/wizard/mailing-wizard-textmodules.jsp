<%@ page language="java" contentType="text/html; charset=utf-8" errorPage="/error.do" %>
<%@ page import="com.agnitas.web.ComMailingContentAction" %>
<%@ page import="com.agnitas.web.MailingWizardAction" %>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
<%@ taglib uri="https://emm.agnitas.de/jsp/jstl/tags" prefix="agn" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<c:set var="ACTION_FINISH" value="<%= MailingWizardAction.ACTION_FINISH %>"/>
<c:set var="ACTION_TEXTMODULE" value="<%= MailingWizardAction.ACTION_TEXTMODULE %>"/>
<c:set var="ACTION_SUBJECT" value="<%= MailingWizardAction.ACTION_SUBJECT %>"/>

<c:url var="previous" value="/mwSubject.do?action=${ACTION_SUBJECT}"/>

<agn:agnForm action="/mwTextmodules" id="wizard-step-8" data-form="resource">
    <html:hidden property="action" value="${ACTION_TEXTMODULE}"/>
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
                                <a href="${previous}">
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
                            <li class="active"><span>8</span></li>
                            <li class="disabled"><span>9</span></li>
                            <li class="disabled"><span>10</span></li>
                            <li class="disabled"><span>11</span></li>
                            <li>
                                <a href="#" data-form-action="${ACTION_TEXTMODULE}">
                                    <bean:message key="button.Proceed" />
                                    <i class="icon icon-angle-right"></i>
                                </a>
                            </li>
                        </ul>
                    </li>
                </ul>
            </div>
            <div class="tile-notification tile-notification-info">
                <bean:message key="mailing.wizard.TextModulesMsg"/>
                <button type="button" data-help="help_${helplanguage}/mailingwizard/step_08/TextModulesMsg.xml" class="icon icon-help"></button>
            </div>
            <div class="tile-content tile-content-forms">
            </div>
            <div class="tile-footer">
                <a href="${previous}" class="btn btn-large pull-left">
                    <i class="icon icon-angle-left"></i>
                    <span class="text"><bean:message key="button.Back"/></span>
                </a>
                <div class="btn-group pull-right">
                    <a href="#" class="btn btn-large btn-primary" data-form-action="${ACTION_TEXTMODULE}">
                        <span class="text"><bean:message key="button.Proceed"/></span>
                        <i class="icon icon-angle-right"></i>
                    </a>
                    <a href="#" class="btn btn-large btn-primary" data-form-action="skip">
                        <span class="text"><bean:message key="button.Skip"/></span>
                    </a>
                    <a href="#" class="btn btn-large btn-primary" data-form-action="${ACTION_FINISH}">
                        <span class="text"><bean:message key="button.Finish"/></span>
                    </a>
                </div>
                <span class="clearfix"></span>
            </div>
        </div>
    </div>
</agn:agnForm>
