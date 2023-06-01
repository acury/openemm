/*

    Copyright (C) 2022 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package com.agnitas.web;

import com.agnitas.beans.Admin;
import com.agnitas.beans.ComTarget;
import com.agnitas.beans.DynamicTag;
import com.agnitas.beans.Mailing;
import com.agnitas.beans.MailingContentType;
import com.agnitas.beans.MailingWizardOption;
import com.agnitas.beans.Mediatype;
import com.agnitas.beans.MediatypeEmail;
import com.agnitas.beans.TargetLight;
import com.agnitas.dao.CampaignDao;
import com.agnitas.emm.core.Permission;
import com.agnitas.emm.core.admin.service.AdminService;
import com.agnitas.emm.core.mailing.service.MailingService;
import com.agnitas.emm.core.mailinglist.service.MailinglistService;
import com.agnitas.emm.core.mailinglist.service.MailinglistApprovalService;
import com.agnitas.emm.core.target.TargetExpressionUtils;
import com.agnitas.emm.core.target.eql.emm.querybuilder.QueryBuilderFilterListBuilder;
import com.agnitas.emm.core.target.form.TargetEditForm;
import com.agnitas.emm.core.target.service.ComTargetService;
import com.agnitas.emm.core.target.web.util.EditorContentSynchronizer;
import com.agnitas.emm.core.workflow.beans.Workflow;
import com.agnitas.emm.core.workflow.dao.ComWorkflowDao;
import com.agnitas.emm.core.workflow.service.ComWorkflowService;
import com.agnitas.service.AgnTagService;
import com.agnitas.util.preview.PreviewImageService;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.agnitas.actions.EmmAction;
import org.agnitas.beans.DynamicTagContent;
import org.agnitas.beans.MailingComponent;
import org.agnitas.beans.MailingComponentType;
import org.agnitas.beans.Mailinglist;
import org.agnitas.beans.MediaTypeStatus;
import org.agnitas.beans.factory.DynamicTagContentFactory;
import org.agnitas.beans.factory.MailingComponentFactory;
import org.agnitas.beans.factory.MailingFactory;
import org.agnitas.dao.EmmActionDao;
import org.agnitas.dao.MailingDao;
import org.agnitas.emm.core.commons.util.ConfigService;
import org.agnitas.emm.core.commons.util.ConfigValue;
import org.agnitas.emm.core.mailing.service.CopyMailingService;
import org.agnitas.emm.core.mailing.service.MailingModel;
import org.agnitas.emm.core.target.exception.UnknownTargetGroupIdException;
import org.agnitas.service.UserActivityLogService;
import org.agnitas.util.AgnUtils;
import org.agnitas.util.DynTagException;
import org.agnitas.util.HttpUtils;
import org.agnitas.web.MailingWizardForm;
import org.agnitas.web.StrutsDispatchActionBase;
import org.agnitas.web.forms.WorkflowParametersHelper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.apache.struts.action.ActionRedirect;
import org.apache.struts.upload.FormFile;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Vector;
import java.util.stream.Collectors;

import static com.agnitas.emm.core.workflow.service.util.WorkflowUtils.updateForwardParameters;
import static org.agnitas.web.forms.WorkflowParametersHelper.WORKFLOW_KEEP_FORWARD;

/**
 * Implementation of <strong>Action</strong> that handles Mailings
 */
public class MailingWizardAction extends StrutsDispatchActionBase {
	private static final Logger logger = LogManager.getLogger(MailingWizardAction.class);

	public static final String ACTION_START = "start";
	public static final String ACTION_NAME = "name";
	public static final String ACTION_TEMPLATE = "template";
	public static final String ACTION_TYPE = "type";
	public static final String ACTION_SENDADDRESS = "sendaddress";
	public static final String ACTION_MAILTYPE = "mailtype";
	public static final String ACTION_SUBJECT = "subject";
	public static final String ACTION_TARGET_VIEW = "targetView";
	public static final String ACTION_TARGET = "target";
	public static final String ACTION_TEXTMODULE = "textmodule";
	public static final String ACTION_TEXTMODULE_ADD = "textmodule_add";
    public static final String ACTION_TEXTMODULE_SAVE = "textmodule_save";
	public static final String ACTION_MEASURELINKS = "links";
	public static final String ACTION_MEASURELINK = "link";
    public static final String ACTION_TO_ATTACHMENT = "to_attachment";
	public static final String ACTION_ATTACHMENT = "attachment";
	public static final String ACTION_ATTACHMENT_DOWNLOAD = "attachmentDownload";
	public static final String ACTION_FINISH = "finish";
	public static final String ACTION_NEW_TARGET = "newTarget";
	public static final String ACTION_ADD_TARGET = "addTarget";

	/** DAO accessing mailinglists. */
    protected MailingFactory mailingFactory;

    /** DAO accessing mailings. */
    protected MailingDao mailingDao;
    protected MailingService mailingService;
    protected MailingComponentFactory mailingComponentFactory;
    protected DynamicTagContentFactory dynamicTagContentFactory;

    /** DAO accessing campaigns. */
    protected CampaignDao campaignDao;

    protected ComTargetService targetService;

    /** DAO accessing EMM actions. */
    protected EmmActionDao emmActionDao;

	protected ConfigService configService;
	protected ComWorkflowDao workflowDao;
	protected ComWorkflowService workflowService;
	protected MailinglistService mailinglistService;
	protected AgnTagService agnTagService;
    protected MailinglistApprovalService mailinglistApprovalService;
    protected PreviewImageService previewImageService;

	private UserActivityLogService userActivityLogService;

	protected CopyMailingService copyMailingService;
	protected QueryBuilderFilterListBuilder filterListBuilder;
	protected AdminService adminService;

	protected EditorContentSynchronizer editorContentSynchronizer;

    // --------------------------------------------------------- Public Methods

