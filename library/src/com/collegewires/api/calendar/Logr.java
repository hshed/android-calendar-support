package com.collegewires.api.calendar;

import android.util.Log;

/** Log utility class to handle the log tag and DEBUG-only logging. */
final class Logr {
  public static void d(String message) {
    if (BuildConfig.DEBUG) {
      Log.d("CalendarSupportAPI", message);
    }
  }

  public static void d(String message, Object... args) {
    if (BuildConfig.DEBUG) {
      d(String.format(message, args));
    }
  }
}
