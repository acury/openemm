<%@ page language="java" contentType="text/html; charset=utf-8" errorPage="/error.do" %>
<%@ page import="com.agnitas.web.MailingBaseAction" %>
<%@ page import="org.agnitas.util.AgnUtils" %>
<%@ page import="com.agnitas.beans.ComAdmin" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="bean" uri="http://struts.apache.org/tags-bean" %>
<%@ taglib prefix="emm" uri="https://emm.agnitas.de/jsp/jsp/common" %>

<c:set var="ACTION_VIEW" value="<%= MailingBaseAction.ACTION_VIEW %>"/>
<c:set var="ACTION_CLONE_AS_MAILING" value="<%= MailingBaseAction.ACTION_CLONE_AS_MAILING %>"/>
<c:set var="ACTION_CONFIRM_DELETE" value="<%= MailingBaseAction.ACTION_CONFIRM_DELETE %>"/>
<c:set var="ACTION_CREATE_FOLLOW_UP" value="<%= MailingBaseAction.ACTION_CREATE_FOLLOW_UP %>"/>
<c:set var="ACTION_CONFIRM_UNDO" value="<%= MailingBaseAction.ACTION_CONFIRM_UNDO %>"/>
<c:set var="ACTION_MAILING_EXPORT" value="<%= MailingBaseAction.ACTION_MAILING_EXPORT %>"/>

<c:set var="workflowParams" value="${emm:getWorkflowParamsWithDefault(pageContext.request, param.workflowId)}" scope="page"/>
<c:set var="isWorkflowDriven" value="${not empty workflowParams and workflowParams.workflowId gt 0}" scope="page"/>

<c:set var="SESSION_CONTEXT_KEYNAME_ADMIN" value="<%= AgnUtils.SESSION_CONTEXT_KEYNAME_ADMIN %>"/>

<c:set var="company" value="<%= ((ComAdmin) session.getAttribute(AgnUtils.SESSION_CONTEXT_KEYNAME_ADMIN)).getCompany() %>"/>
<c:set var="admin" value="${sessionScope[SESSION_CONTEXT_KEYNAME_ADMIN]}"/>

<c:if test="${empty itemActionsSettings}">
    <%-- Instantiate if it doesn't exist yet --%>
    <emm:instantiate var="itemActionsSettings" type="java.util.LinkedHashMap" scope="request"/>
</c:if>


