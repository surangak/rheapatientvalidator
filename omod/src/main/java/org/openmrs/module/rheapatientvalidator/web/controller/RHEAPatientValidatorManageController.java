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
package org.openmrs.module.rheapatientvalidator.web.controller;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifierType;
import org.openmrs.api.context.Context;
import org.openmrs.module.rheapatientvalidator.api.RHEAPatientValidatorService;
import org.openmrs.module.rheashradapter.api.LogEncounterService;
import org.openmrs.module.rheashradapter.model.PostEncounterLog;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v25.message.ORU_R01;
import ca.uhn.hl7v2.model.v25.segment.PID;
import ca.uhn.hl7v2.parser.EncodingNotSupportedException;
import ca.uhn.hl7v2.parser.GenericParser;

/**
 * The main controller.
 */
@Controller
public class RHEAPatientValidatorManageController {
	
	protected final Log log = LogFactory.getLog(getClass());
	
	@RequestMapping(value = "/module/rheapatientvalidator/manage", method = RequestMethod.GET)
	@ResponseBody
	public void manage(@RequestParam(value = "fromId", required = false) int idStartRange,
	        @RequestParam(value = "toId", required = false) int idEndRange, HttpServletRequest request,
	        HttpServletResponse response) {
		
		RHEAPatientValidatorService service = Context.getService(RHEAPatientValidatorService.class);
		GenericParser parser = new GenericParser();
		
		String hl7GivenName = null;
		String hl7FamilyName = null;
		ORU_R01 oru = null;
		PID pid = null;
		
		List<PostEncounterLog> postEncounterLogs = service.getPostEncounterLogs(idStartRange, idEndRange);
		
		for (PostEncounterLog postEncounterLog : postEncounterLogs) {
			boolean nameCheck = false;
			boolean genderCheck = false;
			boolean birthdateCheck = false;
			
			System.out.println("===================================== Start =====================================");

			System.out.println("Post Request ID : " + postEncounterLog.getPostRequestId());
			System.out.println("Patient ECID : " + postEncounterLog.getPatientId());
			Patient patient = null;
			List<PatientIdentifierType> identifierTypeList = null;
			
			PatientIdentifierType patientIdentifierType = Context.getPatientService().getPatientIdentifierTypeByName("ECID");
			
			identifierTypeList = new ArrayList<PatientIdentifierType>();
			identifierTypeList.add(patientIdentifierType);
			
			List<Patient> patients = Context.getPatientService().getPatients(null, postEncounterLog.getPatientId(),
			    identifierTypeList, false);
			
			if (patients != null && !patients.isEmpty()) {
				patient = patients.get(0);
				
				System.out.println("Given name from patient object : " + patients.get(0).getPersonName().getGivenName());
				System.out.println("Family name from patient object : " + patients.get(0).getPersonName().getFamilyName());
				
				Message message = null;
				
				if (postEncounterLog.getHl7data() != null) {
					try {
						
						message = parser.parse(postEncounterLog.getHl7data());
						
						oru = (ORU_R01) message;
						pid = oru.getPATIENT_RESULT().getPATIENT().getPID();
						
						hl7GivenName = pid.getPatientName(0).getGivenName().getValue();
						hl7FamilyName = pid.getPatientName(0).getFamilyName().getSurname().getValue();
						
					}
					catch (EncodingNotSupportedException e) {
						e.printStackTrace();
					}
					catch (HL7Exception e) {
						e.printStackTrace();
					}
					
					if (hl7GivenName.equals(patients.get(0).getPersonName().getFamilyName())) {
						System.out.println("*** Error : Patient names do not match ***");
						patient.getPersonName().setGivenName(hl7GivenName);
						patient.getPersonName().setFamilyName(hl7FamilyName);
						nameCheck = true;
						
					} else {
						System.out.println("*** Observation : Patient names match ***");
					}
					
					if (patient.getGender().equals("N/A")) {
						System.out.println("*** Observation : Patient gender *** " + " Existing value "
						        + patient.getGender() + " Recorded value " + pid.getAdministrativeSex().getValue());
						if (pid.getAdministrativeSex().getValue() != null) {
							patient.setGender(pid.getAdministrativeSex().getValue());
							genderCheck = true;
						}
					}
					
					if (pid.getDateTimeOfBirth().getTime() != null) {
						SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
						
						try {
							Date hl7BirthDate = sdf.parse(pid.getDateTimeOfBirth().getTime().getValue());
							
							if (hl7BirthDate.compareTo(patient.getBirthdate()) != 0) {
								System.out.println("*** Error : Patient birth date does not match *** " + "Observed value "
								        + patient.getBirthdate().toString() + " new value " + hl7BirthDate.toString());
								patient.setBirthdate(hl7BirthDate);
								birthdateCheck = true;
							} else {
								System.out.println("*** Observation : Patient birth date does match ***");
								
							}
						}
						catch (ParseException e) {
							e.printStackTrace();
						}
						
					}
					
					if (nameCheck == true || genderCheck == true || birthdateCheck == true) {
						boolean flag = false;
						try {
							
							//If ECID is not preferred, make it so..
							patient.getPatientIdentifier("ECID").setPreferred(true);
							
							Context.getPatientService().savePatient(patient);
						}
						catch (Exception e) {
							flag = true;
							System.out.println(e);
						}
						finally {
							if (flag == false) {
								System.out.println("*** Patient updated successfully ***");
							}
						}
					} else {
						System.out.println("*** Observation : No changes made to Patient ***");
					}
					
				} else {
					System.out.println("*** Observation : Hl7 data not found ***");
				}
			} else {
				System.out.println("*** Observation : Patient not found ***");
				
			}
			System.out.println("===================================== End =====================================");
			
		}
		
	}
	
