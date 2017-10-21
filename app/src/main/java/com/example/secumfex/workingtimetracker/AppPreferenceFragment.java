package com.example.secumfex.workingtimetracker;

import android.os.Bundle;
import android.preference.PreferenceFragment;

/**
 * This class represents the Preference Fragment
 *
 * Created by Secumfex on 21.10.2017.
 */
public class AppPreferenceFragment extends PreferenceFragment
{
    @Override
    public void onCreate(final Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
    }
}
