/*

    Copyright (C) 2022 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package org.agnitas.dao;

import com.agnitas.beans.ComTrackableLink;
import com.agnitas.beans.TrackableLinkListItem;
import org.agnitas.emm.core.velocity.VelocityCheck;

import java.util.List;
import java.util.Map;

public interface TrackableLinkDao {
   
    /**
     * Getter for property trackableLink by link id and company id.
     *
     * @return Value of trackableLink.
     */
    ComTrackableLink getTrackableLink(int linkID, @VelocityCheck int companyID);
    
    /**
     * Getter for property trackableLink by link id and company id.
     *
     * @return Value of trackableLink.
     */
    ComTrackableLink getTrackableLink(String url, @VelocityCheck int companyID, int mailingID);

    /**
     * Saves trackableLink.
     *
     * @return Saved trackableLink id.
     * @param link
     */
    int saveTrackableLink(ComTrackableLink link);
    
    void batchSaveTrackableLinks(@VelocityCheck int companyID, int mailingId, Map<String, ComTrackableLink> trackableLinksMap, boolean removeUnusedLinks);

	List<TrackableLinkListItem> listTrackableLinksForMailing(@VelocityCheck int companyID, int mailingID);

    boolean isTrackingOnEveryPositionAvailable(@VelocityCheck int companyId, int mailingId);
}
