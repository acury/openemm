<%@ page language="java" contentType="text/html; charset=utf-8" errorPage="/error.do" %>
<%@ page import="com.agnitas.web.MailingBaseAction" %>
<%@ page import="org.agnitas.util.AgnUtils"%>
<%@ taglib uri="https://emm.agnitas.de/jsp/jstl/tags" prefix="agn" %>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic" %>
<%@ taglib uri="http://displaytag.sf.net" prefix="display" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>
<%@ taglib prefix="emm" uri="https://emm.agnitas.de/jsp/jsp/common" %>
<c:set var="ACTION_NEW" value="<%= MailingBaseAction.ACTION_NEW %>"/>
<emm:setAbsolutePath var="absoluteImagePath" path="${emmLayoutBase.imagesURL}"/>

<c:set var="SESSION_CONTEXT_KEYNAME_ADMIN" value="<%= AgnUtils.SESSION_CONTEXT_KEYNAME_ADMIN%>" />

<%--@elvariable id="admin" type="com.agnitas.beans.Admin"--%>
<%--@elvariable id="localeDatePattern" type="java.lang.String"--%>

<c:set var="admin" value="${sessionScope[SESSION_CONTEXT_KEYNAME_ADMIN]}" />

<c:set var="aZone" value="${admin.adminTimezone}" />
<c:set var="adminLocale" value="${admin.locale}" />

<c:set var="aLocale" value="${admin.getLocale()}" />

<agn:agnForm action="/mailingbase.do?action=${ACTION_NEW}&mailingID=0&isTemplate=false" method="GET" data-form="resource">
    <html:hidden property="keepForward" value="${not empty workflowId and workflowId gt 0 ? true : false}"/>

    <div class="tile" data-sizing="container">
        <div class="tile-header" data-sizing="top">
            <h2 class="headline"><bean:message key="Templates"/></h2>

            <ul class="tile-header-nav">
                <li>
                    <a href="#" data-toggle-tab="#tab-templates-list"><bean:message key="default.list"/></a>
                </li>
                <li class="active">
                    <a href="#" data-toggle-tab="#tab-templates-preview"><bean:message key="default.Preview"/></a>
                </li>
            </ul>

            <ul class="tile-header-actions">
                <emm:HideByPermission token="mailing.settings.hide">
                <li>
                    <button type="button" class="btn btn-primary btn-large" data-form-submit data-form-set="templateID: 0">
                        <span class="text"><bean:message key="button.template.without"/></span>
                        <i class="icon icon-angle-right"></i>
                    </button>
                </li>
                </emm:HideByPermission>
            </ul>
        </div>

        <div class="tile-content">
            <div id="tab-templates-list" class="hidden" data-sizing="scroll">
                <ul class="link-list">
                    <c:forEach var="template" items="${mailingBaseForm.templateMailingBases}">
                        <c:set value="${template.creationDate}" var="creationDateTimeStamp" />
                        <fmt:parseDate value="${creationDateTimeStamp}" var="creationDateParsed" pattern="yyyy-MM-dd HH:mm:ss" />
                        <fmt:formatDate value="${creationDateParsed}" var="creationDateFormatted" pattern="${localeDatePattern}" timeZone="${aZone}" />
                        
                        <li>
                            <a href="#" data-form-submit  class="link-list-item" data-form-set="templateID: ${template.id}" data-layout-id="${template.id}" data-action="select-layout">
                                <p class="headline">${template.shortname}</p>
                                <p class="description">
                                    <span data-tooltip="<bean:message key="default.creationDate"/>">
                                        <i class="icon icon-calendar-o"></i>
                                        <strong>${creationDateFormatted}</strong>
                                    </span>
                                </p>
                            </a>
                        </li>
                    </c:forEach>
                </ul>
            </div>

            <div id="tab-templates-preview" class="card-panel" data-sizing="scroll">
                <div class="row flexbox">
                    <c:forEach var="template" items="${mailingBaseForm.templateMailingBases}">
                        <c:set value="${template.creationDate}" var="creationDateTimeStamp" />
                        <fmt:parseDate value="${creationDateTimeStamp}" var="creationDateParsed" pattern="yyyy-MM-dd HH:mm:ss" />
                        <fmt:formatDate value="${creationDateParsed}" var="creationDateFormatted" pattern="${localeDatePattern}" timeZone="${aZone}" />
                        
                        <div class="col-xs-6 col-sm-4 col-md-3 card-content">
                            <a href="#" class="card old-cards" data-form-submit data-action="select-layout" data-form-set="templateID: ${template.id}" data-layout-id="${template.id}" data-action="select-layout">
                                <c:choose>
                                    <c:when test="${template.onlyPostType}">
                                        <c:url var="previewImageSrc" value="assets/core/images/facelift/post_thumbnail.jpg"/>
                                    </c:when>
                                    <c:when test="${template.previewComponentId eq 0}">
                                        <c:url var="previewImageSrc" value="/assets/core/images/facelift/no_preview.png"/>
                                    </c:when>
                                    <c:otherwise>
                                        <c:url var="previewImageSrc" value="/sc?compID=${template.previewComponentId}"/>
                                    </c:otherwise>
                                </c:choose>
                                <img class="card-image" src="${previewImageSrc}"/>
                                <div class="card-body">
                                    <strong class="headline">
                                            ${template.shortname}
                                    </strong>

                                    <p class="description">
                                    <span data-tooltip="<bean:message key="default.creationDate"/>">
                                        <i class="icon icon-calendar-o"></i>
                                        <strong>${creationDateFormatted}</strong>
                                    </span>
                                    </p>
                                </div>
                            </a>
                        </div>
                    </c:forEach>
                </div>
            </div>
        </div>

        <div class="tile-footer" data-sizing="bottom">
            <emm:HideByPermission token="mailing.settings.hide">
                <div class="btn-group pull-right">
                    <button type="button" class="btn btn-primary btn-large" data-form-submit data-form-set="templateID: 0">
                        <span class="text"><bean:message key="button.template.without"/></span>
                        <i class="icon icon-angle-right"></i>
                    </button>
                </div>
            </emm:HideByPermission>
        </div>

    </div>
</agn:agnForm>
