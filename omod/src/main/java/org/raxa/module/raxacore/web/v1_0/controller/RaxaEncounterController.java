package org.raxa.module.raxacore.web.v1_0.controller;

/**
 * Copyright 2012, Raxa
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.ConceptNumeric;
import org.openmrs.DrugOrder;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.api.EncounterService;
import org.openmrs.api.context.Context;
import org.openmrs.logic.result.Result;
import org.openmrs.module.dss.hibernateBeans.Rule;
import org.openmrs.module.dss.service.DssService;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.ConversionUtil;
import org.openmrs.module.webservices.rest.web.RestUtil;
import org.openmrs.module.webservices.rest.web.annotation.WSDoc;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.openmrs.module.webservices.rest.web.v1_0.controller.BaseRestController;
import org.openmrs.util.OpenmrsConstants;
import org.raxa.module.raxacore.RaxaAlert;
import org.raxa.module.raxacore.RaxaAlertService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Controller for REST web service access to the Encounter resource.
 */
@Controller
@RequestMapping(value = "/rest/v1/raxacore/encounter")
public class RaxaEncounterController extends BaseRestController {
	
	Log log = LogFactory.getLog(getClass());
	
	EncounterService service;
	
	DssService dssService;
	
	RaxaAlertService raxaAlertService;
	
	SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
	
	Gson gson = new GsonBuilder().serializeNulls().create();
	
	private static final String[] REQUIRED_FIELDS = { "encounterDatetime", "patient", "encounterType" };
	
	private static final String[] DATE_FORMATS = { "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
	        "yyyy-MM-dd'T'HH:mm:ss.SSS", "EEE MMM d yyyy HH:mm:ss zZzzzz", "yyyy-MM-dd'T'HH:mm:ssZ",
	        "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd" };
	
	public void initEncounterController() {
		service = Context.getEncounterService();
	}
	
	private void validatePost(SimpleObject post) throws ResponseException {
		for (int i = 0; i < REQUIRED_FIELDS.length; i++) {
			if (post.get(REQUIRED_FIELDS[i]) == null) {
				throw new ResponseException(
				                            "Required field " + REQUIRED_FIELDS[i] + " not found") {};
			}
		}
	}
	
	/**
	 * Create new encounter by POST'ing at least name and description property in the request body.
	 *
	 * @param post the body of the POST request
	 * @param request
	 * @param response
	 * @return 201 response status and Encounter object
	 * @throws ResponseException
	 */
	@RequestMapping(method = RequestMethod.POST)
	@WSDoc("Save New Encounter")
	@ResponseBody
	public Object createNewEncounter(@RequestBody SimpleObject post, HttpServletRequest request, HttpServletResponse response)
	        throws ResponseException {
		initEncounterController();
		validatePost(post);
		Encounter encounter = createEncounterFromPost(post);
		evaluateRules(encounter);
		return RestUtil.created(response, getEncounterAsSimpleObject(encounter));
	}
	
	/**
	 * Creates an encounter based on fields in the post object
	 *
	 * @param post
	 * @return
	 */
	private Encounter createEncounterFromPost(SimpleObject post) throws ResponseException {
		Encounter encounter = new Encounter();
		encounter.setPatient(Context.getPatientService().getPatientByUuid(post.get("patient").toString()));
		for (int i = 0; i < DATE_FORMATS.length; i++) {
			try {
				Date date = new SimpleDateFormat(DATE_FORMATS[i]).parse(post.get("encounterDatetime").toString());
				//Openmrs doesn't allow future encounters
				if (date.after(new Date())) {
					encounter.setEncounterDatetime(new Date());
					break;
				} else {
					encounter.setEncounterDatetime(date);
					break;
				}
			}
			catch (Exception ex) {}
		}
		encounter.setEncounterType(service.getEncounterTypeByUuid(post.get("encounterType").toString()));
		if (post.get("location") != null) {
			encounter.setLocation(Context.getLocationService().getLocationByUuid(post.get("location").toString()));
		}
		if (post.get("provider") != null) {
			encounter.setProvider(Context.getPersonService().getPersonByUuid(post.get("provider").toString()));
		} //if no provider is given in the post, set as the current user
		else {
			encounter.setProvider(Context.getAuthenticatedUser().getPerson());
		}
		Encounter newEncounter = service.saveEncounter(encounter);
		if (post.get("obs") != null) {
			createObsFromPost(post, newEncounter);
		}
		if (post.get("orders") != null) {
			createOrdersFromPost(post, newEncounter);
		}
		return encounter;
	}
	
