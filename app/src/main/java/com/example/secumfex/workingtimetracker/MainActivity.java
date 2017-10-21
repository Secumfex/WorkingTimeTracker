package com.example.secumfex.workingtimetracker;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.CalendarList;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;

import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity implements EasyPermissions.PermissionCallbacks{

    private enum ClockInState {CLOCKED_IN, CLOCKED_OUT/*, PAUSED*/}
    private ClockInState clockInState = ClockInState.CLOCKED_OUT;

    private Thread updateThread = null;

    GoogleAccountCredential credential;
    ProgressDialog progressDialog;

//    public static final String EXTRA_MESSAGE = "com.example.secumfex.workingtimetracker.MESSAGE";
    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String[] SCOPES = { CalendarScopes.CALENDAR };

    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PreferenceManager.setDefaultValues(getBaseContext(), R.xml.preferences, false);
        setContentView(R.layout.activity_main);

        // print current date and init updater object
        updateTime();

        // ListView thing
        ArrayList<String> prefArray = new ArrayList<>();
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        prefArray.add(pref.getString(getString(R.string.time_pref_key), "time pref not found"));
        prefArray.add(pref.getString(getString(R.string.event_name_pref_key), "event pref not found"));
        prefArray.add(pref.getString(getString(R.string.calendar_name_pref_key), "calendar pref not found"));

        long lastTime = pref.getLong(getString(R.string.last_time_key), -1);
        String lastTimeString = "last time not set";
        if (lastTime != -1)
        {
            Date dt = new Date(lastTime);
            lastTimeString = DateFormat.getDateTimeInstance().format(dt);
        }
        prefArray.add(lastTimeString);

        int lastTimeState = pref.getInt(getString(R.string.last_time_state_key), -1);
        String lastTimeStateStr = "last time state not set";
        if (lastTimeState != -1)
        {
            if (lastTimeState == 0) {
                lastTimeStateStr = getString(R.string.clocked_out);
            }
            else if (lastTimeState == 1) {
                lastTimeStateStr = getString(R.string.clocked_in);
            }
        }
        prefArray.add(lastTimeStateStr);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, prefArray);
        ListView listView = (ListView) findViewById(R.id.listview);
        listView.setAdapter(adapter);

        // Initialize credentials and service object.
        credential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());

        // Initialize Progress Dialog (invisible)
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Calling Google Calendar API ...");

        getCurrentCheckInState();
    }

    @Override
    public void onResume()
    {
        super.onResume();

        // create and start a new timer thread
        if ( updateThread == null )
        {
            Runnable runnable = new CountDownRunner();
            updateThread = new Thread(runnable);
            updateThread.start();
        }
    }

    @Override
    public void onPause()
    {
        super.onPause();

        //stop, i.e. kill, the timer thread
        if ( updateThread != null)
        {
            Thread t = updateThread;
            updateThread = null;
            t.interrupt();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    /**
     * Create an AppPreferencesActivity
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch(item.getItemId())
        {
            case R.id.preferences:
            {
                Intent intent = new Intent(this, AppPreferenceActivity.class);
                startActivity(intent);
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }


    // prints the current time
    private void updateTime()
    {
        runOnUiThread(new Runnable() {
            public void run(){
                TextView dateView = (TextView) findViewById(R.id.dateView);
                Date date = Calendar.getInstance().getTime();
                String dateStr = DateFormat.getDateTimeInstance().format(date);
                dateView.setText(dateStr);
            }
        });
    }

    private class CountDownRunner implements Runnable{
        // @Override
        public void run() {
            while(!Thread.currentThread().isInterrupted()){
                try {
                    updateTime();
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void getCurrentCheckInState()
    {
        setClockInState( ClockInState.CLOCKED_OUT);
    }

    /**
     * Switch the clock in state without toggling the state transfer
     * @param view source of event
     */
    public void switchClockInStateWithoutToggle(View view)
    {
        if ( clockInState == ClockInState.CLOCKED_OUT )
        {
            setClockInState( ClockInState.CLOCKED_IN );
        }
        else {
            setClockInState(ClockInState.CLOCKED_OUT);
        }
    }

    /**
     * update the views that are dependant on a clock in state change
     */
    private void updateClockInViews()
    {


        // The toggle button
        ToggleButton toggle = (ToggleButton) findViewById(R.id.toggleButton);
        toggle.setChecked( clockInState == ClockInState.CLOCKED_IN );

        String btnStr = "";
        String stateStr = "";
        switch (clockInState)
        {
            case CLOCKED_IN:
                btnStr = getString(R.string.clock_out);
                stateStr = getString(R.string.clocked_in);
                break;
            case CLOCKED_OUT:
                btnStr = getString(R.string.clock_in);
                stateStr = getString(R.string.clocked_out);
                break;
        }

        TextView btn = (TextView) findViewById(R.id.btnCheckInOut);
        btn.setText(btnStr);

        TextView tv = (TextView) findViewById(R.id.testTextView);
        tv.setText( stateStr );
    }

    private void setClockInState(ClockInState state)
    {
        clockInState = state;

        updateClockInViews();
    }

    public void toggleClockIn(View view)
    {
        if ( clockInState == ClockInState.CLOCKED_OUT)
        {
            checkIn(view);
        }
        else if ( clockInState == ClockInState.CLOCKED_IN)
        {
            checkOut(view);
        }

        view.setEnabled(false);
        getResultsFromApi(  );
        view.setEnabled(true);
    }

    /**
     * Called when the user clicks the Open calendar button
     */
    public void openCalendarApp(View view)
    {
        Intent intent = new Intent(this, StatisticsActivity.class);
        startActivity(intent);
    }

    /**
     * Check-In
     */
    public void checkIn(View view)
    {
        setClockInState(ClockInState.CLOCKED_IN);
    }

    /**
     * Check-Out
     */
    public void checkOut(View view)
    {
        setClockInState(ClockInState.CLOCKED_OUT);
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
        if (! isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (credential.getSelectedAccountName() == null) {
            chooseAccount();
        } else if (! isDeviceOnline()) {
            TextView tv = (TextView) findViewById(R.id.testTextView);
            String textStr = "No network connection available.";
            tv.setText(textStr);
        } else {
            new MainActivity.MakeRequestTask(credential, clockInState).execute();
        }
    }

    /**
     * Attempts to set the account used with the API credentials. If an account
     * name was previously saved it will use that one; otherwise an account
     * picker dialog will be shown to the user. Note that the setting the
     * account to use with the credentials object requires the app to have the
     * GET_ACCOUNTS permission, which is requested here if it is not already
     * present. The AfterPermissionGranted annotation indicates that this
     * function will be rerun automatically whenever the GET_ACCOUNTS permission
     * is granted.
     */
    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(
                this, Manifest.permission.GET_ACCOUNTS)) {
            String accountName = getPreferences(Context.MODE_PRIVATE)
                    .getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                credential.setSelectedAccountName(accountName);
                getResultsFromApi();
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                        credential.newChooseAccountIntent(),
                        REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this,
                    "This app needs to access your Google account (via Contacts).",
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
        }
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
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    TextView tv = (TextView) findViewById(R.id.testTextView);
                    String textStr = "This app requires Google Play Services. Please install Google Play Services on your device and relaunch this app.";
                    tv.setText(textStr);
                } else {
                    getResultsFromApi();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        SharedPreferences settings =
                                getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        credential.setSelectedAccountName(accountName);
                        getResultsFromApi();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK) {
                    getResultsFromApi();
                }
                break;
        }
    }

    /**
     * Respond to requests for permissions at runtime for API 23 and above.
     * @param requestCode The request code passed in
     *     requestPermissions(android.app.Activity, String, int, String[])
     * @param permissions The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *     which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults, this);
    }

    /**
     * Callback for when a permission is granted using the EasyPermissions
     * library.
     * @param requestCode The request code associated with the requested
     *         permission
     * @param list The requested permission list. Never null.
     */
    @Override
    public void onPermissionsGranted(int requestCode, List<String> list) {
        // Do nothing.
    }

    /**
     * Callback for when a permission is denied using the EasyPermissions
     * library.
     * @param requestCode The request code associated with the requested
     *         permission
     * @param list The requested permission list. Never null.
     */
    @Override
    public void onPermissionsDenied(int requestCode, List<String> list) {
        // Do nothing.
    }

    /**
     * Checks whether the device currently has a network connection.
     * @return true if the device has a network connection, false otherwise.
     */
    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    /**
     * Check that Google Play services APK is installed and up to date.
     * @return true if Google Play Services is available and up to
     *     date on this device; false otherwise.
     */
    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }

    /**
     * Attempt to resolve a missing, out-of-date, invalid or disabled Google
     * Play Services installation via a user dialog, if possible.
     */
    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }

    /**
     * Display an error dialog showing that Google Play Services is missing
     * or out of date.
     * @param connectionStatusCode code describing the presence (or lack of)
     *     Google Play Services on this device.
     */
    void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                MainActivity.this,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }

    /**
     * An asynchronous task that handles the Google Calendar API call.
     * Placing the API calls in their own task ensures the UI stays responsive.
     */
    private class MakeRequestTask extends AsyncTask<Void, Void, List<Event>> {
        private com.google.api.services.calendar.Calendar calendarService = null;
        private Exception lastError = null;
        private ClockInState clockInState = ClockInState.CLOCKED_OUT;

        MakeRequestTask(GoogleAccountCredential credential, ClockInState clockInState) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            calendarService = new com.google.api.services.calendar.Calendar.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName("Google Calendar API Android Quickstart")
                    .build();
            this.clockInState = clockInState;
        }

        /**
         * Background task to call Google Calendar API.
         * @param params no parameters needed for this task.
         */
        @Override
        protected List<Event> doInBackground(Void... params) {
            try {
                return getDataFromApi();
            } catch (Exception e) {
                lastError = e;
                cancel(true);
                return null;
            }
        }

        private void setLastTimeProperty(ClockInState state, long time)
        {
            // get shared preferences
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

            // create preference setter
            SharedPreferences.Editor editor = sharedPref.edit();
            int stateInt = (state == ClockInState.CLOCKED_IN) ? 1 : 0;
            editor.putLong(getString(R.string.last_time_key), time);
            editor.putInt(getString(R.string.last_time_state_key), stateInt);
            editor.apply();
        }

        /**
         * Fetch a list of the target events from today from the target calendar.
         * @return List of Events that have been altered.
         * @throws IOException if calendarList().list() fails
         */
        private List<Event> getDataFromApi() throws IOException {
            // Prepare DateTime variables.
            Calendar now = Calendar.getInstance();
            Calendar todayMorning = Calendar.getInstance();
            Calendar todayNight = Calendar.getInstance();
            todayMorning.set(Calendar.HOUR_OF_DAY, 5);
            todayNight.set(Calendar.HOUR_OF_DAY, 23);
            todayNight.set(Calendar.MINUTE, 59);
            DateTime todayMorningDateTime = new DateTime(todayMorning.getTime());
            DateTime todayNightDateTime = new DateTime(todayNight.getTime());
            DateTime nowDateTime = new DateTime(now.getTime());

            // Get Preferences
//            SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

            // Retrieve Calendar ID
            String calendarPrefKey = getString(R.string.calendar_name_pref_key);
            String calendarId = getString(R.string.calendar_name);
            String calendarName = sharedPref.getString(calendarPrefKey, calendarPrefKey);
            {
                CalendarList calendarList = calendarService.calendarList().list().setPageToken(null).execute();
                List<CalendarListEntry> items = calendarList.getItems();

                for (CalendarListEntry calendarListEntry : items) {
                    if ( calendarName.equals(calendarListEntry.getSummary()) )
                    {
                        calendarId = calendarListEntry.getId();
                    }
                }
            }

            // Retrieve Events
            String eventPropertyKey = getString(R.string.event_name_pref_key);
            String eventName = sharedPref.getString(eventPropertyKey, getString(R.string.event_name));
            Events events = calendarService.events().list(calendarId)
                    .setMaxResults(10)
                    .setTimeMin(todayMorningDateTime)
                    .setTimeMax(todayNightDateTime)
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .setQ(eventName)
                    .execute();
            List<Event> items = events.getItems();
            List<Event> eventList = new ArrayList<>();

            // If Empty and clock in : Create new Event
            if ( items.isEmpty() && clockInState == ClockInState.CLOCKED_IN )
            {
                // create new event
                Event e = new Event();

                // retrieve daily work time
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
//                SharedPreferences sharedPreferences = getPreferences(Context.MODE_PRIVATE);
                String timePrefKey = getString(R.string.time_pref_key);
                String timeStr = sharedPreferences.getString(timePrefKey, "08:00");
                long hours = Long.parseLong( timeStr.substring(0, timeStr.indexOf(":")) );
                long minutes = Long.parseLong( timeStr.substring(timeStr.indexOf(":") +1) );
                long milliseconds = (1000 * 60 * minutes) + (1000 * 60 * 60 * hours);

                // set start/end time
                EventDateTime edtStart = new EventDateTime();
                edtStart.setDateTime(nowDateTime);
                EventDateTime edtEnd = new EventDateTime();
                edtEnd.setDateTime(new DateTime(nowDateTime.getValue() + milliseconds));
                e.setStart( edtStart );
                e.setEnd( edtEnd );

                // set name
                e.setSummary( eventName );

                //==== insert =====
                e = calendarService.events().insert(calendarId, e).execute();

                eventList.add(e);
            }
            // there are events, so fetch the latest
            else if ( !items.isEmpty() ) {
                // last event : latest startTime, i.e. the most recent event
                Event event = items.get(items.size() - 1);

                // now
                EventDateTime et = new EventDateTime();
                et.setDateTime(nowDateTime);

                // determine which value to change
                if (clockInState == ClockInState.CLOCKED_IN) {
                    // clocked in and somewhere before event end : update start time
                    if (event.getEnd().getDateTime().getValue() > nowDateTime.getValue()) {
                        event.setStart(et);
                    }
                    // clocked in and event already ended : create new event
                    else if (event.getEnd().getDateTime().getValue() < nowDateTime.getValue()) {
                        // create new event
                        Event e = new Event();

                        // set start/end time
                        EventDateTime edtStart = new EventDateTime();
                        edtStart.setDateTime(nowDateTime);
                        EventDateTime edtEnd = new EventDateTime();

                        long timeEightHours = 8 * 60 * 60 * 1000;
                        long timeEnd = event.getEnd().getDateTime().getValue();
                        long timeStart = event.getStart().getDateTime().getValue();
                        long timeLeft = timeEightHours - (timeEnd - timeStart);
                        DateTime dt = new DateTime(nowDateTime.getValue() + timeLeft);
                        edtEnd.setDateTime(dt);

                        e.setStart( edtStart );
                        e.setEnd( edtEnd );

                        // set name
                        e.setSummary(eventName);

                        //==== insert
                        e = calendarService.events().insert(calendarId, e).execute();
                        eventList.add(e);
                    }
                } else if (clockInState == ClockInState.CLOCKED_OUT) {
                    // clocked out and somewhere before event start : create new event or do nothing
                    /*if (event.getStart().getDateTime().getValue() > nowDateTime.getValue()) {
                        //TODO create new event
                    }
                    // clocked out and event already begun or ended : update end time
                    else*/ if (event.getStart().getDateTime().getValue() < nowDateTime.getValue()) {
                        event.setEnd(et);
                    }
                }

                //==== update event
                Event e = calendarService.events().update(calendarId, event.getId(), event).execute();
                eventList.add(e);
            }

            setLastTimeProperty(clockInState, nowDateTime.getValue());

            return eventList;
        }


        @Override
        protected void onPreExecute() {
            TextView tv = (TextView) findViewById(R.id.testTextView);
            tv.setText("");
            progressDialog.show();
        }

        @Override
        protected void onPostExecute(List<Event> output) {
            progressDialog.hide();
            if (output == null || output.size() == 0) {
                TextView tv = (TextView) findViewById(R.id.testTextView);
                String textStr =  "No results returned.";
                tv.setText(textStr);
            } else {
                String textStr =  "Data retrieved using the Google Calendar API:";
                TextView tv = (TextView) findViewById(R.id.testTextView);

                for ( Event e : output )
                {
                    // String with Event Name, Start and End time
                    DateTime start = e.getStart().getDateTime();
                    if (start == null) {
                        start = e.getStart().getDate();
                    }
                    DateTime end = e.getEnd().getDateTime();
                    if (end == null) {
                        end = e.getEnd().getDate();
                    }
                    String evtStr = String.format("%s \n(%s) (%s)", e.getSummary(), start, end);

                    textStr += "\n" + evtStr;
                }

                tv.setText(textStr);
            }
        }

        @Override
        protected void onCancelled() {
            progressDialog.hide();
            if (lastError != null) {
                if (lastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) lastError)
                                    .getConnectionStatusCode());
                } else if (lastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) lastError).getIntent(),
                            StatisticsActivity.REQUEST_AUTHORIZATION);
                } else {
                    TextView tv = (TextView) findViewById(R.id.testTextView);
                    String textStr = String.format("%s\n%s", "The following error occurred:", lastError.getMessage() );
                    tv.setText( textStr );
                }
            } else {
                TextView tv = (TextView) findViewById(R.id.testTextView);
                String textStr = "Request cancelled.";
                tv.setText(textStr);
            }
        }
    }
}
