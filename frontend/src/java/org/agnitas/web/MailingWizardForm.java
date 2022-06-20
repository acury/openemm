/*

    Copyright (C) 2022 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package org.agnitas.web;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.agnitas.beans.DynamicTagContent;
import org.agnitas.beans.TrackableLink;
import org.agnitas.util.AgnUtils;
import org.agnitas.web.forms.StrutsFormBase;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.upload.FormFile;

import com.agnitas.beans.DynamicTag;
import com.agnitas.beans.Mailing;
import com.agnitas.beans.MailingContentType;
import com.agnitas.beans.MediatypeEmail;
import com.agnitas.beans.TargetLight;
import com.agnitas.emm.common.MailingType;
import com.agnitas.web.MailingWizardAction;

import jakarta.mail.internet.InternetAddress;
import jakarta.servlet.http.HttpServletRequest;

public class MailingWizardForm extends StrutsFormBase {
	
	private static final transient Logger logger = LogManager.getLogger( MailingWizardForm.class);
    
    private static final long serialVersionUID = 9104717555855628618L;
    private static final Pattern CONTENT_PARAMETER_PATTERN = Pattern.compile("newContent|content\\[\\d+]\\.dynContent");

    private int newModuleTargetID;


    /** Creates a new instance of TemplateForm */
    public MailingWizardForm() {
    }
    
    @Override
    protected void loadNonFormDataForErrorView(ActionMapping mapping, HttpServletRequest request) {
        super.loadNonFormDataForErrorView(mapping, request);
    }
    
    /**
     * Validate the properties that have been set from this HTTP request,
     * and return an <code>ActionErrors</code> object that encapsulates any
     * validation errors that have been found.  If no errors are found, return
     * <code>null</code> or an <code>ActionErrors</code> object with no
     * recorded error messages.
     * 
     * @param mapping The mapping used to select this instance
     * @param request The servlet request we are processing
     * @return errors
     */
    @Override
    public ActionErrors formSpecificValidate(ActionMapping mapping,
            HttpServletRequest request) {
    	ActionErrors errors = new ActionErrors();

        if (MailingWizardAction.ACTION_NAME.equals(this.action) &&
                StringUtils.length(this.getMailing().getShortname()) < 3) {
            errors.add("shortname", new ActionMessage("error.name.too.short"));
        }

        if (this.action.equals(MailingWizardAction.ACTION_SENDADDRESS)) {
            if (StringUtils.length(getReplyFullname()) > 255) {
                errors.add("replyFullname", new ActionMessage("error.reply_fullname_too_long"));
            }
            if (StringUtils.length(getSenderFullname()) > 255) {
                errors.add("senderFullname", new ActionMessage("error.sender_fullname_too_long"));
            }
            if (StringUtils.length(getSenderFullname())== 0) {
                this.replyFullname = getSenderFullname();
            }
            if (StringUtils.length(senderEmail) < 3) {
                errors.add("shortname", new ActionMessage("error.invalid.email"));
            }
            try {
                InternetAddress adr = new InternetAddress(senderEmail);
                String email = adr.getAddress();
                if (!AgnUtils.isEmailValid(email)) {
                    errors.add("sender", new ActionMessage("error.mailing.sender_adress"));
                }
            } catch (Exception e) {
                if (!StringUtils.contains(senderEmail, "[agn")) {
                    errors.add("sender", new ActionMessage("error.mailing.sender_adress"));
                }
            }
        }

        if (action.equals(MailingWizardAction.ACTION_SUBJECT) && getEmailSubject().length() < 2) {
            errors.add("subject", new ActionMessage("error.mailing.subject.too_short"));
        }

        if (action.equals(MailingWizardAction.ACTION_TEXTMODULE) && mapping.getPath().equals("/mwTextmodule")){
            Map<String, DynamicTag> dynamicTags = mailing.getDynTags();
            if (hasDuplicate(dynamicTags)) {
				errors.add("textmodule", new ActionMessage("error.mailing.content.target.duplicated"));
			}
        }

        if (mailing != null && (MailingWizardAction.ACTION_TARGET.equalsIgnoreCase(action) ||
        		MailingWizardAction.ACTION_FINISH.equalsIgnoreCase(action))) {
    	  if (CollectionUtils.isEmpty(mailing.getTargetGroups()) && CollectionUtils.isEmpty(targetGroups) && mailing.getMailingType() == MailingType.DATE_BASED) {
              errors.add("global", new ActionMessage("error.mailing.rulebased_without_target"));
          }
    	}

        return errors;
    }

    public boolean hasDuplicate(Map<String, DynamicTag> dynamicTags){
        for (DynamicTag dynamicTag : dynamicTags.values()) {
            Map<Integer, DynamicTagContent> contentMap = dynamicTag.getDynContent();
            Set<Integer> targets = new HashSet<>();
            for (DynamicTagContent dynamicTagContent : contentMap.values()) {
                int targetID = dynamicTagContent.getTargetID();
                if (!targets.add(targetID)) {
					return true;
				}
            }
        }
        return false;
    }

    @Override
    protected boolean isParameterExcludedForUnsafeHtmlTagCheck(String parameterName, HttpServletRequest request) {
        return CONTENT_PARAMETER_PATTERN.matcher(parameterName).matches();
    }

	/**
     * Holds value of property action.
     *
     */
    private String action;

    /**
     * Getter for property action.
     *
     * @return Value of property action.
     */
    public String getAction() {
        return this.action;
    }

    /**
     * Setter for property action.
     *
     * @param action New value of property action.
     */
    public void setAction(String action) {
        this.action = action;
    }

    /**
     * Holds value of property mailing.
     */
    private Mailing mailing;

    /**
     * Getter for property mailing.
     *
     * @return Value of property mailing.
     */
    public Mailing getMailing() {
        return this.mailing;
    }

    /**
     * Setter for property mailing.
     *
     * @param mailing New value of property mailing.
     */
    public void setMailing(Mailing mailing) {
        this.mailing = mailing;
    }

    /**
     * Holds value of property targetGroups.
     */
    protected Collection<Integer> targetGroups;

    // Mailing's target expression won't be overwritten unless this flag is set to true.
	private boolean assignTargetGroups;

	private int addTargetID;

    public int getAddTargetID() {
        return addTargetID;
    }

    public void setAddTargetID(int addTargetID) {
        this.addTargetID = addTargetID;
    }

    public Collection<Integer> getTargetGroups() {
        return targetGroups;
    }

    public void setTargetGroups(Collection<Integer> targetGroups) {
        this.targetGroups = targetGroups;
    }

    public void setTargetGroupIds(Integer[] targetGroupIds) {
		targetGroups = new ArrayList<>(Arrays.asList(targetGroupIds));
	}

    public Integer[] getTargetGroupIds() {
        if (CollectionUtils.isEmpty(targetGroups)) {
            targetGroups = mailing.getTargetGroups();
        }

        if (CollectionUtils.isNotEmpty(targetGroups)) {
			return targetGroups.toArray(new Integer[targetGroups.size()]);
		} else {
			return ArrayUtils.EMPTY_INTEGER_OBJECT_ARRAY;
		}
	}

    public boolean getAssignTargetGroups() {
		return assignTargetGroups;
	}

	public void setAssignTargetGroups(boolean assignTargetGroups) {
		this.assignTargetGroups = assignTargetGroups;
	}

    /**
     * Holds value of property aktTracklinkID.
     */
    private Iterator<String> tracklinkIterator=null;
    private TrackableLink tracklink=null;

    /**
     * Setter for property aktTracklinkID..
     */
    public boolean nextTracklink() {
        if (tracklinkIterator.hasNext()) {
            String id = tracklinkIterator.next();

            tracklink = mailing.getTrackableLinks().get(id);
            return true;
        }
        tracklink=null;
        return false;
    }

    /**
     * Getter for property linkUrl.
     *
     * @return Value of property linkUrl.
     */
    public String getLinkUrl() {
        if(tracklink != null) {
            return tracklink.getFullUrl();
        }
        return "";
    }

    /**
     * Setter for property linkUrl.
     */
    public void setLinkUrl(String linkURL) {
        if(tracklink != null) {
            tracklink.setFullUrl(linkURL);
        } else {
            logger.error("setLinkUrl: Trying to set url for invalid tracklink");
        }
    }

    /**
     * Getter for property linkName.
     *
     * @return Value of property linkName.
     */
    public String getLinkName() {
        if(tracklink != null) {
            return tracklink.getShortname();
        }
        return "";
    }

    /**
     * Setter for property linkName.
     *
     * @param name New value of property linkName.
     */
    public void setLinkName(String name) {
        if(tracklink != null) {
            tracklink.setShortname(name);
        } else {
            logger.error("setLinkName: Trying to set name for invalid tracklink");
        }
    }

    /**
     * Setter for property aktTracklinkID.
     */
    public void clearAktTracklink() {
        tracklinkIterator = mailing.getTrackableLinks().keySet().iterator();
    }


    public int getTrackable() {
        if (tracklink != null) {
            return tracklink.getUsage();
        }
        return -1;
    }

    /**
     * Setter for property trackable.
     */
    public void setTrackable(int trackable) {
        if(tracklink != null) {
            tracklink.setUsage(trackable);
        } else {
            logger.error("setLinkUsage: Trying to set usage for invalid tracklink");
        }
    }


    public int getLinkAction() {
        if (tracklink != null) {
            return tracklink.getActionID();
        }
        return -1;
    }

    public void setLinkAction(int linkAction) {
        if(tracklink != null) {
            tracklink.setActionID(linkAction);
        } else {
            logger.error("setLinkAction: Trying to set action for invalid tracklink");
        }
    }


    /**
     * Holds value of property senderEmail.
     */
    private String senderEmail;

    /**
     * Getter for property senderEmail.
     *
     * @return Value of property senderEmail.
     */
    public String getSenderEmail() {
        return this.senderEmail;
    }

    /**
     * Setter for property senderEmail.
     *
     * @param senderEmail New value of property senderEmail.
     */
    public void setSenderEmail(String senderEmail) {
        this.senderEmail = senderEmail;
    }

    /**
     * Holds value of property senderFullname.
     */
    private String senderFullname;

    /**
     * Getter for property senderFullname.
     *
     * @return Value of property senderFullname.
     */
    public String getSenderFullname() {
        return this.senderFullname;
    }

    /**
     * Setter for property senderFullname.
     *
     * @param senderFullname New value of property senderFullname.
     */
    public void setSenderFullname(String senderFullname) {
        this.senderFullname = senderFullname;
    }
    
    /**
     * Holds value of property replyEmail.
     */
    private String replyEmail;

    /**
     * Getter for property replyEmail.
     *
     * @return Value of property replyEmail.
     */
    public String getReplyEmail() {
        return this.replyEmail;
    }

    /**
     * Setter for property replyEmail.
     *
     * @param replyEmail New value of property replyEmail.
     */
    public void setReplyEmail(String replyEmail) {
        this.replyEmail = replyEmail;
    }
    
    
    /**
     * Holds value of property replyFullname.
     */
    private String replyFullname;

    /**
     * Getter for property replyFullname.
     *
     * @return Value of property replyFullname.
     */
    public String getReplyFullname() {
        return this.replyFullname;
    }

    /**
     * Setter for property replyFullname.
     *
     * @param replyFullname New value of property replyFullname.
     */
    public void setReplyFullname(String replyFullname) {
        this.replyFullname = replyFullname;
    }

    /**
     * Holds value of property emailSubject.
     */
    private String emailSubject;

    /**
     * Getter for property emailSubject.
     *
     * @return Value of property emailSubject.
     */
    public String getEmailSubject() {
        return this.emailSubject;
    }

    /**
     * Setter for property emailSubject.
     *
     * @param emailSubject New value of property emailSubject.
     */
    public void setEmailSubject(String emailSubject) {
        this.emailSubject = emailSubject;
    }

    /**
     * Holds value of property emailFormat.
     */
    private int emailFormat = 2;

    /**
     * Getter for property emailFormat.
     *
     * @return Value of property emailFormat.
     */
    public int getEmailFormat() {
        return this.emailFormat;
    }

    /**
     * Setter for property emailFormat.
     *
     * @param emailFormat New value of property emailFormat.
     */
    public void setEmailFormat(int emailFormat) {
        this.emailFormat = emailFormat;
    }

    /**
     * Holds value of property emailOnepixel.
     */
    private String emailOnepixel = MediatypeEmail.ONEPIXEL_TOP;

    /**
     * Getter for property emailOnepixel.
     *
     * @return Value of property emailOnepixel.
     */
    public String getEmailOnepixel() {
        return this.emailOnepixel;
    }

    /**
     * Setter for property emailOnepixel.
     *
     * @param emailOnepixel New value of property emailOnepixel.
     */
    public void setEmailOnepixel(String emailOnepixel) {
        this.emailOnepixel = emailOnepixel;
    }

    /**
     * Holds value of property removeTargetID.
     */
    private int removeTargetID;

    /**
     * Getter for property removeTargetID.
     *
     * @return Value of property removeTargetID.
     */
    public int getRemoveTargetID() {
        return this.removeTargetID;
    }

    /**
     * Setter for property removeTargetID.
     *
     * @param removeTargetID New value of property removeTargetID.
     */
    public void setRemoveTargetID(int removeTargetID) {
        this.removeTargetID = removeTargetID;
    }

    /**
     * Holds value of property dynName.
     */
    private String dynName;

    /**
     * Getter for property dynName.
     *
     * @return Value of property dynName.
     */
    public String getDynName() {
        return this.dynName;
    }

    /**
     * Setter for property dynName.
     *
     * @param dynName New value of property dynName.
     */
    public void setDynName(String dynName) {
        this.dynName = dynName;
    }

    /**
     * Holds value of property newContent.
     */
    private String newContent;

    /**
     * Getter for property newContent.
     *
     * @return Value of property newContent.
     */
    public String getNewContent() {
        return this.newContent;
    }

    /**
     * Setter for property newContent.
     *
     * @param newContent New value of property newContent.
     */
    public void setNewContent(String newContent) {
        this.newContent = newContent;
    }
    
    /**
     * Holds value of property newAttachmentType.
     */
    private int newAttachmentType;
    
    /**
     * Getter for property newAttachmentType.
     * @return Value of property newAttachmentType.
     */
    public int getNewAttachmentType() {
        
        return this.newAttachmentType;
    }
    
    /**
     * Setter for property newAttachmentType.
     * @param newAttachmentType New value of property newAttachmentType.
     */
    public void setNewAttachmentType(int newAttachmentType) {
        
        this.newAttachmentType = newAttachmentType;
    }
    
    /**
     * Holds value of property NewFile.
     */
    private FormFile newFile;

    
    /**
     * Getter for property NewFile.
     *
     * @return Value of property NewFile.
     */
    public FormFile getNewAttachment() {
        return this.newFile;
    }

    /**
     * Setter for property NewFile.
     *
     * @param newImage
     */
    public void setNewAttachment(FormFile newImage) {
        this.newFile = newImage;
    }
    
    /**
     * Holds value of property newAttachmentName.
     */
    private String newAttachmentName;

    /**
     * Getter for property newAttachmentName.
     *
     * @return Value of property newAttachmentName.
     */
    public String getNewAttachmentName() {
        return this.newAttachmentName;
    }

    /**
     * Setter for property newAttachmentName.
     *
     * @param newAttachmentName New value of property newAttachmentName.
     */
    public void setNewAttachmentName(String newAttachmentName) {
        this.newAttachmentName = newAttachmentName;
    }
    
    /**
     *  Holds value of property newAttachmentBackground.
     */
    private FormFile newAttachmentBackground;
    
    /** Getter for property newAttachmentBackground.
     * @return Value of property newAttachmentBackground.
     *
     */
    public FormFile getNewAttachmentBackground() {
        return this.newAttachmentBackground;
    }

    /** Setter for property newAttachmentBackground.
     * @param newAttachmentBackground New value of property newAttachmentBackground.
     *
     */
    public void setNewAttachmentBackground(Object newAttachmentBackground) {
    	this.newAttachmentBackground = (FormFile)newAttachmentBackground;
    }
    
    /**
     *  Holds value of property attachmentTargetID.
     */
    private int attachmentTargetID;
    
    /** Getter for property attachmentTargetID.
     * @return Value of property attachmentTargetID.
     */
    public int getAttachmentTargetID() {
        return this.attachmentTargetID;
    }

    /** Setter for property attachmentTargetID.
     * @param attachmentTargetID New value of property attachmentTargetID.
     */
    public void setAttachmentTargetID(int attachmentTargetID) {
        this.attachmentTargetID = attachmentTargetID;
    }

	public DynamicTagContent getContent(int index) {
		DynamicTag	tag= mailing.getDynTags().get(dynName);
		DynamicTagContent content= tag.getDynContent().get(index);

		return content;
	}
	
     /**
     * Holds value of property contentID.
     */
    private int contentID;

    /**
     * Getter for property contentID.
     *
     * @return Value of property contentID.
     */
    public int getContentID() {
        return this.contentID;
    }

    /**
     * Setter for property contentID.
     *
     * @param contentID New value of property contentID.
     */
    public void setContentID(int contentID) {
        this.contentID = contentID;
    }

    /**
     * Target Group List
     * */

    private List<TargetLight> target;

    /**
     * Getter for property target.
     *
     * @return Value of property target.
     */

    public List<TargetLight> getTarget(){
        return target;
    }

    /**
     * Setter for property target.
     *
     * @param target New value of property target.
     */

    public void setTarget(List<TargetLight> target) {
        this.target = target;
    }

    public void clearEmailData() {
		this.setEmailSubject(null);
		this.setEmailFormat(2);
		this.setEmailOnepixel(null);
		this.setSenderEmail(null);
		this.setSenderFullname(null);
		this.setReplyEmail(null);
		this.setReplyFullname(null);
	}

    private boolean showHTMLEditorForDynTag;

    public boolean isShowHTMLEditorForDynTag() {
        return showHTMLEditorForDynTag;
    }

    public void setShowHTMLEditorForDynTag(boolean showHTMLEditorForDynTag) {
        this.showHTMLEditorForDynTag = showHTMLEditorForDynTag;
    }
    
	private MailingContentType mailingContentType;
 
	public MailingContentType getMailingContentType() throws Exception {
		return mailingContentType;
	}

	public void setMailingContentType(MailingContentType mailingContentType) {
		this.mailingContentType = mailingContentType;
	}

	public boolean isMailingContentTypeAdvertising() {
		return mailingContentType == null || mailingContentType == MailingContentType.advertising;
	}

	public void setMailingContentTypeAdvertising(boolean mailingContentTypeAdvertising) {
		mailingContentType = mailingContentTypeAdvertising ? MailingContentType.advertising : MailingContentType.transaction;
	}

    public int getNewModuleTargetID() {
        return newModuleTargetID;
    }

    public void setNewModuleTargetID(int newModuleTargetID) {
        this.newModuleTargetID = newModuleTargetID;
    }

    private int viewTargetId;

    public int getViewTargetId() {
        return viewTargetId;
    }

    public void setViewTargetId(int viewTargetId) {
        this.viewTargetId = viewTargetId;
    }
}
