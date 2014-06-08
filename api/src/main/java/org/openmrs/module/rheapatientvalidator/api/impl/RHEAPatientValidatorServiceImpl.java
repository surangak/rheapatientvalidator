/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.rheapatientvalidator.api.impl;

import java.util.List;

import org.openmrs.api.impl.BaseOpenmrsService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.rheapatientvalidator.api.RHEAPatientValidatorService;
import org.openmrs.module.rheapatientvalidator.api.db.RHEAPatientValidatorDAO;
import org.openmrs.module.rheashradapter.model.PostEncounterLog;

/**
 * It is a default implementation of {@link RHEAPatientValidatorService}.
 */
public class RHEAPatientValidatorServiceImpl extends BaseOpenmrsService implements RHEAPatientValidatorService {
	
	protected final Log log = LogFactory.getLog(this.getClass());
	
	private RHEAPatientValidatorDAO dao;
	
	/**
     * @param dao the dao to set
     */
    public void setDao(RHEAPatientValidatorDAO dao) {
	    this.dao = dao;
    }
    
    /**
     * @return the dao
     */
    public RHEAPatientValidatorDAO getDao() {
	    return dao;
    }

	@Override
    public List<PostEncounterLog> getPostEncounterLogs(int fromId, int toId) {
	    return dao.getPostEncounterLogs(fromId, toId);
    }
	
}