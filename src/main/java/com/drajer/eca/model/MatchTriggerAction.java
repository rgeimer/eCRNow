package com.drajer.eca.model;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.apache.commons.collections4.SetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.drajer.cda.CdaEicrGenerator;
import com.drajer.eca.model.EventTypes.JobStatus;
import com.drajer.ecrapp.config.ValueSetSingleton;
import com.drajer.ecrapp.util.ApplicationUtils;
import com.drajer.sof.model.Dstu2FhirData;
import com.drajer.sof.model.FhirData;
import com.drajer.sof.model.LaunchDetails;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ca.uhn.fhir.model.dstu2.composite.CodeableConceptDt;

public class MatchTriggerAction extends AbstractAction {

	private final Logger logger = LoggerFactory.getLogger(MatchTriggerAction.class);
	
	@Override
	public void print() {
		
		logger.info(" **** Printing MatchTriggerAction **** ");
		printBase();
		logger.info(" **** End Printing MatchTriggerAction **** ");
	}
	
	@Override
	public void execute(Object obj) {
		
		logger.info(" Executing Match Trigger Action ");
		
		if(obj instanceof LaunchDetails) {
			
			LaunchDetails details = (LaunchDetails)obj;
			
			ObjectMapper mapper = new ObjectMapper();
			PatientExecutionState state = null;
			
			try {
				state = mapper.readValue(details.getStatus(), PatientExecutionState.class);
				state.getMatchTriggerStatus().setActionId(getActionId());
			} catch (JsonMappingException e1) {

				String msg = "Unable to read/write execution state";
				logger.error(msg);
				e1.printStackTrace();
				
				throw new RuntimeException(msg);
				
			} catch (JsonProcessingException e1) {
				
				String msg = "Unable to read/write execution state";
				logger.error(msg);
				e1.printStackTrace();
				
				throw new RuntimeException(msg);
			}
			
			// Execute the Match Trigger Action, even if it completed, because it could be invoked multiple times from 
			// other EICR Actions.
			logger.info(" Executing Match Trigger Action , Prior Execution State : = " + details.getStatus());
			
			// Call the Trigger Queries.
			if(ActionRepo.getInstance().getTriggerQueryService() != null ) { 
							
				
				logger.info(" Getting necessary data from Trigger Queries ");
				FhirData data = ActionRepo.getInstance().getTriggerQueryService().getData(details, details.getStartDate(), details.getEndDate());
						
				if(data != null && data instanceof Dstu2FhirData) {
							
					Dstu2FhirData dstu2Data = (Dstu2FhirData)data;
					
					// For Match Trigger Action, we expect the following
					// No preConditions;
					// No relatedActions;
					// No timingData
					
					// We only expect to match codes. So get the paths and match the codes.
					if(getTriggerData() != null && getTriggerData().size() > 0) {
						
						// we have triggers to match against COVID Value Sets for now.
						// In the future we will use the specific paths provided by the ersd spec to match.
						
						List<ActionData> codePaths = getTriggerData();
						
						for(ActionData ad: codePaths) {
							
							logger.info(" Need to match Trigger Codes for : " + ad.getPath());
							
							List<CodeableConceptDt> ptCodes = dstu2Data.getCodesForExpression(ad.getPath());
							
							if(ptCodes != null && ptCodes.size() > 0) {
								
								logger.info(" Found a Total # of " + ptCodes.size() + " codes found for Patient." + ad.getPath());
								
								Set<String> codesToMatch = ApplicationUtils.convertCodeableConceptsToString(ptCodes);
								
								Set<String> codesToMatchAgainst = ValueSetSingleton.getInstance().getCovidValueSetsAsString();
								
								logger.info(" Total # of "+ codesToMatchAgainst.size() + " Codes in Trigger Code Value Set for matching");
								
								Set<String> intersection = SetUtils.intersection(codesToMatch, codesToMatchAgainst);
								
								if(intersection != null && intersection.size() > 0) {
									
									logger.info(" Number of Matched Codes = " + intersection.size());
									
									// For Testing purposes until we get test data assume the data has matched and continue processing.
									state.getMatchTriggerStatus().setTriggerMatchStatus(true);
									
									// Hardcoded value set and value set version for CONNECTATHON
									String valueSet = "2.16.840.1.113762.1.4.1146.1123";
									String valuesetVersion = "1";
									
									state.getMatchTriggerStatus().addMatchedCodes(intersection, valueSet, ad.getPath(), valuesetVersion);
									// state.getMatchTriggerStatus().getMatchedCodes().addAll(intersection);
								}
								else {
									
									logger.info(" No Matched codes found for : " + ad.getPath());
									state.getMatchTriggerStatus().setTriggerMatchStatus(false);
								}
								
							}
						}
						
					}
					else {
						
						String msg = "No Trigger Data to match trigger Codes.";
						logger.error(msg);
						
						throw new RuntimeException(msg);
					}
					
					// Job is completed, even if it did not match.
					// The next job has to check the Match Status to see if something needs to be reported, it may elect to run the matching again 
					// because data may be entered late even though the app was launched.
					state.getMatchTriggerStatus().setJobStatus(JobStatus.COMPLETED);
					
					try {
						details.setStatus(mapper.writeValueAsString(state));
					} catch (JsonProcessingException e) {
					
						String msg = "Unable to update execution state";
						logger.error(msg);
						e.printStackTrace();
						
						throw new RuntimeException(msg);
					}
				}
				else {
					
					String msg = "No Fhir Data retrieved to match trigger Codes.";
					logger.error(msg);
					
					throw new RuntimeException(msg);
				}
			}
			else {
				
				String msg = "System Startup Issue, Spring Injection not functioning properly, trigger service is null.";
				logger.error(msg);
				
				throw new RuntimeException(msg);
			}

		}
		else {
			
			String msg = "Invalid Object passed to Execute method, Launch Details expected, found : " + obj.getClass().getName();
			logger.error(msg);
			
			throw new RuntimeException(msg);
			
		}
	}
}
