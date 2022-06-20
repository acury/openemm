/*

    Copyright (C) 2022 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package com.agnitas.mailing.autooptimization.web;

import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.Hashtable;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.agnitas.stat.CampaignStatEntry;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.actions.DispatchAction;

import com.agnitas.mailing.autooptimization.beans.ComOptimization;
import com.agnitas.mailing.autooptimization.service.ComOptimizationService;
import com.agnitas.mailing.autooptimization.service.ComOptimizationStatService;
import com.agnitas.mailing.autooptimization.web.forms.ComOptimizationAjaxForm;

public class ComOptimizationAjaxAction extends DispatchAction {

	private static final transient Logger logger = LogManager.getLogger( ComOptimizationAjaxAction.class);
	
	private ComOptimizationService optimizationService;
	private ComOptimizationStatService optimizationStatService;
	
	/* 
	 * TODO Disabled by EMM-7355.
	 * 
	 * When no further usage detected, remove this method and ajaxtags-....jar
	 * 
	public ActionForward groups(ActionMapping mapping, ActionForm form,
			HttpServletRequest request, HttpServletResponse response)
	throws Exception {
		
		ComOptimizationAjaxForm optimizationAjaxForm = (ComOptimizationAjaxForm) form;
		int optimizationID = optimizationAjaxForm.getOptimizationID();
		int companyID = optimizationAjaxForm.getCompanyID();
		int campaignID = optimizationAjaxForm.getCampaignID();
		
		ComOptimization optimization = null;
		if( optimizationID == 0) {
			optimization = new ComOptimizationImpl();
			optimization.setCompanyID(companyID);
			optimization.setCampaignID(campaignID);
			
		} else {
			optimization = optimizationService.get(optimizationID, companyID);
		}
				
		List<Integer> excludeList = optimizationAjaxForm.getExcludeMailingIDs();
		
		List<SelectOption> mailingSelect =  optimizationService.getTestMailingList(optimization, excludeList);
		
		AjaxXmlBuilder xmlBuilder = new AjaxXmlBuilder();
		for( SelectOption option: mailingSelect) {
			xmlBuilder.addItem(option.getText(),option.getValue());
		}
		
		// Set content to xml
	    response.setContentType("application/xml");
	    response.setHeader("Cache-Control", "no-cache");
	    try (PrintWriter pw = response.getWriter()) {
	    	pw.write(xmlBuilder.toString());
	    }
		
		return null;
	}
	*/
	
	public ActionForward splits(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response)
	throws Exception {
		
		String[] allGroups = {"group1","group2","group3","group4","group5"};
		
		ComOptimizationAjaxForm optimizationAjaxForm = (ComOptimizationAjaxForm) form;
		int companyID = optimizationAjaxForm.getCompanyID();
		String splitType = optimizationAjaxForm.getSplitType();

		int groupsCount = optimizationService.getSplitNumbers(companyID, splitType) - 1;

		String groupsAsString = "";
		if (groupsCount > 0 && groupsCount <= allGroups.length) {
			groupsAsString = StringUtils.join(allGroups, ';', 0, groupsCount);
		} else {
			logger.error("Invalid split groups count: " + groupsCount);
		}

		// Set content to plain text
	    response.setContentType("text/plain");
	    response.setHeader("Cache-Control", "no-cache");
	    try (PrintWriter pw = response.getWriter()) {
	    	pw.write(groupsAsString);
	    }
		
		return null;
	}
	
	
	public ActionForward getStats(ActionMapping mapping, ActionForm form,
			HttpServletRequest request, HttpServletResponse response) {
		
		if (logger.isInfoEnabled()) {
			logger.info( "Starting generation of statistics");
		}
		
		ComOptimizationAjaxForm optimizationAjaxForm = (ComOptimizationAjaxForm) form;
		int companyID = optimizationAjaxForm.getCompanyID();
		int optimizationID = optimizationAjaxForm.getOptimizationID();
		
		ComOptimization optimization = optimizationService.get(optimizationID, companyID);
		
		Hashtable<Integer,CampaignStatEntry> stats = optimizationStatService.getStat(optimization);
		Enumeration<Integer> keys= stats.keys();
		
		request.setAttribute("evalType", optimization.getEvalType());
		request.setAttribute("stats", stats);
		request.setAttribute("keys", keys);
		response.setHeader("Cache-Control", "no-cache");

		if (logger.isInfoEnabled()) {
			logger.info( "Finished generation of statistics");
		}
		
		return mapping.findForward("stats");
	}
	
	@Override
	public ActionForward unspecified(ActionMapping mapping, ActionForm form,
			HttpServletRequest request, HttpServletResponse response)
			throws Exception {
		
		/*
		 * TODO Disabled by EMM-7355.
		 * 
		return groups(mapping, form, request, response);
		 */
		
		logger.fatal("Called com.agnitas.mailing.autooptimization.web.ComOptimizationAjaxAction.unspecified() - has been disabled by EMM-7355");
		
		return null;
	}
	
	
	public void setOptimizationService(ComOptimizationService optimizationService) {
		this.optimizationService = optimizationService;
	}

	public ComOptimizationService getOptimizationService() {
		return optimizationService;
	}

	public void setOptimizationStatService(
			ComOptimizationStatService optimizationStatService) {
		this.optimizationStatService = optimizationStatService;
	}
}