	/**
	 * Creates and saves the obs from the given post
	 *
	 * @param post
	 * @param encounter
	 */
	private void createObsFromPost(SimpleObject post, Encounter encounter) throws ResponseException {
		List<LinkedHashMap> obsObjects = (List<LinkedHashMap>) post.get("obs");
		List<Obs> encounterObs = new ArrayList();
		for (int i = 0; i < obsObjects.size(); i++) {
			Obs obs = new Obs();
			obs.setPerson(encounter.getPatient());
			obs.setConcept(Context.getConceptService().getConceptByUuid(obsObjects.get(i).get("concept").toString()));
			obs.setObsDatetime(encounter.getEncounterDatetime());
			if (encounter.getLocation() != null) {
				obs.setLocation(encounter.getLocation());
			}
			obs.setEncounter(encounter);
			if (obsObjects.get(i).get("value") != null) {
				setObsValue(obs, obsObjects.get(i).get("value").toString());
			}
			if (obsObjects.get(i).get("comment") != null) {
				obs.setComment(obsObjects.get(i).get("comment").toString());
			}
			encounter.addObs(obs);
			//Context.getObsService().saveObs(obs, "saving new obs");
		}
	}
	
	private Obs setObsValue(Obs obs, Object value) throws ResponseException {
		if (obs.getConcept().getDatatype().isCoded()) {
			// setValueAsString is not implemented for coded obs (in core)
			Concept valueCoded = (Concept) ConversionUtil.convert(value, Concept.class);
			obs.setValueCoded(valueCoded);
		} else if (obs.getConcept().getDatatype().isComplex()) {
			obs.setValueComplex(value.toString());
		} else {
			if (obs.getConcept().isNumeric()) {
				//get the actual persistent object rather than the hibernate proxy
				ConceptNumeric concept = Context.getConceptService().getConceptNumeric(obs.getConcept().getId());
				String units = concept.getUnits();
				if (StringUtils.isNotBlank(units)) {
					String originalValue = value.toString().trim();
					if (originalValue.endsWith(units)) {
						value = originalValue.substring(0, originalValue.indexOf(units)).trim();
					} else {
						//check that that this value has no invalid units
						try {
							Double.parseDouble(originalValue);
						}
						catch (NumberFormatException e) {
							throw new ResponseException(
							                            originalValue + " has invalid units", e) {};
						}
					}
				}
			}
			try {
				if (obs.getConcept().getDatatype().getHl7Abbreviation().equals("ZZ")) {
					obs.setValueText(value.toString());
				} else {
					obs.setValueAsString(value.toString());
				}
			}
			catch (Exception e) {
				throw new ResponseException(
				                            "Unable to convert obs value " + e.getMessage()) {};
			}
		}
		return obs;
	}
	
	/**
	 * Creates and saves the orders from the given post
	 *
	 * @param post
	 * @param encounter
	 */
	private void createOrdersFromPost(SimpleObject post, Encounter encounter) throws ResponseException {
		List<LinkedHashMap> orderObjects = (List<LinkedHashMap>) post.get("orders");
		for (int i = 0; i < orderObjects.size(); i++) {
			//only setting drug orders now
			DrugOrder order = new DrugOrder();
			order.setPatient(encounter.getPatient());
			order.setConcept(Context.getConceptService().getConceptByUuid(orderObjects.get(i).get("concept").toString()));
			order.setOrderType(Context.getOrderService().getOrderType(OpenmrsConstants.ORDERTYPE_DRUG));
			if (orderObjects.get(i).get("instructions") != null) {
				order.setInstructions(orderObjects.get(i).get("instructions").toString());
			}
			if (orderObjects.get(i).get("drug") != null) {
				order.setDrug(Context.getConceptService().getDrugByUuid(orderObjects.get(i).get("drug").toString()));
			}
			if (orderObjects.get(i).get("instructions") != null) {
				order.setInstructions(orderObjects.get(i).get("instructions").toString());
			}
			if (orderObjects.get(i).get("frequency") != null) {
				order.setFrequency(orderObjects.get(i).get("frequency").toString());
			}
			if (orderObjects.get(i).get("quantity") != null) {
				order.setQuantity((Integer) orderObjects.get(i).get("quantity"));
			}
			if (orderObjects.get(i).get("dose") != null) {
				order.setDose((Double) orderObjects.get(i).get("dose"));
			}
			if (orderObjects.get(i).get("startDate") != null) {
				for (int j = 0; j < DATE_FORMATS.length; j++) {
					try {
						Date date = new SimpleDateFormat(DATE_FORMATS[j]).parse(orderObjects.get(i).get("startDate")
						        .toString());
						order.setStartDate(date);
					}
					catch (Exception ex) {}
				}
			}
			if (orderObjects.get(i).get("autoExpireDate") != null) {
				for (int j = 0; j < DATE_FORMATS.length; j++) {
					try {
						Date date = new SimpleDateFormat(DATE_FORMATS[j]).parse(orderObjects.get(i).get("autoExpireDate")
						        .toString());
						order.setAutoExpireDate(date);
					}
					catch (Exception ex) {}
				}
			}
			order.setEncounter(encounter);
			if (encounter.getProvider() != null) {
				order.setOrderer(Context.getAuthenticatedUser());
			}
			Context.getOrderService().saveOrder(order);
		}
	}
	