	/**
	 * @RequestMapping(value = "/module/rheapatientvalidator/manage", method = RequestMethod.GET)
	 *                       public void manage(ModelMap model) { LogEncounterService service =
	 *                       Context .getService(LogEncounterService.class); GenericParser parser =
	 *                       new GenericParser(); String hl7GivenName = null; String hl7FamilyName =
	 *                       null; ORU_R01 oru = null; PID pid = null; List<PostEncounterLog>
	 *                       postEncounterLogs = service.getPostEncounterLogs();
	 *                       for(PostEncounterLog postEncounterLog : postEncounterLogs){ boolean
	 *                       nameCheck = false; boolean genderCheck = false; boolean birthdateCheck
	 *                       = false; System.out.println(
	 *                       "===================================== Start ====================================="
	 *                       ); System.out.println("Post Request ID : " +
	 *                       postEncounterLog.getPostRequestId());
	 *                       System.out.println("Patient ECID : " +
	 *                       postEncounterLog.getPatientId()); Patient patient = null;
	 *                       List<PatientIdentifierType> identifierTypeList = null;
	 *                       PatientIdentifierType patientIdentifierType = Context
	 *                       .getPatientService().getPatientIdentifierTypeByName("ECID");
	 *                       identifierTypeList = new ArrayList<PatientIdentifierType>();
	 *                       identifierTypeList.add(patientIdentifierType); List<Patient> patients =
	 *                       Context.getPatientService().getPatients(null,
	 *                       postEncounterLog.getPatientId(), identifierTypeList, false);
	 *                       if(patients != null && !patients.isEmpty()){ patient = patients.get(0);
	 *                       System.out.println("Given name from patient object : " +
	 *                       patients.get(0).getPersonName().getGivenName());
	 *                       System.out.println("Family name from patient object : "
	 *                       +patients.get(0).getPersonName().getFamilyName()); Message message =
	 *                       null; if(postEncounterLog.getHl7data() != null){ try { message =
	 *                       parser.parse(postEncounterLog.getHl7data()); oru = (ORU_R01) message;
	 *                       pid = oru.getPATIENT_RESULT().getPATIENT().getPID(); hl7GivenName =
	 *                       pid.getPatientName(0).getGivenName().getValue(); hl7FamilyName =
	 *                       pid.getPatientName(0).getFamilyName().getSurname().getValue(); } catch
	 *                       (EncodingNotSupportedException e) { e.printStackTrace(); } catch
	 *                       (HL7Exception e) { e.printStackTrace(); }
	 *                       if(hl7GivenName.equals(patients
	 *                       .get(0).getPersonName().getFamilyName())){
	 *                       System.out.println("*** Error : Patient names do not match ***");
	 *                       patient.getPersonName().setGivenName(hl7GivenName);
	 *                       patient.getPersonName().setFamilyName(hl7FamilyName); nameCheck = true;
	 *                       }else{ System.out.println("*** Observation : Patient names match ***");
	 *                       } if(patient.getGender().equals("N/A")){
	 *                       System.out.println("*** Observation : Patient gender *** " +
	 *                       " Existing value " + patient.getGender() + " Recorded value " +
	 *                       pid.getAdministrativeSex().getValue());
	 *                       if(pid.getAdministrativeSex().getValue() != null){
	 *                       patient.setGender(pid.getAdministrativeSex().getValue()); genderCheck =
	 *                       true; } } if(pid.getDateTimeOfBirth().getTime() != null){
	 *                       SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd"); try { Date
	 *                       hl7BirthDate =
	 *                       sdf.parse(pid.getDateTimeOfBirth().getTime().getValue());
	 *                       if(hl7BirthDate.compareTo(patient.getBirthdate()) != 0){
	 *                       System.out.println("*** Error : Patient birth date does not match *** "
	 *                       + "Observed value " + patient.getBirthdate().toString() + " new value "
	 *                       + hl7BirthDate.toString()); patient.setBirthdate(hl7BirthDate);
	 *                       birthdateCheck = true; }else{
	 *                       System.out.println("*** Observation : Patient birth date does match ***"
	 *                       ); } } catch (ParseException e) { e.printStackTrace(); } } if(nameCheck
	 *                       == true || genderCheck == true || birthdateCheck == true){ boolean flag
	 *                       = false; try{ Context.getPatientService().savePatient(patient);
	 *                       }catch(Exception e){ flag = true; System.out.println(e); }finally{
	 *                       if(flag == false){
	 *                       System.out.println("*** Patient updated successfully ***"); } } }else{
	 *                       System.out.println("*** Observation : No changes made to Patient ***");
	 *                       } }else{
	 *                       System.out.println("*** Observation : Hl7 data not found ***"); }
	 *                       }else{ System.out.println("*** Observation : Patient not found ***"); }
	 *                       System.out.println(
	 *                       "===================================== End ====================================="
	 *                       ); } }
	 **/
}
