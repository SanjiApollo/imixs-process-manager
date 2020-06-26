/*******************************************************************************
 *  Imixs Workflow Technology
 *  Copyright (C) 2003, 2008 Imixs Software Solutions GmbH,  
 *  http://www.imixs.com
 *  
 *  This program is free software; you can redistribute it and/or 
 *  modify it under the terms of the GNU General Public License 
 *  as published by the Free Software Foundation; either version 2 
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful, 
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of 
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU 
 *  General Public License for more details.
 *  
 *  You can receive a copy of the GNU General Public
 *  License at http://www.gnu.org/licenses/gpl.html
 *  
 *  Contributors:  
 *  	Imixs Software Solutions GmbH - initial API and implementation
 *  	Ralph Soika
 *  
 *******************************************************************************/
package org.imixs.application.ui.form;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ConversationScoped;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import javax.inject.Named;

import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.faces.data.WorkflowController;

/**
 * The ChronicleController collects all chronicle data
 * 
 * history, versions, comments, references, documents
 * 
 * <p>
 * Each chronic entry for a workitme consists of the following data items:
 * <ul>
 * 
 * - type : date|history|file|version|
 * 
 * 
 * <p>
 * With the method 'toggleFilter' the chronicle list can be filter to a specific
 * type. This method call recalculates also the time data.
 * 
 * @see workitem_chronicle.xhtml
 * @author rsoika,gheinle
 */
