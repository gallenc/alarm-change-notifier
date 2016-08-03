CREATE OR REPLACE FUNCTION notify_alarm() RETURNS TRIGGER AS $$

    DECLARE 
        new_data json;
        old_data json;
        notification text= '[{},{}]';
    
    BEGIN

/*
        -- create a json array and populate with [NEW_row_json,OLD_row_json]
        -- Convert the old or new row to JSON, based on the kind of action.
        -- Action = DELETE              -> [empty_object,OLD_row_json]
        -- Action = INSERT              -> [NEW_row_json,empty_object]
        -- Action = UPDATE              -> [NEW_row_json,OLD_row_json]
*/
	    
	    
	    IF (TG_OP = 'INSERT') THEN 
	        new_data=row_to_json(NEW);
	        notification=CONCAT('[', new_data::text, ', {}]');
	        PERFORM pg_notify('opennms_alarm_changes',notification);
	        
        ELSEIF (TG_OP = 'DELETE') THEN
            old_data=row_to_json(OLD);
	        notification=CONCAT('[{}, ', old_data::text, ']');
	        PERFORM pg_notify('opennms_alarm_changes',notification);
            
        ELSE
        /* IGNORE rows where only event count updated*/
            new_data=row_to_json(NEW);
            old_data=row_to_json(OLD);
            notification=CONCAT('[', new_data::text,',', old_data::text, ']');
            
        END IF;

        PERFORM pg_notify('opennms_alarm_changes',notification);
        
/*
        -- Result is ignored since this is an AFTER trigger
*/
        RETURN NULL; 
    END;
    
$$ LANGUAGE plpgsql;

 DROP TRIGGER IF EXISTS alarms_change_notify ON alarms;

 CREATE TRIGGER alarms_change_notify
 AFTER INSERT OR UPDATE OR DELETE ON alarms
    FOR EACH ROW EXECUTE PROCEDURE notify_alarm();
    
    
 -- NOTIFY dml_events, 'some message';

--SELECT array_to_json(array_agg(events)) FROM events;

--SELECT * FROM EVENTS WHERE eventid=3529;

--SELECT row_to_json(select * from events WHERE eventid=3529) ;

--SELECT array_to_json(array_agg(events)) FROM events WHERE eventid=3529;

--SELECT row_to_json(events) from events WHERE eventid=3529 ;

--NOTIFY dml_events, SELECT array_to_json(array_agg(events)) FROM events WHERE eventid=3529;

CREATE OR REPLACE FUNCTION notify_event() RETURNS TRIGGER AS $$

    DECLARE 
        data json;
        notification json;
    
    BEGIN
    
        -- Convert the old or new row to JSON, based on the kind of action.
        -- Action = DELETE?             -> OLD row
        -- Action = INSERT or UPDATE?   -> NEW row
        IF (TG_OP = 'DELETE') THEN
            data = row_to_json(OLD);
        ELSE
            data = row_to_json(NEW);
        END IF;
        
        -- Contruct the notification as a JSON string. ( only in 9.4 not 9.2)
        --notification = json_build_object(
        --                  'table',TG_TABLE_NAME,
        --                  'action', TG_OP,
        --                  'data', data);
        notification = data;
        
                        
        -- Execute pg_notify(channel, notification)
        PERFORM pg_notify('dml_events',notification::text);
        
        -- Result is ignored since this is an AFTER trigger
        RETURN NULL; 
    END;
    
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS events_change_notify ON events;

CREATE TRIGGER events_change_notify
AFTER INSERT OR UPDATE OR DELETE ON events
    FOR EACH ROW EXECUTE PROCEDURE notify_event();
 
 
 --select * from events where eventid=3529;
UPDATE events SET eventackuser= 'Dramatic' where eventid=3529;
 
 

 
    
  