	/**
	 * Evaluate rules for a given patient
	 *
	 * @param patient
	 */
	private void evaluateRules(Encounter encounter) {
		
		if (encounter == null) {
			throw new IllegalArgumentException("Encounter cannot be null");
		}
		
		Patient patient = encounter.getPatient();
		
		if (patient == null) {
			throw new IllegalArgumentException("Patient cannot be null");
		}
		
		if (dssService == null) {
			log.debug("Fetching DSS service...");
			try {
				dssService = Context.getService(DssService.class);
			}
			catch (Exception e) {
				log.error("Could not get the DSS service. I will not proceed.");
				return;
			}
		}
		
		log.debug("Going to get and execute rules...");
		List<Rule> rules = dssService.getPrioritizedRulesByConceptsInEncounter(encounter);
		List<Result> results = dssService.runRules(patient, rules);
		HashSet<Result> resultSet = new HashSet<Result>();
		for (Result currResult : results) {
			resultSet.add(currResult);
		}
		
		// finally, create alerts if needed
		for (Result currResult : resultSet) {
			createAlertsForResult(currResult, patient);
		}
	}
	
	private void createAlertsForResult(Result result, Patient patient) {
		if (result == null) {
			return;
		}
		// create alert
		RaxaAlert alert = new RaxaAlert();
		// TODO: what to put in here?
		// result.toString() returns the string in the "Actions"
		// section of the MLM file if it had concluded true
		alert.setDescription(result.toString());
		alert.setName(result.toString());
		alert.setPatient(patient);
		alert.setTime(new Date());
		
		// TODO: which type?
		alert.setAlertType("DSS");
		
		// TODO: which provider?
		// Provider provider = Context.getProviderService().getProviderByUuid("73bbb069-9781-4afc-a9d1-54b6b2270e05");
		Provider provider = null;
		List<Provider> providers = Context.getProviderService().getAllProviders();
		if (providers.size() > 0) {
			provider = providers.get(0);
		}
		
		if (provider == null) {
			throw new IllegalArgumentException("Provider cannot be null");
		}
		alert.setProviderSent(provider);
		alert.setProviderSentId(provider.getId());
		// save it
		try {
			if (raxaAlertService == null) {
				raxaAlertService = Context.getService(RaxaAlertService.class);
			}
			log.info("Going to save alert...");
			raxaAlertService.saveRaxaAlert(alert);
		}
		catch (Exception e) {
			log.error("Could not save RaxaAlert. Here is what I know: " + e.toString());
		}
	}
	
	/**
	 * Get the encounter as FULL representation
	 *
	 * @param uuid
	 * @param rep
	 * @param request
	 * @return
	 * @throws ResponseException
	 */
	@RequestMapping(value = "/{uuid}", method = RequestMethod.GET)
	@WSDoc("Gets Full representation of Encounter for the uuid path")
	@ResponseBody()
	public String getEncounterByUuidFull(@PathVariable("uuid") String uuid, HttpServletRequest request)
	        throws ResponseException {
		initEncounterController();
		Encounter encounter = service.getEncounterByUuid(uuid);
		SimpleObject obj = getEncounterAsSimpleObject(encounter);
		return gson.toJson(obj);
	}
	
	/**
	 * Get the encounters by provider
	 *
	 * @param uuid
	 * @param rep
	 * @param request
	 * @return
	 * @throws ResponseException
	 */
	@RequestMapping(method = RequestMethod.GET, params = "provider")
	@WSDoc("Gets Full representation of Encounter for the uuid path")
	@ResponseBody()
	public String getEncountersByProvider(@RequestParam Map<String, String> params, HttpServletRequest request)
	        throws ResponseException {
		initEncounterController();
		List<Provider> providers = new ArrayList();
		providers.add(Context.getProviderService().getProviderByUuid(params.get("provider")));
		List<Encounter> encounters = service.getEncounters(null, null, null, null, null, null, providers, null, null, true);
		ArrayList results = new ArrayList();
		for (Encounter encounter : encounters) {
			results.add(getEncounterAsSimpleObject(encounter));
		}
		return gson.toJson(new SimpleObject().add("results", results));
	}
	