    /**
     * Initialization of mailing wizard. If current user is logged in - forwards to action input forward.
     *
     * @param mapping The ActionMapping used to select this instance
     * @param form data for the action filled by the jsp
     * @param req request from jsp
     * @param res response
     * @return destination specified in struts-config.xml to forward to next jsp
     * @throws Exception if a exception occurs
     */
	public ActionForward init(ActionMapping mapping, ActionForm form,
			HttpServletRequest req, HttpServletResponse res) throws Exception {
		if (!AgnUtils.isUserLoggedIn(req)) {
			return mapping.findForward("logon");
		}
		updateForwardParameters(req, true);
		getExtendedRequestAttrs(req);

		Admin admin = AgnUtils.getAdmin(req);

		List<MailingWizardOption> allowedOptions = Arrays.stream(MailingWizardOption.values())
				.filter(o -> admin.permissionAllowed(o.getRequiredPermission()))
				.collect(Collectors.toList());

		boolean canSelectOptionAutomatically = allowedOptions.size() == 1;
		if (canSelectOptionAutomatically) {
            return autoSelectOption(allowedOptions.get(0), req);
		}

		return mapping.getInputForward();
	}

	private ActionForward autoSelectOption(MailingWizardOption optionToSelect, HttpServletRequest req) {
        ActionRedirect actionRedirect = new ActionRedirect(optionToSelect.getRedirectionUrl());

        if (optionToSelect.isNeedKeepForward()) {
            boolean keepForward = false;

            String workflowIdParam = req.getParameter(WorkflowParametersHelper.WORKFLOW_ID);

            if (StringUtils.isNoneBlank(workflowIdParam)) {
                int workflowId = Integer.parseInt(workflowIdParam);
                keepForward = workflowId > 0;
            }

            actionRedirect.addParameter(WORKFLOW_KEEP_FORWARD, keepForward);
        }

        return actionRedirect;
    }

    protected void getExtendedRequestAttrs(HttpServletRequest req) {
        // Do nothing
    }

	/**
     * If the user is not logged in - forwards to login page<br>
     * Gets list of mailinglists for current company. If there are no mailinglists existing - adds error message and
     * forwards to input forward ("mwStart").<br>
     * In other case creates mailing using mailingFactory; initializes mailing (creates default html- and text-template
     * components and mediatype); sets mailing template id if templateID parameter existed in request; sets mailing
     * default target mode; sets mailing into Form; forwards to "next" (currently that is the page for entering mailing
     * name and description)
     *
     * @param mapping The ActionMapping used to select this instance
     * @param form data for the action filled by the jsp
     * @param request request from jsp
     * @param response response
     * @return destination specified in struts-config.xml to forward to next jsp
     * @throws Exception if a exception occurs
     */
	public ActionForward start(ActionMapping mapping, ActionForm form,
			HttpServletRequest request, HttpServletResponse response) throws Exception {
		if (!AgnUtils.isUserLoggedIn(request)) {
			return mapping.findForward("logon");
		}

		List<Mailinglist> mlists = mailinglistApprovalService.getEnabledMailinglistsForAdmin(AgnUtils.getAdmin(request));

		if (mlists.size() <= 0) {
			ActionMessages	errors = new ActionMessages();

			errors.add(ActionMessages.GLOBAL_MESSAGE, new ActionMessage("error.mailing.noMailinglist"));
			saveErrors(request, errors);
			return mapping.getInputForward();
		}

		//populate mailing data with info from workflow
		updateForwardParameters(request, true);

		MailingWizardForm aForm = (MailingWizardForm) form;
		Mailing mailing = mailingFactory.newMailing();

		mailing.init(AgnUtils.getCompanyID(request), getApplicationContext(request));
        Map<String, String> map = AgnUtils.getReqParameters(request);
        String templateIDString = map.get("templateID");
        if (StringUtils.isNotEmpty(templateIDString)) {
            mailing.setMailTemplateID(NumberUtils.toInt(templateIDString));
        }
        mailing.setTargetMode(Mailing.TARGET_MODE_AND);

		setMailingWorkflowParameters(request, mailing);
		aForm.setMailing(mailing);
		aForm.setMailingContentType(MailingContentType.advertising);

		return mapping.findForward("next");
	}

	/**
     * If the user is not logged in - forwards to login page.
     * Sets "isTemplate" mailing property to false. Loads list of company's templates to request.
     * Forwards to "next" (currently the page for choosing mailing template)
     *
     * @param mapping The ActionMapping used to select this instance
     * @param form data for the action filled by the jsp
     * @param req request from jsp
     * @param res response
     * @return destination specified in struts-config.xml to forward to next jsp
     * @throws Exception if a exception occurs
     */
	public ActionForward name(ActionMapping mapping, ActionForm form,
			HttpServletRequest req, HttpServletResponse res) throws Exception {
		if (!AgnUtils.isUserLoggedIn(req)) {
			return mapping.findForward("logon");
		}

		MailingWizardForm aForm = (MailingWizardForm) form;
		Mailing mailing = aForm.getMailing();

		if (mailing != null) {
			mailing.setMailingContentType(aForm.getMailingContentType());
			mailing.setIsTemplate(false);
		}
        prepareTemplatePage(req);

        return mapping.findForward("next");
	}

