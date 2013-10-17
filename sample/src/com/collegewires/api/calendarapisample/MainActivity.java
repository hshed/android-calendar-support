package com.collegewires.api.calendarapisample;


import java.util.Calendar;

import android.app.Activity;
import android.os.Bundle;
import com.collegewires.api.calendar.CalendarViewPager;
import com.collegewires.api.calendar.CalendarViewPager.SelectionMode;

public class MainActivity extends Activity {

	private CalendarViewPager calendarViewPager;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_main);
		Calendar today = Calendar.getInstance();
		
		calendarViewPager = (CalendarViewPager) findViewById(R.id.calendar_view);
		
		calendarViewPager.initialize(this).minDate(today.getTime())
						.selectionMode(SelectionMode.SINGLE).create();
		
	}

}
