<%@ page contentType="text/html; charset=utf-8"%>
<%@ taglib prefix="mvc" uri="https://emm.agnitas.de/jsp/jsp/spring" %>

<%--@elvariable id="upsellingInfoUrl" type="String"--%>

<div class="tile" data-sizing="container">
    <div class="tile-header" data-sizing="top">
        <h2 class="headline">
            <i class="icon icon-bolt"></i>
            <mvc:message code="ReferenceTables"/>
        </h2>
    </div>

    <div class="upselling-content reference-tables-upselling" data-sizing="scroll">

        <div class="upselling-headline">
            <h3 class="upselling-title"><mvc:message code="ReferenceTables"/></h3>
            <h1 class="upselling-header"><mvc:message code="referenceTables.teaser.header"/></h1>
        </div>
        <div class="upselling-desc">
            <p><mvc:message code="referenceTables.teaser.text"/></p>
			
			
			<c:choose>
				<c:when test="${aLocale eq 'de_DE'}">
					<a href="https://www.agnitas.de/e-marketing-manager/premium-features/smart-data/" class="more-info-btn" target="_blank">
				</c:when>
				<c:otherwise>
					<a href="https://www.agnitas.de/en/e-marketing_manager/premium-features/smart-data/" class="more-info-btn" target="_blank">
				</c:otherwise>
			</c:choose>
                <mvc:message code="general.upselling.information"/>
            </a>
        </div>

    </div>

    <div class="tile-footer" data-sizing="bottom">
        <a href="javascript:void(0);" class="btn btn-large pull-left" onclick="history.back(); return false;">
            <i class="icon icon-angle-left"></i>
            <span class="text"><mvc:message code="button.Back" /></span>
        </a>
    </div>
</div>