	/**
     * If the user is not logged in - forwards to login page<br>
     * If template is selected: loads template from database and copies template data to mailing. <br>
     * If template is not selected - sets status active to first mailing mediatype (if first mailing mediatype is
     * null - creates it)<br>
     * Forwards to "next" (currently the page for selecting mailing type)
     *
     * @param mapping The ActionMapping used to select this instance
     * @param form data for the action filled by the jsp
     * @param req request from jsp
     * @param res response
     * @return destination specified in struts-config.xml to forward to next jsp
     * @throws Exception if a exception occurs
     */
	public ActionForward template(ActionMapping mapping, ActionForm form, HttpServletRequest req, HttpServletResponse res) throws Exception {
		if (!AgnUtils.isUserLoggedIn(req)) {
			return mapping.findForward("logon");
		}
		final int companyId = AgnUtils.getCompanyID(req);

		MailingWizardForm aForm = (MailingWizardForm) form;
		Mailing mailing = aForm.getMailing();

		Integer workflowId = (Integer) req.getSession().getAttribute("workflowId");
		Date planDate = null;
		if (workflowId != null) {
			Workflow workflow = workflowDao.getWorkflow(workflowId, companyId);
			planDate = workflow.getGeneralStartDate();
		}

		if (aForm.getMailing().getMailTemplateID() == 0) {
			mailing.setIsTemplate(false);

			Map<Integer, Mediatype> mediatypes = mailing.getMediatypes();

			Mediatype type = mediatypes.get(0);
			if (type != null) {
				type.setStatus(MediaTypeStatus.Active.getCode());
			} else {
				// should not happen
				MediatypeEmail paramEmail = mailing.getEmailParam();

				paramEmail.setCharset("iso-8859-1");
				paramEmail.setMailFormat(MailingModel.Format.ONLINE_HTML.getCode());
				paramEmail.setLinefeed(0);
				paramEmail.setPriority(1);
				paramEmail.setStatus(MediaTypeStatus.Active.getCode());
				mediatypes.put(0, paramEmail);
			}
			mailing.setPlanDate(planDate);
			setMailingWorkflowParameters(req, mailing);
			aForm.clearEmailData();
		} else {
			Mailing template = mailingDao.getMailing(aForm.getMailing().getMailTemplateID(), companyId);

			if (template != null) {
				int newMailingID = copyMailingService.copyMailing(companyId, aForm.getMailing().getMailTemplateID(), companyId, aForm.getMailing().getShortname(), aForm.getMailing().getDescription());
				Mailing newMailing = mailingDao.getMailing(newMailingID, companyId);
				newMailing.setIsTemplate(false);
				newMailing.setPlanDate(planDate);
				newMailing.setMailingContentType(aForm.getMailingContentType());
				newMailing.setPlanDate(planDate);
				newMailing.setMailTemplateID(aForm.getMailing().getMailTemplateID());
				setMailingWorkflowParameters(req, newMailing);

                Map<Integer, Mediatype> mediatypes = newMailing.getMediatypes();
                Mediatype type = mediatypes.get(0);
				if (type != null) {
					type.setStatus(MediaTypeStatus.Active.getCode());
				}
				aForm.setMailing(newMailing);

				MediatypeEmail param = newMailing.getEmailParam();
				// param.setStatus(MediaTypeStatus.Active.getCode());
				aForm.setEmailSubject(param.getSubject());
				aForm.setEmailFormat(param.getMailFormat());
				aForm.setEmailOnepixel(param.getOnepixel());
				aForm.setSenderEmail(param.getFromEmail());
				aForm.setSenderFullname(param.getFromFullname());
				aForm.setReplyEmail(param.getReplyEmail());
				aForm.setReplyFullname(param.getReplyFullname());

				aForm.setMailing(newMailing);
			}
		}
		return mapping.findForward("next");
	}

	protected void prepareTemplatePage(HttpServletRequest req) {
        List<Mailing> templates = mailingService.getTemplates(AgnUtils.getAdmin(req));
        req.setAttribute("templates", templates);
    }

    /**
     * If the user is not logged in - forwards to login page.
     * Forwards to "next" (currently the page for entering sender-address, sender-name,
     * replyto-address and reply-to name)
     *
     * @param mapping The ActionMapping used to select this instance
     * @param form data for the action filled by the jsp
     * @param req request from jsp
     * @param res response
     * @return destination specified in struts-config.xml to forward to next jsp
     * @throws Exception if a exception occurs
     */

	public ActionForward type(ActionMapping mapping, ActionForm form, HttpServletRequest req, HttpServletResponse res) throws Exception {
		if (!AgnUtils.isUserLoggedIn(req)) {
			return mapping.findForward("logon");
		}
		return mapping.findForward("next");
	}


    /**
     * If the user is not logged in - forwards to login page.
     * Saves the address information(sender-address, sender-name, replyto-address and reply-to name) into the mailing
     * email param. Forwards to "next" (currently the page for selecting mail format)
     *
     * @param mapping The ActionMapping used to select this instance
     * @param form data for the action filled by the jsp
     * @param req request from jsp
     * @param res response
     * @return destination specified in struts-config.xml to forward to next jsp
     * @throws Exception if a exception occurs
     */
	public ActionForward sendaddress(ActionMapping mapping, ActionForm form,
			HttpServletRequest req, HttpServletResponse res) throws Exception {
		if (!AgnUtils.isUserLoggedIn(req)) {
			return mapping.findForward("logon");
		}
		MailingWizardForm aForm = (MailingWizardForm) form;
		Mailing mailing = aForm.getMailing();

		MediatypeEmail param = mailing.getEmailParam();
		param.setFromEmail(aForm.getSenderEmail());
		param.setFromFullname(aForm.getSenderFullname());
		param.setReplyEmail(aForm.getReplyEmail());
		param.setReplyFullname(aForm.getReplyFullname());

		return mapping.findForward("next");
	}

    /**
     * If the user is not logged in - forwards to login page.
     * Saves the subject into mailing email param. Builds mailing dependencies.
     * Loads campaigns, mailinglists and target-groups to request. Forwards to "next" (currently to the page for
     * setting mailinglist, target groups etc.)
     *
     * @param mapping The ActionMapping used to select this instance
     * @param form data for the action filled by the jsp
     * @param req request from jsp
     * @param res response
     * @return destination specified in struts-config.xml to forward to next jsp
     * @throws Exception if a exception occurs
     */
	public ActionForward subject(ActionMapping mapping, ActionForm form,
			HttpServletRequest req, HttpServletResponse res) throws Exception {
		if (!AgnUtils.isUserLoggedIn(req)) {
			return mapping.findForward("logon");
		}
		MailingWizardForm aForm = (MailingWizardForm) form;
		Mailing mailing = aForm.getMailing();
		MediatypeEmail param = mailing.getEmailParam();

		param.setSubject(aForm.getEmailSubject());
		aForm.getMailing().buildDependencies(true,
				getApplicationContext(req));

		aForm.setTargetGroups(mailing.getTargetGroups());

		return targetView(mapping, form, req, res);
	}

	/**
     * If the user is not logged in - forwards to login page.
     * Loads campaigns, mailinglists and target-groups to request. Forwards to "targetView"
     * (currently the page for setting mailinglist, target groups etc.)
     *
     * @param mapping The ActionMapping used to select this instance
     * @param form data for the action filled by the jsp
     * @param req request from jsp
     * @param res response
     * @return destination specified in struts-config.xml to forward to next jsp
     * @throws Exception if a exception occurs
     */
	public ActionForward targetView(ActionMapping mapping, ActionForm form,
			HttpServletRequest req, HttpServletResponse res) throws Exception {
		if (!AgnUtils.isUserLoggedIn(req)) {
			return mapping.findForward("logon");
		}

        prepareTargetPage(req, form);

		return mapping.findForward("targetView");
	}

