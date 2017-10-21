package com.example.secumfex.workingtimetracker;

import android.preference.PreferenceActivity;

import java.util.List;

public class AppPreferenceActivity extends PreferenceActivity
{
    @Override
    public void onBuildHeaders(List<Header> target)
    {
        loadHeadersFromResource(R.xml.headers_preference, target);
    }

    @Override
    protected boolean isValidFragment(String fragmentName)
    {
        return AppPreferenceFragment.class.getName().equals(fragmentName);
    }
}