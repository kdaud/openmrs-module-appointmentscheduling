package org.openmrs.module.appointment.web.controller;

import java.util.Date;

import javax.servlet.http.HttpServletRequest;

import org.openmrs.Patient;
import org.openmrs.Visit;
import org.openmrs.api.context.Context;
import org.openmrs.module.appointment.Appointment;
import org.openmrs.module.appointment.api.AppointmentService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PatientDashboardAppointmentExtController {
	
	@RequestMapping("/module/appointment/patientDashboardAppointmentExt.form")
	public String showForm(HttpServletRequest request, @RequestParam("patientId") Integer patientId,
	        @RequestParam("action") String action) {
		//End the consulation
		if (action.equals("endConsult")) {
			Patient patient = Context.getPatientService().getPatient(patientId);
			Appointment appointment = Context.getService(AppointmentService.class).getLastAppointment(patient);
			Visit visit = appointment.getVisit();
			Context.getVisitService().endVisit(visit, new Date());
			Context.getVisitService().saveVisit(visit);
			//TODO change to use enum
			appointment.setStatus("Completed");
			Context.getService(AppointmentService.class).saveAppointment(appointment);
			
			return "redirect:/patientDashboard.form?patientId=" + patientId;
		}
		//Schedule new appointment
		else {
			return "redirect:/module/appointment/appointmentForm.form?patientId=" + patientId;
		}
	}
}