    /**
     * If the user is not logged in - forwards to login page.
     * Saves the mail format to mailing email param. Resets html template if mailing format is "Text". Forwards to
     * "next" (the page for entering mailing subject)
     *
     * @param mapping The ActionMapping used to select this instance
     * @param form data for the action filled by the jsp
     * @param req request from jsp
     * @param res response
     * @return destination specified in struts-config.xml to forward to next jsp
     * @throws Exception if a exception occurs
     */
	public ActionForward mailtype(ActionMapping mapping, ActionForm form,
			HttpServletRequest req, HttpServletResponse res) throws Exception {
		if (!AgnUtils.isUserLoggedIn(req)) {
			return mapping.findForward("logon");
		}
		MailingWizardForm aForm = (MailingWizardForm) form;
		MediatypeEmail param = aForm.getMailing().getEmailParam();

		int mailFormat = aForm.getEmailFormat();
		param.setMailFormat(mailFormat);
		if (mailFormat == MailingModel.Format.TEXT.getCode()) {
			param.setHtmlTemplate("");
			aForm.getMailing().getHtmlTemplate().setEmmBlock("", "text/html");
		} else {
            param.setHtmlTemplate("[agnDYN name=\"HTML-Version\"/]");
            int templateId = aForm.getMailing().getMailTemplateID();
            if (templateId == 0) {
                aForm.getMailing().getHtmlTemplate().setEmmBlock("[agnDYN name=\"HTML-Version\"/]", "text/html");
            } else {
                Mailing template = mailingDao.getMailing(templateId, AgnUtils.getCompanyID(req));
                aForm.getMailing().getHtmlTemplate().setEmmBlock(template.getHtmlTemplate().getEmmBlock(), "text/html");
            }
        }

		return mapping.findForward("next");
	}

	/**
     * Loads required data for target page (Mailinglists, Campaigns and Target groups) into request.
     *
	 * @param request request from jsp
	 * @param form
	 */
	public void prepareTargetPage(HttpServletRequest request, ActionForm form) {
		final Admin admin = AgnUtils.getAdmin(request);
		final int companyId = admin.getCompanyID();
		final boolean excludeHiddenTargets = !admin.permissionAllowed(Permission.MAILING_CONTENT_SHOW_EXCLUDED_TARGETGROUPS);
        MailingWizardForm mailingWizardForm = (MailingWizardForm) form;

		List<TargetLight> targetList = targetService.getTargetLights(admin, false, true, false, excludeHiddenTargets);

		request.setAttribute("targets", targetList);
		request.setAttribute("mailinglists", mailinglistApprovalService.getEnabledMailinglistsForAdmin(admin));
		request.setAttribute("campaigns", campaignDao.getCampaignList(companyId, "LOWER(shortname)", 1));
		request.setAttribute("targetComplexities", targetService.getTargetComplexities(companyId));
        setComplexTargetExpressionRequestAttr(request, admin, mailingWizardForm);
        mailingWizardForm.setTarget(targetList);
		configureTargetALTG(request, mailingWizardForm);
	}

    protected void setComplexTargetExpressionRequestAttr(HttpServletRequest request, Admin admin, MailingWizardForm mailingWizardForm) {
        request.setAttribute("complexTargetExpression", mailingWizardForm.getMailing().hasComplexTargetExpression());
    }

	public void configureTargetALTG(HttpServletRequest request, MailingWizardForm form) {
		//altg is not supported
        request.setAttribute("adminAltgIds", Collections.emptyList());
        request.setAttribute("altgId", 0);
	}

    /**
     * If the user is not logged in - forwards to login page.<br>
     * Loads target groups list into request.<br>
     * Gets mailing dyntag by name taken from form property dynName. Sets mailing ID and companyID for that dynTag.
     * Cleans up trackable links of mailing and rebuilds dependencies. Finds next dynTag in list of mailings dynTags.
     * (If it already was the last tag - forwards to "skip"). Sets next dynTag name to form. Updates property that
     * indicates if we need to show HTML editor for editing content of the tag (that basically dependant on is it
     * HTML-version tag or Text-version tag). Forwards to input forward (text module editing page)<br>
     * If the dynName property of form is not set and mailing doesn't have dyntags - forwards to "skip". If the dynName
     * property of form is not set and mailing does have dyntags - takes the first one and sets its name to form;
     * updates property that indicates if we need to show HTML editor for editing content; forwards to "next".
     *
     * @param mapping The ActionMapping used to select this instance
     * @param form data for the action filled by the jsp
     * @param req request from jsp
     * @param res response
     * @return destination specified in struts-config.xml to forward to next jsp
     * @throws Exception if a exception occurs
     */
	public ActionForward textmodule(ActionMapping mapping, ActionForm form,
			HttpServletRequest req, HttpServletResponse res) throws Exception {
		if (!AgnUtils.isUserLoggedIn(req)) {
			return mapping.findForward("logon");
		}

		MailingWizardForm aForm = (MailingWizardForm) form;
		Mailing mailing = aForm.getMailing();
		DynamicTag dynTag = null;
        prepareAttachmentPage(req, form);
		dynTag = mailing.getDynTags().get(aForm.getDynName());
		if (dynTag != null) {
			dynTag.setMailingID(mailing.getId());
			dynTag.setCompanyID(mailing.getCompanyID());

			mailing.cleanupTrackableLinks(new Vector<>());
			mailing.buildDependencies(true, getApplicationContext(req));
			if (aForm.getDynName() != null
					&& aForm.getDynName().trim().length() != 0) {
				Iterator<String> it = mailing.getDynTags().keySet().iterator();
				while (it.hasNext()) {
					if (it.next().equals(aForm.getDynName())) {
						break;
					}
				}
				if(!it.hasNext()) {
					return mapping.findForward("skip");
				}
                String dynName = it.next();
                aForm.setDynName(dynName);
                aForm.setShowHTMLEditorForDynTag(allowHTMLEditor(mailing, dynName));
			}
			return mapping.getInputForward();
		}

		if (aForm.getDynName() == null
				|| aForm.getDynName().trim().length() == 0) {
            if (!mailing.getDynTags().keySet()
                    .iterator().hasNext()) {
                return mapping.findForward("skip");
            }
            String dynName = mailing.getDynTags().keySet().iterator().next();
            aForm.setDynName(dynName);
            aForm.setShowHTMLEditorForDynTag(allowHTMLEditor(mailing, dynName));
		}
		return mapping.findForward("next");
	}

