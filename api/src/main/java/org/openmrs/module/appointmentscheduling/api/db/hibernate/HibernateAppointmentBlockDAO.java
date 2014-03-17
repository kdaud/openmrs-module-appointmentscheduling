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
package org.openmrs.module.appointmentscheduling.api.db.hibernate;

import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.criterion.Conjunction;
import org.hibernate.criterion.Disjunction;
import org.hibernate.criterion.Restrictions;
import org.openmrs.Provider;
import org.openmrs.api.LocationService;
import org.openmrs.api.context.Context;
import org.openmrs.module.appointmentscheduling.AppointmentBlock;
import org.openmrs.module.appointmentscheduling.AppointmentType;
import org.openmrs.module.appointmentscheduling.api.db.AppointmentBlockDAO;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class HibernateAppointmentBlockDAO extends HibernateSingleClassDAO implements AppointmentBlockDAO {
	
	public HibernateAppointmentBlockDAO() {
		super(AppointmentBlock.class);
	}
	
	/**
	 * Returns the appointment blocks corresponding to the given date interval and location.
	 * 
	 * @param fromDate the lower bound of the date interval.
	 * @param toDate the upper bound of the date interval.
	 * @param locations the of locations to filter by.
	 * @param provider the provider to filter by.
	 * @param appointment type the type of appointment to filter by.
	 * @return the appointment blocks that is on the given date interval and locations.
	 */
	@Override
	@Transactional(readOnly = true)
	public List<AppointmentBlock> getAppointmentBlocks(Date fromDate, Date toDate, String locations, Provider provider,
	        AppointmentType appointmentType) {
		List<AppointmentBlock> filteredAppointmentBlocks = null;
		Criteria criteria = sessionFactory.getCurrentSession().createCriteria(AppointmentBlock.class);
		
		criteria.add(Restrictions.eq("voided", false)); // we only want voided appointment blocks
		
		if (locations != null && !locations.isEmpty()) {
			String[] locationsAsArray = locations.split(",");
			Disjunction disjunction = Restrictions.disjunction();
			LocationService locationService = Context.getLocationService();
			for (int i = 0; i < locationsAsArray.length; i++) {
				disjunction.add(Restrictions.eq("location",
				    locationService.getLocation(Integer.parseInt(locationsAsArray[i]))));
			}
			criteria.add(disjunction);
		}
		if (fromDate != null) {
			criteria.add(Restrictions.ge("startDate", fromDate));
		}
		if (toDate != null) {
			criteria.add(Restrictions.le("endDate", toDate));
		}
		if (provider != null) {
			criteria.add(Restrictions.eq("provider.id", provider.getProviderId()));
		}
		
		List<AppointmentBlock> appointmentBlocks = criteria.list();
		if (appointmentType != null) {
			filteredAppointmentBlocks = new ArrayList<AppointmentBlock>();
			String stringQuery = "SELECT appointmentBlock FROM AppointmentBlock AS appointmentBlock WHERE :appointmentType IN elements(appointmentBlock.types) AND voided = 0 ORDER BY appointmentBlock.startDate, appointmentBlock.endDate, appointmentBlock.provider";
			Query query = super.sessionFactory.getCurrentSession().createQuery(stringQuery)
			        .setParameter("appointmentType", appointmentType);
			List<AppointmentBlock> appointmentBlocksFilteredByType = query.list();
			for (AppointmentBlock appointmentBlockContainsType : appointmentBlocksFilteredByType) {
				for (AppointmentBlock appointmentBlock : appointmentBlocks) {
					if (appointmentBlock.getId().equals(appointmentBlockContainsType.getId())) {
						//Intersection 
						filteredAppointmentBlocks.add(appointmentBlock);
					}
				}
			}
		} else
			filteredAppointmentBlocks = appointmentBlocks;
		
		return filteredAppointmentBlocks;
	}
	
	/**
	 * Returns the overlapping appointment blocks to the given appointment block.
	 * 
	 * @param appointmentBlock is the appointment block for which we want to test overlap.
	 * @return the appointment blocks that overlaps to the given appointment block.
	 */
	@SuppressWarnings("unchecked")
	@Override
	@Transactional(readOnly = true)
	public List<AppointmentBlock> getOverlappingAppointmentBlocks(AppointmentBlock appointmentBlock) {
		Criteria criteria = sessionFactory.getCurrentSession().createCriteria(AppointmentBlock.class);
		Disjunction disjunction = Restrictions.disjunction();
		if (appointmentBlock != null) {
			Date fromDate = appointmentBlock.getStartDate();
			Date toDate = appointmentBlock.getEndDate();
			if (fromDate != null && toDate != null) {
				//let givenAppointmentBlock.startDate = fromDate, givenAppointmentBlock.endDate = toDate.
				//let checkedAppointmentBlock.startDate = fromDate' , checkedAppointmentBlock.endDate = toDate'.
				
				//1) create the conjunction - (fromDate>=fromDate' AND fromDate<toDate') 
				Conjunction conjunction = Restrictions.conjunction();
				conjunction.add(Restrictions.le("startDate", fromDate));
				conjunction.add(Restrictions.gt("endDate", fromDate));
				//add the conjunction to the disjunction
				disjunction.add(conjunction);
				//2) create the conjunction - (fromDate<fromDate' AND toDate>fromDate')
				conjunction = Restrictions.conjunction();
				conjunction.add(Restrictions.gt("startDate", fromDate));
				conjunction.add(Restrictions.lt("startDate", toDate));
				//add the conjunction to the disjunction
				disjunction.add(conjunction); //the disjunction - (fromDate>=fromDate' AND fromDate<toDate') OR (fromDate<fromDate' AND toDate>fromDate')
				criteria.add(disjunction);
				
				//restriction for the provider
				criteria.add(Restrictions.eq("provider", appointmentBlock.getProvider()));
				if (appointmentBlock.getAppointmentBlockId() != null) {
					//restriction for not comparing the same appointment blocks
					criteria.add(Restrictions.ne("appointmentBlockId", appointmentBlock.getAppointmentBlockId()));
				}
				//restriction for ignoring "voided" appointment blocks
				criteria.add(Restrictions.eq("voided", false));
				
				return criteria.list();
			}
		}
		return new ArrayList<AppointmentBlock>();
	}
}
