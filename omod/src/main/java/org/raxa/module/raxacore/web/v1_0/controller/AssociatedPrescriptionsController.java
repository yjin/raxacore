/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.raxa.module.raxacore.web.v1_0.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.openmrs.Concept;
import org.openmrs.Drug;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;
import org.openmrs.module.dss.service.DssService;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.RestUtil;
import org.openmrs.module.webservices.rest.web.annotation.WSDoc;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.openmrs.module.webservices.rest.web.v1_0.controller.BaseRestController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 *
 * @author yanjin
 */
/**
 * Controller for REST web service access to the AssociatedPrescriptions resource.
 */
@Controller
@RequestMapping(value = "/rest/v1/raxacore/associatedprescriptions")
public class AssociatedPrescriptionsController extends BaseRestController {
	
	DssService dssService;
	
	ConceptService conceptService;
	
	Gson gson = new GsonBuilder().serializeNulls().create();
	
	public void initAssociatedPrescriptionsController() {
		dssService = Context.getService(DssService.class);
		conceptService = Context.getConceptService();
	}
	
	/**
	 * Returns the Resource Version
	 */
	private String getResourceVersion() {
		return "1.0";
	}
	
	@RequestMapping(method = RequestMethod.POST)
	@WSDoc("Save new pairs of diagnosis and drug")
	@ResponseBody
	public Object createNewAssociatedPrescriptions(@RequestBody SimpleObject post, HttpServletRequest request,
	        HttpServletResponse response) throws ResponseException {
		initAssociatedPrescriptionsController();
		Concept concept = conceptService.getConceptByUuid(post.get("concept").toString());
		List<LinkedHashMap> objects = (List<LinkedHashMap>) post.get("associatedPrescriptions");
		List<Concept> concepts = new ArrayList<Concept>();
		for (int i = 0; i < objects.size(); i++) {
			concepts.add(conceptService.getConceptByUuid(objects.get(i).get("concept").toString()));
		}
		
		boolean created = dssService.addAssociatedPrescriptions(concept, concepts);
		return RestUtil.created(response, created);
		
	}
	
	@RequestMapping(method = RequestMethod.GET, params = "q")
	@WSDoc("Gets associated drugs for the given concept uuid")
	@ResponseBody()
	public String getAssociatedPrescriptionsByConcept(@RequestParam Map<String, String> params, HttpServletRequest request)
	        throws ResponseException {
		initAssociatedPrescriptionsController();
		
		List<Drug> drugs = dssService.getRecommendedDrugsbyObservationConcept(conceptService.getConceptByUuid(params
		        .get("q")));
		ArrayList results = new ArrayList();
		for (Drug drug : drugs) {
			results.add(getDrugAsSimpleObject(drug));
		}
		return gson.toJson(new SimpleObject().add("results", results));
	}
	
	/**
	 * Returns a SimpleObject containing some fields of Drug
	 *
	 * @param drug
	 * @return
	 */
	private SimpleObject getDrugAsSimpleObject(Drug drug) {
		SimpleObject obj = new SimpleObject();
		obj.add("uuid", drug.getUuid());
		obj.add("name", drug.getName());
		obj.add("description", drug.getDescription());
		obj.add("minimumDailyDose", drug.getMinimumDailyDose());
		obj.add("maximumDailyDose", drug.getMaximumDailyDose());
		if (drug.getDosageForm() != null) {
			obj.add("dosageForm", drug.getDosageForm().getName().getName());
		}
		obj.add("strength", drug.getDoseStrength());
		obj.add("units", drug.getUnits());
		obj.add("combination", drug.getCombination());
		obj.add("concept", drug.getConcept().getUuid());
		obj.add("fullName", drug.getFullName(Context.getLocale()));
		return obj;
	}
	
}
