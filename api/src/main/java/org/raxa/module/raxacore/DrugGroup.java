package org.raxa.module.raxacore;

/**
 * Copyright 2012, Raxa
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
import java.io.Serializable;
import java.util.Set;
import java.util.HashSet;
import org.openmrs.BaseOpenmrsMetadata;
import org.openmrs.Drug;

/**
 * PatientList stores the data needed to lists patients for registration,
 * screener, etc.
 */
public class DrugGroup extends BaseOpenmrsMetadata implements Serializable {
	
	private Integer id;
	
	private Set<Drug> drugs = new HashSet<Drug>(0);
	
	public DrugGroup() {
	}
	
	/**
	 * Sets id
	 *
	 * @param id: id to set
	 */
	@Override
	public void setId(Integer id) {
		this.id = id;
	}
	
	/**
	 * Gets id
	 *
	 * @return the patientListId
	 */
	@Override
	public Integer getId() {
		return id;
	}
	
	/**
	 * @return the drugs
	 */
	public Set<Drug> getDrugs() {
		return drugs;
	}
	
	/**
	 * @param drugs the drugs to set
	 */
	public void setDrugs(Set<Drug> drugs) {
		this.drugs = drugs;
	}
	
	/**
	 * Compares two PatientList objects for similarity
	 *
	 * @param obj PatientList object to compare to
	 * @return boolean true/false whether or not they are the same objects
	 * @see java.lang.Object#equals(java.lang.Object) @should equal PatientList
	 * with same patientListId @should not equal PatientList with different
	 * patientListId @should not equal on null @should have equal patientList
	 * objects with no patientListIds @should not have equal PatientList objects
	 * when one has null patientListId
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof DrugGroup) {
			DrugGroup pList = (DrugGroup) obj;
			if (this.getId() != null && pList.getId() != null) {
				return (this.getId().equals(pList.getId()));
			}
		}
		return this == obj;
	}
	
	/**
	 * @see java.lang.Object#hashCode() @should have same hashcode when equal
	 * @should have different hash code when not equal @should get hash code
	 * with null attributes
	 */
	@Override
	public int hashCode() {
		if (this.getId() == null) {
			return super.hashCode();
		}
		return this.getId().hashCode();
	}
	
}
