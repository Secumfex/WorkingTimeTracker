package com.example.secumfex.workingtimetracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.google.gson.Gson;

import java.lang.reflect.Type;

/**
 * This class provides general purpose, auxiliary, utility functionality.
 *
 * Created by Secumfex on 21.10.2017.
 */

class Utils {
    /** Serialize an Object utilizing a TypeToken object (i.e. for generic types like List<T>) */
    public static String serialize(Object object, Type type)
    {
        Gson gson = new Gson();
        return gson.toJson(object);
    }

    /** Serialize an Object */
    public static String serialize(Object object)
    {
        Gson gson = new Gson();
        return gson.toJson(object);
    }

    /** Deserialize an Object utilizing a TypeToken object (i.e. for generic types like List<T>) */
    public static <GenericClass> GenericClass deserialize(String object, Type classType)
    {
        Gson gson = new Gson();
        return gson.fromJson(object, classType);
    }

    /** Deserialize an Object utilizing a TypeToken object (i.e. for generalized types like List<T>) */
    public static <GenericClass> GenericClass deserialize(String object, Class<GenericClass> classType)
    {
        Gson gson = new Gson();
        return gson.fromJson(object, classType);
    }

    /**
     * Save an object in the specified shared preferences (uses {@link #serialize(Object)})
     */
    public static void saveObjectToSharedPreferences(Context context, String preferenceFileName, String serializedObjectKey, Object object) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(preferenceFileName, 0);
        SharedPreferences.Editor sharedPreferencesEditor = sharedPreferences.edit();
        String serializedObject = serialize(object);
        sharedPreferencesEditor.putString(serializedObjectKey, serializedObject);
        sharedPreferencesEditor.apply();
    }

    /**
     * Save an object in the specified shared preferences (uses {@link #serialize(Object)})
     */
    public static void saveObjectToDefaultPreferences(Context context, String preferenceFileName, String serializedObjectKey, Object object) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor sharedPreferencesEditor = sharedPreferences.edit();
        String serializedObject = serialize(object);
        sharedPreferencesEditor.putString(serializedObjectKey, serializedObject);
        sharedPreferencesEditor.apply();
    }

    /**
     * Load an object from the specified shared preferences (uses @link #deserialize(String, Class)})
     */
    public static <GenericClass> GenericClass loadSavedObjectFromPreferences(Context context, String preferenceFileName, String preferenceKey, Class<GenericClass> classType) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(preferenceFileName, 0);
        if (sharedPreferences.contains(preferenceKey)) {
            final Gson gson = new Gson();
            return deserialize(sharedPreferences.getString(preferenceKey, ""), classType);
        }
        return null;
    }

    /**
     * Load an object from the specified shared preferences (uses {@link #deserialize(String, Class)})
     */
    public static <GenericClass> GenericClass loadSavedObjectFromDefaultPreferences(Context context, String preferenceKey, Class<GenericClass> classType) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (sharedPreferences.contains(preferenceKey)) {
            final Gson gson = new Gson();
            return deserialize(sharedPreferences.getString(preferenceKey, ""), classType);
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