<emm:instantiate var="element" type="java.util.LinkedHashMap">
    <emm:instantiate var="dropDownItems" type="java.util.LinkedHashMap"/>

    <c:set target="${element}" property="btnCls" value="btn btn-secondary btn-regular dropdown-toggle"/>
    <c:set target="${element}" property="extraAttributes" value="data-toggle='dropdown'"/>
    <c:set target="${element}" property="iconBefore" value="icon-wrench"/>
    <c:set target="${element}" property="name">
        <bean:message key="action.Action"/>
    </c:set>
    <c:set target="${element}" property="iconAfter" value="icon-caret-down"/>
    <c:set target="${element}" property="dropDownItems" value="${dropDownItems}"/>

    <%-- Add dropdown items (actions) --%>

    <c:if test="${param.mailingId ne 0}">
    
            <%-- Mailing copy --%>

        <emm:ShowByPermission token="${param.isTemplate ? 'template.change' : 'mailing.change'}">
            <emm:instantiate var="option" type="java.util.LinkedHashMap">
                <c:set target="${dropDownItems}" property="1" value="${option}"/>
                <c:set target="${option}" property="url">
                    <c:url value="/mailingbase.do">
                        <c:param name="action" value="${ACTION_CLONE_AS_MAILING}"/>
                        <c:param name="mailingID" value="${param.mailingId}"/>
                    </c:url>
                </c:set>
                <c:set target="${option}" property="icon" value="icon-copy"/>
                <c:set target="${option}" property="name">
                    <bean:message key="button.Copy"/>
                </c:set>
            </emm:instantiate>
        </emm:ShowByPermission>
    
        <%-- Return to campaign --%>

        <c:if test="${isWorkflowDriven}">
            <emm:instantiate var="option" type="java.util.LinkedHashMap">
                <c:set target="${dropDownItems}" property="0" value="${option}"/>
                <c:set target="${option}" property="url">
                    <c:url value="/workflow/${workflowParams.workflowId}/view.action">
                        <c:if test="${not empty workflowParams.workflowForwardParams}">
                            <c:param name="forwardParams" value="${workflowParams.workflowForwardParams};elementValue=${param.mailingId}"/>
                        </c:if>
                    </c:url>
                </c:set>
                <c:set target="${option}" property="icon" value="icon-reply"/>
                <c:set target="${option}" property="name">
                    <bean:message key="mailing.button.toCampaign"/>
                </c:set>
            </emm:instantiate>
        </c:if>

        <%@include file="/WEB-INF/jsp/mailing/actions-dropdown-followup.jspf" %>

		<%-- Mailing JSON export --%>
		<emm:ShowByPermission token="mailing.export">
			<emm:instantiate var="option" type="java.util.LinkedHashMap">
				<c:set target="${dropDownItems}" property="5" value="${option}" />
				<c:set target="${option}" property="url">
					<c:url value="/mailingbase.do">
						<c:param name="action" value="${ACTION_MAILING_EXPORT}" />
						<c:param name="mailingID" value="${param.mailingId}" />
					</c:url>
				</c:set>
				<c:set target="${option}" property="icon" value="icon-database" />
				<c:set target="${option}" property="name">
					<bean:message
						key="${param.isTemplate ? 'template.export' : 'mailing.export'}" />
				</c:set>
				<c:set target="${option}" property="extraAttributes"
					value="data-prevent-load=''" />
			</emm:instantiate>
		</emm:ShowByPermission>

        <%-- Mailing edit undo --%>

        <emm:ShowByPermission token="${param.isTemplate ? 'template.change' : 'mailing.change'}">
            <c:if test="${param.isMailingUndoAvailable}">
                <emm:instantiate var="option" type="java.util.LinkedHashMap">
                    <c:set target="${dropDownItems}" property="4" value="${option}"/>
                    <c:set target="${option}" property="url">
                        <c:url value="/mailingbase.do">
                            <c:param name="action" value="${ACTION_CONFIRM_UNDO}"/>
                            <c:param name="mailingID" value="${param.mailingId}"/>
                        </c:url>
                    </c:set>
                    <c:set target="${option}" property="icon" value="icon-reply"/>
                    <c:set target="${option}" property="extraAttributes" value=" data-confirm=''"/>
                    <c:set target="${option}" property="name">
                        <bean:message key="button.Undo"/>
                    </c:set>
                </emm:instantiate>
            </c:if>
        </emm:ShowByPermission>


        
                <%-- Mailing delete --%>

        <emm:ShowByPermission token="${param.isTemplate ? 'template.delete' : 'mailing.delete'}">
            <emm:instantiate var="option" type="java.util.LinkedHashMap">
                <c:set target="${dropDownItems}" property="2" value="${option}"/>
                <c:set target="${option}" property="url">
                    <c:url value="/mailingbase.do">
                        <c:param name="action" value="${ACTION_CONFIRM_DELETE}"/>
                        <c:param name="mailingID" value="${param.mailingId}"/>
                    </c:url>
                </c:set>
                <c:set target="${option}" property="icon" value="icon-trash-o"/>
                <c:set target="${option}" property="extraAttributes" value=" data-confirm=''"/>
                <c:set target="${option}" property="name">
                    <bean:message key="button.Delete"/>
                </c:set>
            </emm:instantiate>
        </emm:ShowByPermission>
        
    </c:if>
    

    <%-- Do not show a dropdown when it's empty --%>

    <c:if test="${not empty dropDownItems}">
        <c:set target="${itemActionsSettings}" property="${param.elementIndex}" value="${element}"/>
    </c:if>
</emm:instantiate>
