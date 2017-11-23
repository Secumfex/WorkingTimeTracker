package com.example.secumfex.workingtimetracker;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;

/**
 * This class provides general purpose, auxiliary, utility functionality.
 *
 * Created by Secumfex on 21.10.2017.
 */

class Utils {
    public static void saveObjectToSharedPreference(Context context, String preferenceFileName, String serializedObjectKey, Object object) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(preferenceFileName, 0);
        SharedPreferences.Editor sharedPreferencesEditor = sharedPreferences.edit();
        final Gson gson = new Gson();
        String serializedObject = gson.toJson(object);
        sharedPreferencesEditor.putString(serializedObjectKey, serializedObject);
        sharedPreferencesEditor.apply();
    }

    public static <GenericClass> GenericClass getSavedObjectFromPreference(Context context, String preferenceFileName, String preferenceKey, Class<GenericClass> classType) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(preferenceFileName, 0);
        if (sharedPreferences.contains(preferenceKey)) {
            final Gson gson = new Gson();
            return gson.fromJson(sharedPreferences.getString(preferenceKey, ""), classType);
        }
        return null;
    }

    static long getTimeValueSeconds( int seconds )
    {
        return 1000 * seconds;
    }

    static long getTimeValueMinutes( int minutes )
    {
        return getTimeValueSeconds( 60 * minutes );
    }
    static long getTimeValueHours( int hours )
    {
        return getTimeValueMinutes( 60 * hours );
    }
    static long getTimeValueDays( int days )
    {
        return getTimeValueHours( 24 * days );
    }
    static long getTimeValue(int days, int hours, int minutes, int seconds)
    {
        return getTimeValueDays( days ) + getTimeValueHours( hours ) + getTimeValueMinutes( minutes ) + getTimeValueSeconds( seconds );
    }

    static int getHours( long timeValue )
    {
        return getMinutes(timeValue) / 60;
    }

    static int getMinutes( long timeValue )
    {
        return (int) timeValue / (1000 * 60);
    }
}