@Named
@ConversationScoped
public class ChronicleController implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	@Inject
	protected WorkflowController workflowController;
	private static Logger logger = Logger.getLogger(ChronicleController.class.getName());

	List<ChronicleEntity> originChronicleList;
	List<ChronicleEntity> filteredChronicleList;
	String filter = null;

	Map<Integer, Set<Integer>> yearsMonths;

	
	private DateFormat dateFormat=null;

	@SuppressWarnings("unchecked")
	@PostConstruct
	public void init() {
		long l = System.currentTimeMillis();
		originChronicleList = new ArrayList<ChronicleEntity>();

		yearsMonths = new HashMap<Integer, Set<Integer>>();
		
		try {
            Locale browserLocale = FacesContext.getCurrentInstance().getViewRoot().getLocale();
            ResourceBundle rb = ResourceBundle.getBundle("bundle.messages", browserLocale);
            String sDatePattern = rb.getString("dateTimePattern");
             dateFormat = new SimpleDateFormat(sDatePattern);
        } catch (MissingResourceException mre) {
            dateFormat=null;
        }


		/* collect history */
		List<List<Object>> history = workflowController.getWorkitem().getItemValue("txtworkflowhistory");
		// change order
		Collections.reverse(history);
		// do we have real history entries?
		if (history.size() > 0 && history.get(0) instanceof List) {
			for (List<Object> entries : history) {

				Date date = (Date) entries.get(0);
				String message = (String) entries.get(1);
				String user = (String) entries.get(2);

				ItemCollection entry = new ItemCollection();
				entry.replaceItemValue("date", date);
				entry.replaceItemValue("user", user);
				entry.replaceItemValue("message", message);
				entry.replaceItemValue("type", "history");

				addChronicleEntry(originChronicleList, entry);
			}
		}

		/* collect comments */
		List<Map<String, List<Object>>> comments = workflowController.getWorkitem().getItemValue("txtCommentLog");
		for (Map<String, List<Object>> comment : comments) {

			ItemCollection itemCol = new ItemCollection(comment);
			Date date = itemCol.getItemValueDate("datcomment");
			String message = itemCol.getItemValueString("txtcomment");
			String user = itemCol.getItemValueString("nameditor");

			ItemCollection entry = new ItemCollection();
			entry.replaceItemValue("date", date);
			entry.replaceItemValue("user", user);
			entry.replaceItemValue("message", message);
			entry.replaceItemValue("type", "comment");

			addChronicleEntry(originChronicleList, entry);
		}

	
		// sort chronicles by date.....
		Collections.sort(originChronicleList, new ChronicleEntityComparator(true));

		computeTimeData(originChronicleList);

		// set full filtered list
		filteredChronicleList = new ArrayList<ChronicleEntity>();
		filteredChronicleList.addAll(originChronicleList);

		logger.fine("...init in " + (System.currentTimeMillis() - l) + "ms");
	}

	/**
	 * Returns the current active filter or null if no filter is active. 
	 * @return
	 */
	public String getFilter() {
		return filter;
	}
	
	
	public List<Integer> getYears() {
		Set<Integer> result = yearsMonths.keySet();

		List<Integer> sortedList = new ArrayList<>(result);
		Collections.sort(sortedList);
		Collections.reverse(sortedList);

		return sortedList;
	}

	/**
	 * return months by year descending
	 * 
	 * @param year
	 * @return
	 */
	public List<Integer> getMonths(int year) {
		Set<Integer> result = yearsMonths.get(year);

		if (result==null) {
			// no entries
			return new ArrayList<>();
		}
		
		List<Integer> sortedList = new ArrayList<>(result);
		Collections.sort(sortedList);
		Collections.reverse(sortedList);

		return sortedList;
	}

	/**
	 * Returns all chronical entries by year/month
	 * 
	 * @param year
	 * @param month
	 * @return
	 */
	public List<ChronicleEntity> getChroniclePerMonth(int year, int month) {
		ArrayList<ChronicleEntity> result = new ArrayList<ChronicleEntity>();
		for (ChronicleEntity entry : filteredChronicleList) {
			Date date = entry.getDate();

			LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
			if (month == localDate.getMonthValue() && year == localDate.getYear()) {
				result.add(entry);
			}
		}
		
		logger.finest("......getChroniclePerMonth - found " + result.size() +  " chronicle entities");		
		return result;
	}

	/**
	 * This method call creates a new filtered list of the originChronicl List. and
	 * recalculates also the time data.
	 * 
	 * @param category
	 */
	public void toggleFilter(String category) {
		long l = System.currentTimeMillis();
		logger.finest("......toggleFilter : " + category);

		if (category != null && !category.isEmpty() && category.equals(filter)) {
			// toggle existing category
			filter = null;
		} else {
			filter = category;
		}

		if (filter == null || filter.isEmpty()) {
			filteredChronicleList = new ArrayList<ChronicleEntity>();
			filteredChronicleList.addAll(originChronicleList);
		} else {
			filteredChronicleList = new ArrayList<ChronicleEntity>();
			// build a new filtered list with only data of the given category
			for (ChronicleEntity chronicleEntry : originChronicleList) {
				List<ItemCollection> entries = chronicleEntry.getEntries();
				// test each entry for the given category
				for (ItemCollection entry : entries) {
					if (filter.equals(entry.getType())) {
						// match!
						addChronicleEntry(filteredChronicleList, entry);
					}
				}
			}
			// sort chronicles by date.....
			Collections.sort(filteredChronicleList, new ChronicleEntityComparator(true));
		}

		
		computeTimeData(filteredChronicleList);

		logger.finest("......filter=" + filter + " size= " + filteredChronicleList.size());
		logger.fine("...computed filter in " + (System.currentTimeMillis() - l) + "ms");
	}

	/**
	 * This helper method adds a chronicle entry (ItemCollection) into the
	 * chronicleList.
	 * 
	 * The method updates the time data
	 * 
	 * @param entry
	 */
	private void addChronicleEntry(List<ChronicleEntity> chronicleList, ItemCollection entry) {
		String user = entry.getItemValueString("user");
		Date date = entry.getItemValueDate("date");

		ChronicleEntity chronicleEntity = new ChronicleEntity(user, date);
		int chronicleIndex = chronicleList.indexOf(chronicleEntity);
		if (chronicleIndex > -1) {
			// already created
			chronicleEntity = chronicleList.get(chronicleIndex);
		} // add history...
		chronicleEntity.addEntry(entry);

		if (chronicleIndex > -1) {
			chronicleList.set(chronicleIndex, chronicleEntity);
		} else {
			chronicleList.add(chronicleEntity);
		}
	}

	/**
	 * This method recalculates the yeas/months for the current entry list
	 */
	private void computeTimeData(List<ChronicleEntity> chronicleList) {
		yearsMonths = new HashMap<Integer, Set<Integer>>();
	
		for (ChronicleEntity chronicleEntity : chronicleList) {
			// update years table
			addTimeData(chronicleEntity.getDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
		}
	
	}

	/**
	 * Adds the month and year data as a category
	 */
	private void addTimeData(LocalDate localDate) {
		int year = localDate.getYear();
		int month = localDate.getMonthValue();
		Set<Integer> mothsPerYear = yearsMonths.get(year);
		if (mothsPerYear == null) {
			mothsPerYear = new HashSet<Integer>();
		}
		mothsPerYear.add(month);
		yearsMonths.put(year, mothsPerYear);

	}

}