	/**
     * Loads target groups list into request and forwards to "previous".
     *
     * @param mapping The ActionMapping used to select this instance
     * @param form data for the action filled by the jsp
     * @param req request from jsp
     * @param res response
     * @return destination specified in struts-config.xml to forward to next jsp
     * @throws Exception if a exception occurs
     */
	public ActionForward type_previous(ActionMapping mapping, ActionForm form,
			HttpServletRequest req, HttpServletResponse res) throws Exception {
        prepareTemplatePage(req);
		return mapping.findForward("previous");
	}

    /**
     * Checks if HTMLEditor is allowed for dynTag (checks if the text module comes from HTML-version or Text-version).
     *
     * @param mailing a mailing which content should be checked.
	 * @param dynTargetName dynTag name to check.
     * @return {@code true} if HTMLEditor is allowed, otherwise {@code false}.
     * @throws DynTagException if a exception occurs.
     */
    private boolean allowHTMLEditor(Mailing mailing, String dynTargetName) throws DynTagException {
    	MailingComponent textTemplate = mailing.getTextTemplate();
    	if (textTemplate == null) {
    		return true;
		}

    	for (DynamicTag tag : agnTagService.getDynTags(textTemplate.getEmmBlock())) {
    		if (StringUtils.equals(tag.getDynName(), dynTargetName)) {
    			return false;
			}
		}

		return true;
    }

    /**
     * If the user is not logged in - forwards to login page.
     * Creates new dynTag content. Sets its properties. Adds it to current dynTag. Resets targetID and newContent
     * properties of form. Loads target groups list into request. Forwards to "add" (text module editing page)
     *
     * @param mapping The ActionMapping used to select this instance
     * @param form data for the action filled by the jsp
     * @param req request from jsp
     * @param res response
     * @return destination specified in struts-config.xml to forward to next jsp
     * @throws Exception if a exception occurs
     */
	public ActionForward textmodule_add(ActionMapping mapping, ActionForm form,
			HttpServletRequest req, HttpServletResponse res) throws Exception {

		if (!AgnUtils.isUserLoggedIn(req)) {
			return mapping.findForward("logon");
		}

		MailingWizardForm aForm = (MailingWizardForm) form;
		Mailing mailing = aForm.getMailing();
		DynamicTag dynTag = mailing.getDynTags().get(aForm.getDynName());
		DynamicTagContent content = dynamicTagContentFactory.newDynamicTagContent();

		dynTag.setMailingID(mailing.getId());
		dynTag.setCompanyID(mailing.getCompanyID());

		content.setCompanyID(mailing.getCompanyID());
		content.setDynContent(aForm.getNewContent());
		content.setTargetID(aForm.getNewModuleTargetID());
		content.setDynNameID(dynTag.getId());
		content.setMailingID(dynTag.getMailingID());
		content.setDynOrder(dynTag.getMaxOrder()+1);
		dynTag.addContent(content);
		aForm.setNewModuleTargetID(0);
		aForm.setNewContent("");
        prepareAttachmentPage(req, form);
		return mapping.findForward("add");

	}

    /**
     * If the user is not logged in - forwards to login page.<br>
     * Saves existing textmodule.<br>
     * Calls method <code>textmodule</code> of this class. Sets dynName property of form. Updates property that
     * indicates if we need to show HTML editor for editing content. Loads target groups list into request. Forwards to
     * textmodule editing page.
     *
     * @param mapping The ActionMapping used to select this instance
     * @param form data for the action filled by the jsp
     * @param req request from jsp
     * @param res response
     * @return destination specified in struts-config.xml to forward to next jsp
     * @throws Exception if a exception occurs
     */
	public ActionForward textmodule_save(ActionMapping mapping, ActionForm form,
			HttpServletRequest req, HttpServletResponse res) throws Exception {

		if (!AgnUtils.isUserLoggedIn(req)) {
			return mapping.findForward("logon");
		}
        MailingWizardForm aForm = (MailingWizardForm) form;
        String dynName = aForm.getDynName();
        Mailing mailing = aForm.getMailing();
        textmodule(mapping, form, req, res);
        aForm.setDynName(dynName);
        aForm.setShowHTMLEditorForDynTag(allowHTMLEditor(mailing, dynName));
        prepareAttachmentPage(req, form);
        return mapping.getInputForward();
	}

    /**
     * If the user is not logged in - forwards to login page.
     * Changes the order, moves the current dynContent one position up. Loads target groups list into request. Forwards
     * to textmodule editing page.
     *
     * @param mapping The ActionMapping used to select this instance
     * @param form data for the action filled by the jsp
     * @param req request from jsp
     * @param res response
     * @return destination specified in struts-config.xml to forward to next jsp
     * @throws Exception if a exception occurs
     */
	public ActionForward textmodule_move_up(ActionMapping mapping, ActionForm form,
			HttpServletRequest req, HttpServletResponse res) throws Exception {

		if (!AgnUtils.isUserLoggedIn(req)) {
			return mapping.findForward("logon");
		}
        MailingWizardForm aForm = (MailingWizardForm) form;
        Mailing mailing = aForm.getMailing();
        DynamicTag dynTag = mailing.getDynTags().get(aForm.getDynName());
        dynTag.moveContentDown(aForm.getContentID(), -1, true);
        prepareAttachmentPage(req, form);
        return mapping.findForward("add");
	}

    /**
     * If the user is not logged in - forwards to login page.
     * Changes the order, moves the current dynContent one position down. Loads target groups list into request. Forwards
     * to textmodule editing page.
     *
     * @param mapping The ActionMapping used to select this instance
     * @param form data for the action filled by the jsp
     * @param req request from jsp
     * @param res response
     * @return destination specified in struts-config.xml to forward to next jsp
     * @throws Exception if a exception occurs
     */
	public ActionForward textmodule_move_down(ActionMapping mapping, ActionForm form,
			HttpServletRequest req, HttpServletResponse res) throws Exception {
		if (!AgnUtils.isUserLoggedIn(req)) {
			return mapping.findForward("logon");
		}
        MailingWizardForm aForm = (MailingWizardForm) form;
        Mailing mailing = aForm.getMailing();
        DynamicTag dynTag = mailing.getDynTags().get(aForm.getDynName());
        dynTag.moveContentDown(aForm.getContentID(), 1, true);
        prepareAttachmentPage(req, form);
        return mapping.findForward("add");
	}

