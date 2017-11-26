package com.example.secumfex.workingtimetracker;

import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;

import com.google.api.services.calendar.*;
import com.google.api.client.util.DateTime;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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
        mCallApiButton.setText("Query current Month Statistics");
        mCallApiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCallApiButton.setEnabled(false);
                mOutputText.setText("");
                startGetCurrentMonthHoursActivity();
                mCallApiButton.setEnabled(true);
            }
        });
        activityLayout.addView(mCallApiButton);

        mCallApiButton = new Button(this);
        mCallApiButton.setText("Query Last Month Statistics");
        mCallApiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCallApiButton.setEnabled(false);
                mOutputText.setText("");
                startGetLastMonthHoursActivity();
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
     * Create and start a CalendarQuery to retrieve the current Month hours
     */
    private void startGetCurrentMonthHoursActivity() {

        DateTime now = new DateTime(System.currentTimeMillis());
 //       DateTime lastFirstOfWeekTime   = new DateTime(getLastFirstDayOfWeekTimeValue());
        DateTime minTime = new DateTime(getLastFirstOfMonthTimeValue());
        DateTime maxTime = new DateTime(now.getValue() + Utils.getTimeValueMinutes(15) );

        Intent intent = new Intent(this, CalendarQuery.class);
        Bundle bundle = new Bundle();
        putDefaultValuesToCalendarQueryBundle( bundle );

        bundle.putLong(getString(R.string.min_time_pref_key), minTime.getValue());
        bundle.putLong(getString(R.string.max_time_pref_key), maxTime.getValue());

        intent.putExtras(bundle);
        startActivityForResult(intent, REQUEST_CURRENT_MONTH_EVENTS);
    }

    private void startGetLastMonthHoursActivity()
    {
        DateTime minTime = new DateTime(getFirstOfLastMonthTimeValue());
        DateTime maxTime = new DateTime(getLastOfLastMonthTimeValue());

        Intent intent = new Intent(this, CalendarQuery.class);
        Bundle bundle = new Bundle();
        putDefaultValuesToCalendarQueryBundle( bundle );

        bundle.putLong(getString(R.string.min_time_pref_key), minTime.getValue());
        bundle.putLong(getString(R.string.max_time_pref_key), maxTime.getValue());

        intent.putExtras(bundle);
        startActivityForResult(intent, REQUEST_LAST_MONTH_EVENTS);
    }

    String getAccountName()
    {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String accountPrefKey = getString(R.string.account_name_pref_key);
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
            case REQUEST_LAST_MONTH_EVENTS:
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String eventListStr =
                            data.getStringExtra(CalendarQuery.KEY_RESULT);
                    if (eventListStr != null) {
                        //EventWrapper[] eventArr = Utils.deserialize(eventListStr, EventWrapper[].class);
                        //List<EventWrapper> eventWrapperList = Arrays.asList(eventArr);

                        EventWrapper[] eventWrappersArr = Utils.deserialize(eventListStr, EventWrapper[].class);
                        List<EventWrapper> eventWrapperList = Arrays.asList(eventWrappersArr);

                        List<String> output = calculateCurrentMonthHours(eventWrapperList);
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

    private List<String> calculateCurrentMonthHours(List<EventWrapper> eventWrapperList)
    {
        long durationSinceLastFirstOfWeek = 0;
        long totalDurationMilliseconds = 0;

        DateTime now = new DateTime(System.currentTimeMillis());
        DateTime lastFirstOfWeekTime   = new DateTime(getLastFirstDayOfWeekTimeValue());
        List<String> eventStrings = new ArrayList<>();

        SparseArray<Long> dayToDurationMap = new SparseArray<>();

        // sum event durations
        for (EventWrapper eventWrapper : eventWrapperList) {
            DateTime start = new DateTime(eventWrapper.start);
            DateTime end = new DateTime(eventWrapper.end);

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

    private long getLastOfLastMonthTimeValue()
    {
        Calendar calendar = Calendar.getInstance();
        if ( calendar.get(Calendar.MONTH) == Calendar.JANUARY )
        {
            calendar.set(Calendar.YEAR, calendar.get(Calendar.YEAR) - 1);
            calendar.set(Calendar.MONTH, calendar.DECEMBER);
        }
        else
        {
            calendar.set(Calendar.MONTH, calendar.get(Calendar.MONTH) - 1);
        }
        Calendar.getInstance().getActualMaximum(Calendar.DAY_OF_MONTH);
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 50);
        return calendar.getTimeInMillis();
    }

    private long getFirstOfLastMonthTimeValue()
    {
        Calendar calendar = Calendar.getInstance();
        if ( calendar.get(Calendar.MONTH) == Calendar.JANUARY )
        {
            calendar.set(Calendar.YEAR, calendar.get(Calendar.YEAR) - 1);
            calendar.set(Calendar.MONTH, calendar.DECEMBER);
        }
        else
        {
            calendar.set(Calendar.MONTH, calendar.get(Calendar.MONTH) - 1);
        }
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 5);
        calendar.set(Calendar.MINUTE, 0);

        return calendar.getTimeInMillis();
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

    void putDefaultValuesToCalendarQueryBundle( Bundle bundle )
    {
        String accountName = getAccountName();
        String calendarName = getCalendarName();
        String eventName = getEventName();
        int numMaxResults = 60;
        bundle.putString(getString(R.string.account_name_pref_key), accountName);
        bundle.putString(getString(R.string.event_name_pref_key), eventName);
        bundle.putString(getString(R.string.calendar_name_pref_key), calendarName);
        bundle.putInt(getString(R.string.num_max_results_pref_key), numMaxResults);

    }
}