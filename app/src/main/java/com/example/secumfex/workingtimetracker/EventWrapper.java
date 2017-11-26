package com.example.secumfex.workingtimetracker;

import com.google.api.client.util.DateTime;

/**
 * Simple wrapper for Google Calendar Events
 */
public class EventWrapper {
    public final String id;
    public final String summary;
    public final String description;
    public final String location;
    public final long start;
    public final long end;
    public final long updated;

    EventWrapper(com.google.api.services.calendar.model.Event event)
    {
        this.id = event.getId();
        this.summary = event.getSummary();
        this.description = event.getDescription();
        this.location = event.getLocation();
        if (event.getStart() != null)
        {
            this.start = event.getStart().getDateTime().getValue();
        }
        else
        {
            this.start = -1;
        }
        if (event.getEnd() != null)
        {
            this.end = event.getEnd().getDateTime().getValue();
        }
        else
        {
            this.end= -1;
        }
        if (event.getUpdated() != null)
        {
            this.updated = event.getUpdated().getValue();
        }
        else
        {
            this.updated = -1;
        }
    }
}
