package com.example.secumfex.workingtimetracker;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;

import com.google.api.services.calendar.*;
import com.google.api.client.util.DateTime;

import com.google.api.services.calendar.model.*;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class StatisticsActivity extends Activity
{
    GoogleAccountCredential mCredential;
    private TextView mOutputText;
    private Button mCallApiButton;
    ProgressDialog mProgress;

    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;
    static final int REQUEST_LAST_MONTH_EVENTS = 1004;
    static final int REQUEST_CURRENT_MONTH_EVENTS = 1005;
    static final int REQUEST_CURRENT_DAY = 1006;

    private static final String BUTTON_TEXT = "Call Google Calendar API";
    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String[] SCOPES = { CalendarScopes.CALENDAR_READONLY };

    /**
     * Create the main activity.
     * @param savedInstanceState previously saved instance data.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout activityLayout = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        activityLayout.setLayoutParams(lp);
        activityLayout.setOrientation(LinearLayout.VERTICAL);
        activityLayout.setPadding(16, 16, 16, 16);

        ViewGroup.LayoutParams tlp = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);

        mCallApiButton = new Button(this);
        mCallApiButton.setText(BUTTON_TEXT);
        mCallApiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCallApiButton.setEnabled(false);
                mOutputText.setText("");
                getResultsFromApi();
                mCallApiButton.setEnabled(true);
            }
        });
        activityLayout.addView(mCallApiButton);

        mOutputText = new TextView(this);
        mOutputText.setLayoutParams(tlp);
        mOutputText.setPadding(16, 16, 16, 16);
        mOutputText.setVerticalScrollBarEnabled(true);
        mOutputText.setMovementMethod(new ScrollingMovementMethod());
        mOutputText.setText(
                "Click the \'" + BUTTON_TEXT +"\' button to test the API.");
        activityLayout.addView(mOutputText);

        mProgress = new ProgressDialog(this);
        mProgress.setMessage("Calling Google Calendar API ...");

        setContentView(activityLayout);
    }


    /**
     * Attempt to call the API, after verifying that all the preconditions are
     *
     * satisfied. The preconditions are: Google Play Services installed, an
     * account was selected and the device currently has online access. If any
     * of the preconditions are not satisfied, the app will prompt the user as
     * appropriate.
     */
    private void getResultsFromApi() {
        String accountName = getAccountName();
        String calendarName = getCalendarName();
        String eventName = getEventName();

        DateTime now = new DateTime(System.currentTimeMillis());
 //       DateTime lastFirstOfWeekTime   = new DateTime(getLastFirstDayOfWeekTimeValue());
        DateTime minTime = new DateTime(getLastFirstOfMonthTimeValue());
        DateTime maxTime = new DateTime(now.getValue() + Utils.getTimeValueMinutes(15) );
        int numMaxResults = 60;

        CalendarQuery query = new CalendarQuery();
        query.setAccountName(accountName)
                .setEventName(eventName)
                .setAccountName(accountName)
                .setCalendarName(calendarName)
                .setMinTime(minTime)
                .setMaxTime(maxTime)
                .setNumMaxResults(numMaxResults);
        Intent intent = new Intent(this, CalendarQuery.class);
        startActivityForResult(intent, REQUEST_CURRENT_MONTH_EVENTS);
    }


    String getAccountName()
    {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String accountPrefKey = getString(R.string.app_name);
        String accountName = sharedPref.getString(accountPrefKey, null);

        return accountName;
    }


    String getCalendarName()
    {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String calendarPrefKey = getString(R.string.calendar_name_pref_key);
        String calendarName = sharedPref.getString(calendarPrefKey, calendarPrefKey);

        return calendarName;
    }


    /**
     * Called when an activity launched here (specifically, AccountPicker
     * and authorization) exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     * @param requestCode code indicating which activity result is incoming.
     * @param resultCode code indicating the result of the incoming
     *     activity result.
     * @param data Intent (containing result data) returned by incoming
     *     activity result.
     */
    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode) {
            case REQUEST_CURRENT_MONTH_EVENTS:
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String eventListStr =
                            data.getStringExtra(CalendarQuery.KEY_RESULT);
                    if (eventListStr != null) {
                        Event[] eventArr = Utils.deserialize(eventListStr, Event[].class);
                        List<Event> eventList = Arrays.asList(eventArr);

                        List<String> output = calculateCurrentMonthHours(eventList);
                        printResults(output);

                        //TODO write events to preferences or s.th.
                        /*
                        SharedPreferences settings =
                                getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();

                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        */
                    }
                    else
                    {
                        // something went wrong no results could be generated
                    }
                }
                break;
        }
    }

    private void printResults(List<String> output)
    {
        mProgress.hide();
        if (output == null || output.size() == 0) {
            String textStr = "No results returned.";
            mOutputText.setText(textStr);
        } else {
            String textStr = "Data retrieved using the Google Calendar API:";
            output.add(0, textStr);
            mOutputText.setText(TextUtils.join("\n", output));
        }
    }

    private List<String> calculateCurrentMonthHours(List<Event> eventList)
    {
        long durationSinceLastFirstOfWeek = 0;
        long totalDurationMilliseconds = 0;

        DateTime now = new DateTime(System.currentTimeMillis());
        DateTime lastFirstOfWeekTime   = new DateTime(getLastFirstDayOfWeekTimeValue());
        List<String> eventStrings = new ArrayList<>();

        SparseArray<Long> dayToDurationMap = new SparseArray<>();

        // sum event durations
        for (Event event : eventList) {
            DateTime start = event.getStart().getDateTime();
            DateTime end = event.getEnd().getDateTime();

            // skip full day events
            if (start == null || end == null) {
                continue;
            }

            // calculate duration
            long durationMilliseconds = end.getValue() - start.getValue();

            // update total duration
            totalDurationMilliseconds += durationMilliseconds;

            // if within the running week
            if ( start.getValue() >= lastFirstOfWeekTime.getValue() )
            {
                durationSinceLastFirstOfWeek += durationMilliseconds;
            }

            // add duration to day
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis( start.getValue() );
            int dayOfYear = calendar.get(Calendar.DAY_OF_YEAR);
            Long dayDuration = dayToDurationMap.get( dayOfYear );
            if ( dayDuration == null )
            {
                dayDuration = 0L;
            }
            dayToDurationMap.put( dayOfYear, dayDuration + durationMilliseconds );
        }

        // iterate days, show work times for day
        for (int i = 0; i < dayToDurationMap.size(); i++)
        {
            int dayOfYear = dayToDurationMap.keyAt(i);
            long durationOfDay = dayToDurationMap.get( dayOfYear );

            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.DAY_OF_YEAR, dayOfYear);

            long hours = durationOfDay / Utils.getTimeValueHours(1);
            long minutes = (durationOfDay - Utils.getTimeValueHours((int) hours)) / Utils.getTimeValueMinutes(1);
            SimpleDateFormat dayFormat = new SimpleDateFormat("dd.MM. EE", Locale.getDefault());
            String dayStr = dayFormat.format( calendar.getTime() );
            String decimalZeroStr = (minutes < 10 ? "0" : "");
            String durationStr = Long.toString(hours) + ":" + decimalZeroStr + Long.toString(minutes);
            eventStrings.add(String.format("%s (%s)", dayStr, durationStr));
        }

        // create total duration string and add to result
        String totalDurationStr = getDurationString(totalDurationMilliseconds);
        String totalDurationInfoStr = "Total working time ";
        eventStrings.add(String.format("%s (%s)", totalDurationInfoStr, totalDurationStr));

        // create overtime string and add
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String timeStr = pref.getString(getString(R.string.time_pref_key), null);
        if ( timeStr != null )
        {
            int hours = Integer.parseInt( timeStr.substring(0, timeStr.indexOf(":")) );
            int minutes = Integer.parseInt( timeStr.substring(timeStr.indexOf(":") +1) );
            long milliseconds = Utils.getTimeValueHours(hours) + Utils.getTimeValueMinutes(minutes);

            long regularWorkingTime = dayToDurationMap.size() * milliseconds;
            long overTimeSinceFirstOfMonth = totalDurationMilliseconds - regularWorkingTime;

            String overTimeStr = getDurationString(overTimeSinceFirstOfMonth);
            String overTimeInfoStr = "Over time ";
            eventStrings.add(String.format("%s (%s)", overTimeInfoStr, overTimeStr));
        }

        return eventStrings;
    }

    /**
     * Retrieves the eventName from the shared preferences, or the default
     * @return the eventName
     */
    private String getEventName()
    {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        String eventPropertyKey = getString(R.string.event_name_pref_key);
        return sharedPref.getString(eventPropertyKey, getString(R.string.event_name_pref_def));
    }

    private String getDurationString(long durationMilliseconds )
    {
        int hours = Utils.getHours( durationMilliseconds );
        int minutes = Utils.getMinutes(durationMilliseconds - Utils.getTimeValueHours(hours));
        String decimalZeroStr = (minutes < 10 ? "0" : "");
        return String.format("%s:%s%s",Long.toString(hours),decimalZeroStr,Long.toString(minutes));
   }

    private long getLastFirstOfMonthTimeValue()
    {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 5);
        calendar.set(Calendar.MINUTE, 0);
        return calendar.getTimeInMillis();
    }

    private long getLastFirstDayOfWeekTimeValue()
    {
        Calendar calendar = Calendar.getInstance();
        int firstDayOfWeek = calendar.getFirstDayOfWeek();
        calendar.set(Calendar.DAY_OF_WEEK, firstDayOfWeek);
        calendar.set(Calendar.HOUR_OF_DAY, 5);
        calendar.set(Calendar.MINUTE, 0);
        return calendar.getTimeInMillis();
    }
}