    /**
     * If the user is not logged in - forwards to login page.
     * Resets form's trackable link iterator. Loads list of none-form actions into request. Forwards to links editing
     * page (forward is "next")
     *
     * @param mapping The ActionMapping used to select this instance
     * @param form data for the action filled by the jsp
     * @param req request from jsp
     * @param res response
     * @return destination specified in struts-config.xml to forward to next jsp
     * @throws Exception if a exception occurs
     */
	public ActionForward links(ActionMapping mapping, ActionForm form,
			HttpServletRequest req, HttpServletResponse res) throws Exception {
		if (!AgnUtils.isUserLoggedIn(req)) {
			return mapping.findForward("logon");
		}

		MailingWizardForm aForm = (MailingWizardForm) form;
		aForm.clearAktTracklink();
        prepareLinkPage(req);
		return mapping.findForward("next");
	}

    /**
     * If the user is not logged in - forwards to login page.
     * Gets next link, loads list of none-form actions into request, forwards to link editing page.
     * If no more links found - loads list of target groups to request, forwards to attachments page.
     *
     * @param mapping The ActionMapping used to select this instance
     * @param form data for the action filled by the jsp
     * @param req request from jsp
     * @param res response
     * @return destination specified in struts-config.xml to forward to next jsp
     * @throws Exception if a exception occurs
     */
	public ActionForward link(ActionMapping mapping, ActionForm form,
			HttpServletRequest req, HttpServletResponse res) throws Exception {
		if (!AgnUtils.isUserLoggedIn(req)) {
			return mapping.findForward("logon");
		}

		MailingWizardForm aForm = (MailingWizardForm) form;

		if(aForm.nextTracklink()) {
            prepareLinkPage(req);
			return mapping.findForward("next");
		}
        prepareAttachmentPage(req, form);
		return mapping.findForward("skip");
	}

    /**
     * If the user is not logged in - forwards to login page.
     * Loads list of none-form actions into request, forwards to link editing page.
     *
     * @param mapping The ActionMapping used to select this instance
     * @param form data for the action filled by the jsp
     * @param req request from jsp
     * @param res response
     * @return destination specified in struts-config.xml to forward to next jsp
     * @throws Exception if a exception occurs
     */
	public ActionForward link_save_only(ActionMapping mapping, ActionForm form,
			HttpServletRequest req, HttpServletResponse res) throws Exception {
		if (!AgnUtils.isUserLoggedIn(req)) {
			return mapping.findForward("logon");
		}
        prepareLinkPage(req);
		return mapping.findForward("next");
	}

    protected void prepareLinkPage(HttpServletRequest req) {
        List<EmmAction> linkActions = emmActionDao.getEmmNotFormActions(AgnUtils.getCompanyID(req));
        req.setAttribute("linkActions", linkActions);
    }

    /**
     * If the user is not logged in - forwards to login page.
     * Loads list of target groups into request, forwards to attachments editing page.
     *
     * @param mapping The ActionMapping used to select this instance
     * @param form data for the action filled by the jsp
     * @param req request from jsp
     * @param res response
     * @return destination specified in struts-config.xml to forward to next jsp
     * @throws Exception if a exception occurs
     */
    public ActionForward to_attachment(ActionMapping mapping, ActionForm form,
			HttpServletRequest req, HttpServletResponse res) throws Exception {
		if (!AgnUtils.isUserLoggedIn(req)) {
			return mapping.findForward("logon");
		}
        prepareAttachmentPage(req, form);
		return mapping.findForward("skip");
	}

	protected void prepareAttachmentPage(HttpServletRequest request, ActionForm form) {
		final Admin admin = AgnUtils.getAdmin(request);
		final boolean excludeHiddenTargets = !admin.permissionAllowed(Permission.MAILING_CONTENT_SHOW_EXCLUDED_TARGETGROUPS);

		List<TargetLight> targetList = targetService.getTargetLights(admin, false, true, false, excludeHiddenTargets);

		request.setAttribute("targets", targetList);

		((MailingWizardForm) form).setTarget(targetList);
	}

    /**
     * If the user is not logged in - forwards to login page.
     * Checks if the size of new attachment exceeds the max allowed size for attachment. If the size is over the limit
     * adds appropriate error messages. If the size is ok - creates a new mailing component for attachment and fills it
     * with data and adds that component to mailing. Loads list of target groups into request. Forwards to attachments
     * page
     *
     * @param mapping The ActionMapping used to select this instance
     * @param form data for the action filled by the jsp
     * @param req request from jsp
     * @param res response
     * @return destination specified in struts-config.xml to forward to next jsp
     * @throws Exception if a exception occurs
     */
	public ActionForward attachment(ActionMapping mapping, ActionForm form,
			HttpServletRequest req, HttpServletResponse res) throws Exception {
		if (!AgnUtils.isUserLoggedIn(req)) {
			return mapping.findForward("logon");
		}

		MailingWizardForm aForm = (MailingWizardForm) form;
		FormFile newAttachment=aForm.getNewAttachment();
        try	{
            int fileSize = newAttachment.getFileSize();
            boolean maxSizeOverflow = false;
            // check for parameter max_allowed_packet
            int maxSize = configService.getIntegerValue(ConfigValue.MaximumUploadAttachmentSize);
            if (fileSize != 0 && fileSize > maxSize){
                maxSizeOverflow = true;
            }
            if (fileSize != 0 && !maxSizeOverflow) {
				MailingComponent comp=mailingComponentFactory.newMailingComponent();

				comp.setCompanyID(AgnUtils.getCompanyID(req));
				comp.setMailingID(aForm.getMailing().getId());
				if(aForm.getNewAttachmentType() == 0) {
					comp.setType(MailingComponentType.Attachment);
				} else {
					comp.setType(MailingComponentType.PersonalizedAttachment);
				}
				if(aForm.getNewAttachmentName().isEmpty()) {
					aForm.setNewAttachmentName(aForm.getNewAttachment().getFileName());
				}

				comp.setComponentName(aForm.getNewAttachmentName());
				comp.setBinaryBlock(newAttachment.getFileData(), newAttachment.getContentType());
				comp.setTargetID(aForm.getAttachmentTargetID());
				aForm.getMailing().addComponent(comp);
				userActivityLogService.writeUserActivityLog(AgnUtils.getAdmin(req), "upload mailing attachment", "Mailing ID: "+aForm.getMailing().getId()+", component name: "+aForm.getNewAttachmentName());
            } else if (maxSizeOverflow) {
                ActionMessages errors = new ActionMessages();
                errors.add(ActionMessages.GLOBAL_MESSAGE, new ActionMessage("error.attachment", maxSize));
                saveErrors(req, errors);
            }

		} catch (Exception e) {
			logger.error("saveAttachment: "+e);
		}
        prepareAttachmentPage(req, form);
		return mapping.findForward("next");
	}