	/**
	 * Returns a SimpleObject containing some fields of Drug
	 *
	 * @param drug
	 * @return
	 */
	private SimpleObject getEncounterAsSimpleObject(Encounter encounter) {
		SimpleObject obj = new SimpleObject();
		obj.add("uuid", encounter.getUuid());
		obj.add("display", encounter.toString());
		obj.add("encounterDatetime", encounter.getEncounterDatetime());
		if (encounter.getPatient() != null) {
			SimpleObject patientObj = new SimpleObject();
			patientObj.add("uuid", encounter.getPatient().getUuid());
			patientObj.add("display", encounter.getPatient().getPersonName().getFullName());
			obj.add("patient", patientObj);
		}
		if (encounter.getLocation() != null) {
			SimpleObject locationObj = new SimpleObject();
			locationObj.add("uuid", encounter.getLocation().getUuid());
			locationObj.add("display", encounter.getLocation().getDisplayString());
			obj.add("location", locationObj);
		}
		SimpleObject encounterTypeObj = new SimpleObject();
		encounterTypeObj.add("uuid", encounter.getEncounterType().getUuid());
		encounterTypeObj.add("display", encounter.getEncounterType().getName());
		obj.add("encounterType", encounterTypeObj);
		Set<Obs> obs = encounter.getObs();
		if (!encounter.getObs().isEmpty()) {
			ArrayList obsObjects = new ArrayList();
			Iterator<Obs> obsIter = obs.iterator();
			while (obsIter.hasNext()) {
				Obs currentObs = obsIter.next();
				obsObjects.add(createObjectFromObs(currentObs));
			}
			obj.add("obs", obsObjects);
		}
		Set<Order> orders = encounter.getOrders();
		if (!orders.isEmpty()) {
			ArrayList orderObjects = new ArrayList();
			Iterator<Order> orderIter = orders.iterator();
			while (orderIter.hasNext()) {
				Order currentOrder = orderIter.next();
				orderObjects.add(createObjectFromOrder(currentOrder));
			}
			obj.add("orders", orderObjects);
		}
		return obj;
	}
	
	/**
	 * Helper function to add an order to simpleobject for returning over REST
	 *
	 * @param obj
	 * @param order
	 * @return
	 */
	private SimpleObject createObjectFromOrder(Order order) {
		SimpleObject newOrderObject = new SimpleObject();
		newOrderObject.add("uuid", order.getUuid());
		if (order.getOrderType() != null) {
			SimpleObject orderType = new SimpleObject();
			orderType.add("uuid", order.getOrderType().getUuid());
			orderType.add("display", order.getOrderType().getName());
			newOrderObject.add("orderType", orderType);
		}
		SimpleObject orderConcept = new SimpleObject();
		orderConcept.add("uuid", order.getConcept().getUuid());
		orderConcept.add("display", order.getConcept().getName().getName());
		newOrderObject.add("concept", orderConcept);
		if (order.isDrugOrder()) {
			DrugOrder currentDrugOrder = (DrugOrder) order;
			newOrderObject.add("instructions", currentDrugOrder.getInstructions());
			newOrderObject.add("startDate", currentDrugOrder.getStartDate().toString());
			newOrderObject.add("autoExpireDate", currentDrugOrder.getAutoExpireDate().toString());
			newOrderObject.add("dose", currentDrugOrder.getDose());
			newOrderObject.add("units", currentDrugOrder.getUnits());
			newOrderObject.add("frequency", currentDrugOrder.getFrequency());
			newOrderObject.add("quantity", currentDrugOrder.getQuantity());
			SimpleObject drugObj = new SimpleObject();
			drugObj.add("uuid", currentDrugOrder.getDrug().getUuid());
			drugObj.add("display", currentDrugOrder.getDrug().getName());
			newOrderObject.add("drug", drugObj);
		}
		return newOrderObject;
	}
	
	/**
	 * Helper function to add an obs to simpleobject for returning over REST
	 *
	 * @param obj
	 * @param order
	 * @return
	 */
	private SimpleObject createObjectFromObs(Obs obs) {
		SimpleObject newObsObject = new SimpleObject();
		newObsObject.add("uuid", obs.getUuid());
		newObsObject.add("obsDatetime", df.format(obs.getObsDatetime()));
		newObsObject.add("value", obs.getValueAsString(Locale.ENGLISH));
		newObsObject.add("comment", obs.getComment());
		if (obs.getOrder() != null) {
			newObsObject.add("order", obs.getOrder().getUuid());
		} else {
			newObsObject.add("order", null);
		}
		return newObsObject;
	}
}
