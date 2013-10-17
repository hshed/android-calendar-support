package com.collegewires.api.calendar;

/*
 * Copyright 2013 Hrishikesh Kumar
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at 
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * Based on https://github.com/square/android-times-square
 * 
 * Copyright 2012 Square, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import static java.util.Calendar.DATE;
import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.DAY_OF_WEEK;
import static java.util.Calendar.HOUR_OF_DAY;
import static java.util.Calendar.MILLISECOND;
import static java.util.Calendar.MINUTE;
import static java.util.Calendar.MONTH;
import static java.util.Calendar.SECOND;
import static java.util.Calendar.YEAR;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Parcelable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import com.collegewires.api.calendar.MonthCellDescriptor.RangeState;

public class CalendarViewPager extends ViewPager {

	public enum SelectionMode {
		/**
		 * Only one date will be selectable. If there is already a selected date
		 * and you select a new one, the old date will be unselected.
		 */
		SINGLE,
		/**
		 * Multiple dates will be selectable. Selecting an already-selected date
		 * will un-select it.
		 */
		MULTIPLE,
		/**
		 * Allows you to select a date range. Previous selections are cleared
		 * when you either:
		 * <ul>
		 * <li>Have a range selected and select another date (even if it's in
		 * the current range).</li>
		 * <li>Have one date selected and then select an earlier date.</li>
		 * </ul>
		 */
		RANGE
	}

	private Calendar today;
	private DateFormat weekdayNameFormat, monthNameFormat, fullDateFormat;
	private Date minDate;
	private final Calendar minCal = Calendar.getInstance();
	private final Calendar maxCal = Calendar.getInstance();
	private final Calendar monthCounter = Calendar.getInstance();

	SelectionMode selectionMode;
	private OnDateSelectedListener dateListener;
	private DateSelectableFilter dateConfiguredListener;
	private OnInvalidDateSelectedListener invalidDateListener = new DefaultOnInvalidDateSelectedListener();

	final List<Calendar> selectedCals = new ArrayList<Calendar>();
	final List<MonthDescriptor> months = new ArrayList<MonthDescriptor>();
	final List<MonthDescriptor> tempmonths = new ArrayList<MonthDescriptor>();
	final List<MonthCellDescriptor> selectedCells = new ArrayList<MonthCellDescriptor>();
	final List<List<List<MonthCellDescriptor>>> cells = new ArrayList<List<List<MonthCellDescriptor>>>();
	final List<List<List<MonthCellDescriptor>>> tempcells = new ArrayList<List<List<MonthCellDescriptor>>>();
	
	private CardPagerAdapter mCardPagerAdapter;
	LayoutInflater inflater;
	private int focusedpage = 0;

	public CalendarViewPager(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public CalendarViewPager(Context context) {
		super(context);
	}

	private void setMinDate(Date minDate) {
		this.minDate = minDate;
	}
	
	private void setSelectionMode(SelectionMode selectionMode) {
		this.selectionMode = selectionMode;
	}
	
	private SelectionMode getSelectionMode(){
		return this.selectionMode;
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		if (months.isEmpty()) {
			throw new IllegalStateException(
					"Must have at least one month to display.  Did you forget to call init()?");
		}
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}   
	
	public Builder initialize(Context context) {
		return new Builder(context);
	}

	private void init(Context context) {
		weekdayNameFormat = new SimpleDateFormat(
				context.getString(R.string.day_name_format));
		monthNameFormat = new SimpleDateFormat(context.getString(R.string.month_name_format));
		fullDateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM);
		if (today == null)
			today = Calendar.getInstance();

		if (minDate == null) {
			throw new IllegalArgumentException("minDate must not be null.");
		}
		if (minDate.getTime() == 0) {
			throw new IllegalArgumentException("minDate must be non-zero. "
					+ dbg(minDate));
		}

		this.selectionMode = getSelectionMode();
		// Clear out any previously-selected dates/cells.
		selectedCals.clear();
		selectedCells.clear();
		// Clear previous state.
		cells.clear();
		months.clear();
		minCal.setTime(minDate);
		Calendar next = Calendar.getInstance();
		//don't initialize up to year 2099; causes lag
		//initialize with numbers of year = 5 to overcome lag
		next.add(YEAR, 5);
		maxCal.setTime(next.getTime());
		setMidnight(minCal);
		setMidnight(maxCal);
		maxCal.add(MINUTE, -1);

		// Now iterate between minCal and maxCal and build up our list of months
		// to show.
		monthCounter.setTime(minCal.getTime());
		final int maxMonth = maxCal.get(MONTH);
		final int maxYear = maxCal.get(YEAR);
		while ((monthCounter.get(MONTH) <= maxMonth // Up to, including the month.
				|| monthCounter.get(YEAR) < maxYear) // Up to the year.
				&& monthCounter.get(YEAR) < maxYear + 1) { // But not > next year.
			Date date = monthCounter.getTime();
			MonthDescriptor month = new MonthDescriptor(
					monthCounter.get(MONTH), monthCounter.get(YEAR), date,
					monthNameFormat.format(date));
			cells.add(getMonthCells(month, monthCounter));
			Logr.d("Adding month %s", month);
			months.add(month);
			monthCounter.add(MONTH, 1);
		}

		mCardPagerAdapter = new CardPagerAdapter(context);
		setAdapter(mCardPagerAdapter);
		
		setOnPageChangeListener(new OnPageChangeListener() {
			@Override
			public void onPageSelected(int position) {
			}
			@Override
			public void onPageScrolled(int position, float positionOffset,
					int positionOffsetPixels) {
				focusedpage = position;
			}
			@Override
			public void onPageScrollStateChanged(int state) {
				//we will add years up to 2099 while scrolling
				//in background thread to avoid lag
					if (focusedpage == 2 && maxCal.get(YEAR)!=2099) {
						monthCounter.setTime(maxCal.getTime());
						Calendar next = maxCal;
						next.set(YEAR, 2099);
						maxCal.setTime(next.getTime());
						setMidnight(maxCal);
						maxCal.add(MINUTE, -1);

						final int maxMonth = maxCal.get(MONTH);
						final int maxYear = maxCal.get(YEAR);
						
						new AsyncTask<Void, Void, Void>() {

							@Override
							protected Void doInBackground(Void... params) {
								while ((monthCounter.get(MONTH) <= maxMonth 
										|| monthCounter.get(YEAR) < maxYear) 
										&& monthCounter.get(YEAR) < maxYear + 1) { 
									Date date = monthCounter.getTime();
									MonthDescriptor month = new MonthDescriptor(
											monthCounter.get(MONTH),
											monthCounter.get(YEAR), date,
											monthNameFormat.format(date));
									tempcells.add(getTempMonthCells(month, monthCounter));
									Logr.d("Adding month %s", month);
									tempmonths.add(month);
									monthCounter.add(MONTH, 1);
								}
								return null;
							}

							@Override
							protected void onPostExecute(Void result) {
								cells.addAll(tempcells);
							    tempcells.clear();
								months.addAll(tempmonths);
								tempmonths.clear();
								mCardPagerAdapter.notifyDataSetChanged();
								super.onPostExecute(result);
							}
						}.execute();
					}
				}
			}
		);
	}

	public CardPagerAdapter getCardPagerAdapter() {
		return mCardPagerAdapter;
	}

	private class CellClickedListener implements MonthView.Listener {
		@Override
		public void handleClick(MonthCellDescriptor cell) {
			Date clickedDate = cell.getDate();
			if (!betweenDates(clickedDate, minCal, maxCal)
					|| !isDateSelectable(clickedDate)) {
				if (invalidDateListener != null) {
					invalidDateListener.onInvalidDateSelected(clickedDate);
				}
			} else {
				boolean wasSelected = doSelectDate(clickedDate, cell);
				if (wasSelected && dateListener != null) {
					dateListener.onDateSelected(clickedDate);
				}
			}
		}
	}

	private boolean doSelectDate(Date date, MonthCellDescriptor cell) {
		Calendar newlySelectedCal = Calendar.getInstance();
		newlySelectedCal.setTime(date);
		// Sanitize input: clear out the hours/minutes/seconds/millis.
		setMidnight(newlySelectedCal);
		// Clear any remaining range state.
		for (MonthCellDescriptor selectedCell : selectedCells) {
			selectedCell.setRangeState(RangeState.NONE);
		}

		switch (selectionMode) {
		case RANGE:
			if (selectedCals.size() > 1) {
				// We've already got a range selected: clear the old one.
				clearOldSelections();
			} else if (selectedCals.size() == 1
					&& newlySelectedCal.before(selectedCals.get(0))) {
				// We're moving the start of the range back in time: clear the
				// old start date.
				clearOldSelections();
			}
			break;

		case MULTIPLE:
			date = applyMultiSelect(date, newlySelectedCal);
			break;

		case SINGLE:
			clearOldSelections();
			break;
		default:
			throw new IllegalStateException("Unknown selectionMode "
					+ selectionMode);
		}

		if (date != null) {
			// Select a new cell.
			if (selectedCells.size() == 0 || !selectedCells.get(0).equals(cell)) {
				selectedCells.add(cell);
				cell.setSelected(true);
			}
			selectedCals.add(newlySelectedCal);
			if (selectionMode == SelectionMode.RANGE
					&& selectedCells.size() > 1) {
				// Select all days in between start and end.
				Date start = selectedCells.get(0).getDate();
				Date end = selectedCells.get(1).getDate();
				selectedCells.get(0).setRangeState(
						MonthCellDescriptor.RangeState.FIRST);
				selectedCells.get(1).setRangeState(
						MonthCellDescriptor.RangeState.LAST);

				for (List<List<MonthCellDescriptor>> month : cells) {
					for (List<MonthCellDescriptor> week : month) {
						for (MonthCellDescriptor singleCell : week) {
							if (singleCell.getDate().after(start)
									&& singleCell.getDate().before(end)
									&& singleCell.isSelectable()) {
								singleCell.setSelected(true);
								singleCell.setRangeState(MonthCellDescriptor.RangeState.MIDDLE);
								selectedCells.add(singleCell);
							}
						}
					}
				}
			}
		}
		// Update the adapter.
		validateAndUpdate();
		return date != null;
	}

	private void validateAndUpdate() {
		if (getAdapter() == null) {
			setAdapter(mCardPagerAdapter);
		}
		mCardPagerAdapter.notifyDataSetChanged();
	}

	public class CardPagerAdapter extends PagerAdapter {

		int counter;

		public CardPagerAdapter(Context context) {
			inflater = (LayoutInflater) context
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			counter = months.size();
		}

		@Override
		public Object instantiateItem(View collection, int position) {
			MonthView monthView = MonthView.create(null, inflater,
					weekdayNameFormat, new CellClickedListener(), today);
			monthView.init(months.get(position), cells.get(position));
			monthView.setTag(position);
			((ViewPager) collection).addView(monthView);
			return monthView;
		}

		@Override
		public int getItemPosition(Object object) {
			return POSITION_NONE;
		}

		@Override
		public void destroyItem(View collection, int position, Object view) {
			((ViewPager) collection).removeView((View) view);
		}

		@Override
		public boolean isViewFromObject(View view, Object object) {
			return view == ((View) object);
		}

		@Override
		public void finishUpdate(View arg0) {
		}

		@Override
		public void restoreState(Parcelable arg0, ClassLoader arg1) {
		}

		@Override
		public Parcelable saveState() {
			return null;
		}

		@Override
		public void startUpdate(View arg0) {
		}

		@Override
		public int getCount() {
			return months.size();
		}
	}

	List<List<MonthCellDescriptor>> getMonthCells(MonthDescriptor month,
			Calendar startCal) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(startCal.getTime());
		List<List<MonthCellDescriptor>> cells = new ArrayList<List<MonthCellDescriptor>>();
		cal.set(DAY_OF_MONTH, 1);
		int firstDayOfWeek = cal.get(DAY_OF_WEEK);
		int offset = cal.getFirstDayOfWeek() - firstDayOfWeek;
		if (offset > 0) {
			offset -= 7;
		}
		cal.add(Calendar.DATE, offset);

		Calendar minSelectedCal = minDate(selectedCals);
		Calendar maxSelectedCal = maxDate(selectedCals);

		while ((cal.get(MONTH) < month.getMonth() + 1 || cal.get(YEAR) < month
				.getYear()) //
				&& cal.get(YEAR) <= month.getYear()) {
			Logr.d("Building week row starting at %s", cal.getTime());
			List<MonthCellDescriptor> weekCells = new ArrayList<MonthCellDescriptor>();
			cells.add(weekCells);
			for (int c = 0; c < 7; c++) {
				Date date = cal.getTime();
				boolean isCurrentMonth = cal.get(MONTH) == month.getMonth();
				boolean isToday = sameDate(cal, getToday());
				int value = cal.get(DAY_OF_MONTH);
				boolean isSelected = isCurrentMonth
						&& containsDate(selectedCals, cal);
				boolean isSelectable = isCurrentMonth
						&& betweenDates(cal.getTime(), minCal, maxCal)
						&& isDateSelectable(date);

				MonthCellDescriptor.RangeState rangeState = MonthCellDescriptor.RangeState.NONE;
				if (selectedCals != null && selectedCals.size() > 1) {
					if (sameDate(minSelectedCal, cal)) {
						rangeState = MonthCellDescriptor.RangeState.FIRST;
					} else if (sameDate(maxDate(selectedCals), cal)) {
						rangeState = MonthCellDescriptor.RangeState.LAST;
					} else if (betweenDates(cal.getTime(), minSelectedCal,
							maxSelectedCal)) {
						rangeState = MonthCellDescriptor.RangeState.MIDDLE;
					}
				}

				weekCells.add(new MonthCellDescriptor(date, isCurrentMonth,
						isSelectable, isSelected, isToday, value, rangeState));
				cal.add(DATE, 1);
			}
		}
		return cells;
	}
	
	List<List<MonthCellDescriptor>> getTempMonthCells(MonthDescriptor month,
			Calendar startCal) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(startCal.getTime());
		List<List<MonthCellDescriptor>> cells = new ArrayList<List<MonthCellDescriptor>>();
		cal.set(DAY_OF_MONTH, 1);
		int firstDayOfWeek = cal.get(DAY_OF_WEEK);
		int offset = cal.getFirstDayOfWeek() - firstDayOfWeek;
		if (offset > 0) {
			offset -= 7;
		}
		cal.add(Calendar.DATE, offset);
		 List<Calendar> selectedCals = new ArrayList<Calendar>();
		Calendar minSelectedCal = minDate(selectedCals);
		Calendar maxSelectedCal = maxDate(selectedCals);

		while ((cal.get(MONTH) < month.getMonth() + 1 || cal.get(YEAR) < month
				.getYear()) //
				&& cal.get(YEAR) <= month.getYear()) {
			Logr.d("Building week row starting at %s", cal.getTime());
			List<MonthCellDescriptor> weekCells = new ArrayList<MonthCellDescriptor>();
			cells.add(weekCells);
			for (int c = 0; c < 7; c++) {
				Date date = cal.getTime();
				boolean isCurrentMonth = cal.get(MONTH) == month.getMonth();
				boolean isToday = sameDate(cal, getToday());
				int value = cal.get(DAY_OF_MONTH);
				boolean isSelected = isCurrentMonth
						&& containsDate(selectedCals, cal);
				boolean isSelectable = isCurrentMonth
						&& betweenDates(cal.getTime(), minCal, maxCal)
						&& isDateSelectable(date);

				MonthCellDescriptor.RangeState rangeState = MonthCellDescriptor.RangeState.NONE;
				if (selectedCals != null && selectedCals.size() > 1) {
					if (sameDate(minSelectedCal, cal)) {
						rangeState = MonthCellDescriptor.RangeState.FIRST;
					} else if (sameDate(maxDate(selectedCals), cal)) {
						rangeState = MonthCellDescriptor.RangeState.LAST;
					} else if (betweenDates(cal.getTime(), minSelectedCal,
							maxSelectedCal)) {
						rangeState = MonthCellDescriptor.RangeState.MIDDLE;
					}
				}

				weekCells.add(new MonthCellDescriptor(date, isCurrentMonth,
						isSelectable, isSelected, isToday, value, rangeState));
				cal.add(DATE, 1);
			}
		}
		return cells;
	}

	private Date applyMultiSelect(Date date, Calendar selectedCal) {
		for (MonthCellDescriptor selectedCell : selectedCells) {
			if (selectedCell.getDate().equals(date)) {
				// De-select the currently-selected cell.
				selectedCell.setSelected(false);
				selectedCells.remove(selectedCell);
				date = null;
				break;
			}
		}
		for (Calendar cal : selectedCals) {
			if (sameDate(cal, selectedCal)) {
				selectedCals.remove(cal);
				break;
			}
		}
		return date;
	}

	private void clearOldSelections() {
		for (MonthCellDescriptor selectedCell : selectedCells) {
			// De-select the currently-selected cell.
			selectedCell.setSelected(false);
		}
		selectedCells.clear();
		selectedCals.clear();
	}

	private boolean isDateSelectable(Date date) {
		if (dateConfiguredListener == null) {
			return true;
		}
		return dateConfiguredListener.isDateSelectable(date);
	}

	public void setOnDateSelectedListener(OnDateSelectedListener listener) {
		dateListener = listener;
	}

	/**
	 * Set a listener to react to user selection of a disabled date.
	 * 
	 * @param listener
	 *            the listener to set, or null for no reaction
	 */
	public void setOnInvalidDateSelectedListener(
			OnInvalidDateSelectedListener listener) {
		invalidDateListener = listener;
	}

	/**
	 * Set a listener used to discriminate between selectable and unselectable
	 * dates. Set this to disable arbitrary dates as they are rendered.
	 * <p>
	 * Important: set this before you call {@link #init(Date, Date)} methods. If
	 * called afterwards, it will not be consistently applied.
	 */
	public void setDateSelectableFilter(DateSelectableFilter listener) {
		dateConfiguredListener = listener;
	}

	/**
	 * Interface to be notified when a new date is selected. This will only be
	 * called when the user initiates the date selection. If you call
	 * {@link #selectDate(Date)} this listener will not be notified.
	 * 
	 * @see #setOnDateSelectedListener(OnDateSelectedListener)
	 */
	public interface OnDateSelectedListener {
		void onDateSelected(Date date);
	}

	/**
	 * Interface to be notified when an invalid date is selected by the user.
	 * This will only be called when the user initiates the date selection. If
	 * you call {@link #selectDate(Date)} this listener will not be notified.
	 * 
	 * @see #setOnInvalidDateSelectedListener(OnInvalidDateSelectedListener)
	 */
	public interface OnInvalidDateSelectedListener {
		void onInvalidDateSelected(Date date);
	}

	/**
	 * Interface used for determining the selectability of a date cell when it
	 * is configured for display on the calendar.
	 * 
	 * @see #setDateSelectableFilter(DateSelectableFilter)
	 */
	public interface DateSelectableFilter {
		boolean isDateSelectable(Date date);
	}

	private class DefaultOnInvalidDateSelectedListener implements
			OnInvalidDateSelectedListener {
		@Override
		public void onInvalidDateSelected(Date date) {
			String errMessage = getResources().getString(R.string.invalid_date,
					fullDateFormat.format(minCal.getTime()));
			Toast.makeText(getContext(), errMessage, Toast.LENGTH_SHORT).show();
		}
	}

	static boolean betweenDates(Date date, Calendar minCal, Calendar maxCal) {
		final Date min = minCal.getTime();
		return (date.equals(min) || date.after(min)) // >= minCal
				&& date.before(maxCal.getTime()); // && < maxCal
	}

	public Calendar getToday() {
		return today;
	}

	public void setToday(Calendar today) {
		this.today = today;

	}
	
	 public Date getSelectedDate() {
		    return (selectedCals.size() > 0 ? selectedCals.get(0).getTime() : null);
	 }

	/** Returns a string summarizing what the client sent us for init() params. */
	private static String dbg(Date minDate) {
		return "minDate: " + minDate;
	}

	/** Clears out the hours/minutes/seconds/millis of a Calendar. */
	static void setMidnight(Calendar cal) {
		cal.set(HOUR_OF_DAY, 0);
		cal.set(MINUTE, 0);
		cal.set(SECOND, 0);
		cal.set(MILLISECOND, 0);
	}

	private static Calendar minDate(List<Calendar> selectedCals) {
		if (selectedCals == null || selectedCals.size() == 0) {
			return null;
		}
		Collections.sort(selectedCals);
		return selectedCals.get(0);
	}

	private static Calendar maxDate(List<Calendar> selectedCals) {
		if (selectedCals == null || selectedCals.size() == 0) {
			return null;
		}
		Collections.sort(selectedCals);
		return selectedCals.get(selectedCals.size() - 1);
	}

	private static boolean sameDate(Calendar cal, Calendar selectedDate) {
		return cal.get(MONTH) == selectedDate.get(MONTH)
				&& cal.get(YEAR) == selectedDate.get(YEAR)
				&& cal.get(DAY_OF_MONTH) == selectedDate.get(DAY_OF_MONTH);
	}

	private static boolean containsDate(List<Calendar> selectedCals,
			Calendar cal) {
		for (Calendar selectedCal : selectedCals) {
			if (sameDate(cal, selectedCal)) {
				return true;
			}
		}
		//new AlertDialog.Builder(context).
		return false;
	}
	/**
	 * Builder for CalendarViewPager. </br>
	 * @param SelectionMode default is SINGLE.
	 */
	public class Builder {
		Context context;
		Date minDate;
		SelectionMode selectionMode = SelectionMode.SINGLE;
		public Builder(Context context){
			this.context = context;
		}
		public Builder minDate(Date minDate) {
			this.minDate = minDate;
			return this;
		}
		public Builder selectionMode(SelectionMode selectionMode) {
			this.selectionMode = selectionMode;
			return this;
		}
		public void create(){
			setMinDate(this.minDate);
			setSelectionMode(this.selectionMode);
			init(context);
		}
	}
	
}