    /**
     * If the user is not logged in - forwards to login page.
     * Allows to download attachment by name
     *
     * @param mapping The ActionMapping used to select this instance
     * @param form data for the action filled by the jsp
     * @param req request from jsp
     * @param response response
     * @return destination specified in struts-config.xml to forward to next jsp
     * @throws Exception if a exception occurs
     */
	public ActionForward attachmentDownload(ActionMapping mapping, ActionForm form,
			HttpServletRequest req, HttpServletResponse response) throws Exception {
		if (!AgnUtils.isUserLoggedIn(req)) {
			return mapping.findForward("logon");
		}

		MailingWizardForm aForm = (MailingWizardForm) form;
        String compName = req.getParameter("compName");
        compName = new String(compName.getBytes("ISO-8859-1"), "UTF-8");
        MailingComponent comp = aForm.getMailing().getComponents().get(compName);

        if (comp != null) {
            HttpUtils.setDownloadFilenameHeader(response, comp.getComponentName().replace("\"", "\\\""));
			try (ServletOutputStream out = response.getOutputStream()) {
				response.setContentLength(comp.getBinaryBlock().length);
				out.write(comp.getBinaryBlock());
				out.flush();
			}
        } else {
            prepareAttachmentPage(req, form);
            return mapping.findForward("error");
        }
        return null;
	}

    /**
     * If the user is not logged in - forwards to login page.
     * Saves the mailing into database and forwards to finish page.
     *
     * @param mapping The ActionMapping used to select this instance
     * @param form data for the action filled by the jsp
     * @param req request from jsp
     * @param res response
     * @return destination specified in struts-config.xml to forward to next jsp
     * @throws Exception if a exception occurs
     */
	public ActionForward finish(ActionMapping mapping, ActionForm form,
			HttpServletRequest req, HttpServletResponse res) throws Exception {

		if (!AgnUtils.isUserLoggedIn(req)) {
			return mapping.findForward("logon");
		}

		MailingWizardForm aForm = (MailingWizardForm) form;
		Mailing mailing = aForm.getMailing();

		mailing.getEmailParam().setEncryptedSend(configService.getBooleanValue(ConfigValue.SendEncryptedMailings, mailing.getCompanyID()));

		mailingDao.saveMailing(mailing, false);
		previewImageService.generateMailingPreview(AgnUtils.getAdmin(req), req.getSession().getId(), mailing.getId(), true);

		updateForwardParameters(req, true);
		return mapping.findForward("finish");
	}

    /**
     * If the user is not logged in - forwards to login page.
     * Forwards to "previous" (used for handling Previous button click).
     *
     * @param mapping The ActionMapping used to select this instance
     * @param form data for the action filled by the jsp
     * @param req request from jsp
     * @param res response
     * @return destination specified in struts-config.xml to forward to next jsp
     * @throws Exception if a exception occurs
     */
	public ActionForward previous(ActionMapping mapping, ActionForm form,
			HttpServletRequest req, HttpServletResponse res) throws Exception {
		if (!AgnUtils.isUserLoggedIn(req)) {
			return mapping.findForward("logon");
		}

		return mapping.findForward("previous");
	}

    /**
     * If the user is not logged in - forwards to login page.
     * Forwards to "next" (used for handling Next button click).
     *
     * @param mapping The ActionMapping used to select this instance
     * @param form data for the action filled by the jsp
     * @param req request from jsp
     * @param res response
     * @return destination specified in struts-config.xml to forward to next jsp
     * @throws Exception if a exception occurs
     */
	public ActionForward next(ActionMapping mapping, ActionForm form,
			HttpServletRequest req, HttpServletResponse res) throws Exception {
		if (!AgnUtils.isUserLoggedIn(req)) {
			return mapping.findForward("logon");
		}

		return mapping.findForward("next");
	}

    /**
     * If the user is not logged in - forwards to login page.
     * Forwards to "skip" (used for handling Skip button click).
     *
     * @param mapping The ActionMapping used to select this instance
     * @param form data for the action filled by the jsp
     * @param req request from jsp
     * @param res response
     * @return destination specified in struts-config.xml to forward to next jsp
     * @throws Exception if a exception occurs
     */
	public ActionForward skip(ActionMapping mapping, ActionForm form,
			HttpServletRequest req, HttpServletResponse res) throws Exception {
		if (!AgnUtils.isUserLoggedIn(req)) {
			return mapping.findForward("logon");
		}

		return mapping.findForward("skip");
	}

	/**
     * If the user is not logged in - forwards to login page.
     * Saves openrate-measure property to mailing email param. Loads campaigns, mailinglists and target-groups to
     * request. Adds target to mailing targets list if needed. Removes target from mailing target list if needed.
     * Updates target expression (re-generates String representing selected targets IDs of mailing). Forwards to
     * "next" (currently the page for managing textmodules)
     *
     * @param mapping The ActionMapping used to select this instance
     * @param form data for the action filled by the jsp
     * @param request request from jsp
     * @param response response
     * @return destination specified in struts-config.xml to forward to next jsp
     */
	public ActionForward target(ActionMapping mapping, ActionForm form,
			HttpServletRequest request, HttpServletResponse response) {
		if (!AgnUtils.isUserLoggedIn(request)) {
			return mapping.findForward("logon");
		}

		MailingWizardForm aForm = (MailingWizardForm) form;
		Mailing mailing = aForm.getMailing();

		MediatypeEmail param = mailing.getEmailParam();
		param.setOnepixel(aForm.getEmailOnepixel());

		if (aForm.getAssignTargetGroups()) {
            mailing.setTargetExpression(TargetExpressionUtils.makeTargetExpression(aForm.getTargetGroups(), mailing.getTargetMode() != Mailing.TARGET_MODE_OR));
        }

		return mapping.findForward("next");
	}

