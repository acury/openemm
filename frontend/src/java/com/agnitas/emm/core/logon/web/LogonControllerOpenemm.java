package com.agnitas.emm.core.logon.web;

import org.agnitas.emm.core.commons.util.ConfigService;
import org.agnitas.emm.core.logintracking.service.LoginTrackService;
import org.agnitas.service.UserActivityLogService;
import org.agnitas.service.WebStorage;
import org.springframework.stereotype.Controller;

import com.agnitas.emm.core.admin.service.AdminService;
import com.agnitas.emm.core.logon.service.ClientHostIdService;
import com.agnitas.emm.core.logon.service.ComHostAuthenticationService;
import com.agnitas.emm.core.logon.service.ComLogonService;

@Controller
public class LogonControllerOpenemm extends LogonControllerBasic {
    public LogonControllerOpenemm(ComLogonService logonService, LoginTrackService loginTrackService, ComHostAuthenticationService hostAuthenticationService, WebStorage webStorage, ConfigService configService, UserActivityLogService userActivityLogService, final ClientHostIdService clientHostIdService, final AdminService adminService) {
    	super(logonService, loginTrackService, hostAuthenticationService, webStorage, configService, userActivityLogService, clientHostIdService, adminService);
    }
}
