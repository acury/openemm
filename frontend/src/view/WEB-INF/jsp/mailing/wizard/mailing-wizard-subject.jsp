<%@ page language="java" contentType="text/html; charset=utf-8"  errorPage="/error.do" %>
<%@ page import="com.agnitas.web.MailingWizardAction" %>
<%@ taglib uri="https://emm.agnitas.de/jsp/jstl/tags" prefix="agn" %>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<c:set var="ACTION_SUBJECT" value="<%= MailingWizardAction.ACTION_SUBJECT %>" scope="request"/>

<agn:agnForm action="/mwSubject" id="wizard-step-6" data-form-focus="emailSubject" data-form="resource">
    <html:hidden property="action" value="${ACTION_SUBJECT}"/>

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
                                <a href="#" data-form-action="previous">
                                    <i class="icon icon-angle-left"></i>
                                    <bean:message key="button.Back" />
                                </a>
                            </li>
                            <li class="disabled"><span>1</span></li>
                            <li class="disabled"><span>2</span></li>
                            <li class="disabled"><span>3</span></li>
                            <li class="disabled"><span>4</span></li>
                            <li class="disabled"><span>5</span></li>
                            <li class="active"><span>6</span></li>
                            <li class="disabled"><span>7</span></li>
                            <li class="disabled"><span>8</span></li>
                            <li class="disabled"><span>9</span></li>
                            <li class="disabled"><span>10</span></li>
                            <li class="disabled"><span>11</span></li>
                            <li>
                                <a href="#" data-form-action="${ACTION_SUBJECT}">
                                    <bean:message key="button.Proceed" />
                                    <i class="icon icon-angle-right"></i>
                                </a>
                            </li>
                        </ul>
                    </li>
                </ul>
            </div>
            <div class="tile-notification tile-notification-info">
                <bean:message key="mailing.wizard.MailingSubjectMsg"/>
                <button type="button" data-help="help_${helplanguage}/mailingwizard/step_06/MailingSubjectMsg.xml" class="icon icon-help"></button>
            </div>
            <div class="tile-content tile-content-forms">
                <div class="form-group">
                    <div class="col-sm-4">
                        <label class="control-label" for="emailSubject"><bean:message key="mailing.Subject"/></label>
                    </div>
                    <div class="col-sm-8">
                        <html:text styleId="emailSubject" styleClass="form-control" property="emailSubject" maxlength="99" size="42"/>
                    </div>
                </div>
            </div>
            <div class="tile-footer">
                <a href="#" class="btn btn-large pull-left" data-form-action="previous">
                    <i class="icon icon-angle-left"></i>
                    <span class="text"><bean:message key="button.Back"/></span>
                </a>
                <a href="#" class="btn btn-large btn-primary pull-right" data-form-action="${ACTION_SUBJECT}">
                    <span class="text"><bean:message key="button.Proceed"/></span>
                    <i class="icon icon-angle-right"></i>
                </a>
                <span class="clearfix"></span>
            </div>
        </div>
    </div>
</agn:agnForm>