	public ActionForward addTarget(ActionMapping mapping, ActionForm form,
                                   HttpServletRequest request, HttpServletResponse response) {
        if (!AgnUtils.isUserLoggedIn(request)) {
            return mapping.findForward("logon");
        }

        ActionMessages errors = new ActionErrors();
        try {
			MailingWizardForm aForm = (MailingWizardForm) form;
			int newTargetId = aForm.getAddTargetID();

			Collection<Integer> targetGroups = CollectionUtils.emptyIfNull(aForm.getTargetGroups());
			if (newTargetId > 0 && !targetGroups.contains(newTargetId)) {
				ComTarget target = targetService.getTargetGroup(aForm.getAddTargetID(), AgnUtils.getCompanyID(request));
				if (target.getDeleted() == 0) {
					targetGroups.add(newTargetId);
				}
				aForm.setTargetGroups(targetGroups);
			}

			return targetView(mapping, form, request, response);
		} catch (UnknownTargetGroupIdException e) {
			errors.add(ActionMessages.GLOBAL_MESSAGE, new ActionMessage("error.target.unknownTargetGroup"));
		} catch (Exception e) {
			errors.add(ActionMessages.GLOBAL_MESSAGE, new ActionMessage("Error"));
		}

        return mapping.findForward("messages");
    }

	public ActionForward newTarget(ActionMapping mapping, ActionForm form,
								   HttpServletRequest request, HttpServletResponse response) throws Exception {
		if (!AgnUtils.isUserLoggedIn(request)) {
			return mapping.findForward("logon");
		}

		Admin admin = AgnUtils.getAdmin(request);

        request.setAttribute("editTargetForm", new TargetEditForm());
		request.setAttribute("mailTrackingAvailable", AgnUtils.isMailTrackingAvailable(admin));
		request.setAttribute("queryBuilderFilters", filterListBuilder.buildFilterListJson(admin, true));

		return mapping.findForward("newTarget");
	}

	public ActionForward viewTarget(ActionMapping mapping, ActionForm form,
								   HttpServletRequest request, HttpServletResponse response) throws Exception {
		MailingWizardForm wizardForm = (MailingWizardForm) form;
		request.getRequestDispatcher("/target/" + wizardForm.getViewTargetId() + "/view.action?isMailingWizard=true").forward(request, response);
		return null;
	}

	private void setMailingWorkflowParameters(HttpServletRequest req, Mailing mailing) {
		HttpSession session = req.getSession();

		Integer workflowId = (Integer) session.getAttribute(WorkflowParametersHelper.WORKFLOW_ID);
		if (workflowId != null && workflowId > 0) {
			Map<String, String> forwardParams = AgnUtils.getParamsMap((String) session.getAttribute(WorkflowParametersHelper.WORKFLOW_FORWARD_PARAMS));
			int mailingIconId = NumberUtils.toInt(forwardParams.get("nodeId"));

			workflowService.assignWorkflowDrivenSettings(AgnUtils.getAdmin(req), mailing, workflowId, mailingIconId);
			if(mailing.getSplitID() == Mailing.YES_SPLIT_ID) {
				mailing.setSplitID(Mailing.NONE_SPLIT_ID);
			}
		}
	}

	protected ApplicationContext getApplicationContext(HttpServletRequest req){
        // this method should be removed after bean Mailing will be refactored
        return WebApplicationContextUtils.getRequiredWebApplicationContext(req.getSession().getServletContext());
    }

	public void setMailinglistService(MailinglistService mailinglistService) {
		this.mailinglistService = mailinglistService;
	}

    public void setMailingFactory(MailingFactory mailingFactory) {
        this.mailingFactory = mailingFactory;
    }

    public void setMailingDao(MailingDao mailingDao) {
        this.mailingDao = mailingDao;
    }

	public void setMailingComponentFactory(MailingComponentFactory mailingComponentFactory) {
        this.mailingComponentFactory = mailingComponentFactory;
    }

    public void setDynamicTagContentFactory(DynamicTagContentFactory dynamicTagContentFactory) {
        this.dynamicTagContentFactory = dynamicTagContentFactory;
    }

    public CampaignDao getCampaignDao() {
        return campaignDao;
    }

    public void setCampaignDao(CampaignDao campaignDao) {
        this.campaignDao = campaignDao;
    }

    @Required
	public void setTargetService(ComTargetService targetService) {
		this.targetService = targetService;
	}

	/**
     * Returns DAO accessing EMM actions.
     *
     * @return DAO accessing EMM actions
     */
    public EmmActionDao getEmmActionDao() {
        return emmActionDao;
    }

    /**
     * Sets DAO accessing EMM actions.
     *
     * @param emmActionDao DAO accessing EMM actions
     */
    public void setEmmActionDao(EmmActionDao emmActionDao) {
        this.emmActionDao = emmActionDao;
    }

	@Required
	public void setConfigService(ConfigService configService) {
		this.configService = configService;
	}

	@Required
	public void setWorkflowDao(ComWorkflowDao workflowDao) {
		this.workflowDao = workflowDao;
	}

	@Required
	public void setWorkflowService(ComWorkflowService workflowService) {
		this.workflowService = workflowService;
	}

	@Required
	public void setAgnTagService(AgnTagService agnTagService) {
		this.agnTagService = agnTagService;
	}

    @Required
    public final void setMailinglistApprovalService(final MailinglistApprovalService service) {
    	this.mailinglistApprovalService = Objects.requireNonNull(service, "Mailinglist approval service is null");
    }

	@Required
	public void setUserActivityLogService(UserActivityLogService userActivityLogService) {
		this.userActivityLogService = userActivityLogService;
	}

	@Required
	public void setCopyMailingService(CopyMailingService copyMailingService) {
		this.copyMailingService = copyMailingService;
	}

	@Required
	public void setMailingService(MailingService mailingService) {
		this.mailingService = mailingService;
	}

	@Required
	public void setPreviewImageService(PreviewImageService previewImageService) {
    	this.previewImageService = previewImageService;
	}

	@Required
	public void setFilterListBuilder(QueryBuilderFilterListBuilder filterListBuilder) {
		this.filterListBuilder = filterListBuilder;
	}

	@Required
	public void setAdminService(AdminService adminService) {
		this.adminService = adminService;
	}

	@Required
	public void setEditorContentSynchronizer(EditorContentSynchronizer editorContentSynchronizer) {
		this.editorContentSynchronizer = editorContentSynchronizer;
	}
}
