package org.opennms.plugins.dbnotifier;

import com.impossibl.postgres.api.jdbc.PGConnection;
import com.impossibl.postgres.api.jdbc.PGNotificationListener;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Statement;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;


public class DatabaseChangeNotifier {

	private static final Logger LOG = LoggerFactory.getLogger(DatabaseChangeNotifier.class);

	/**
	 * If NOTIFY_ALARM_CHANGES is added to paramList in Constructor, triggers are applied to alarms table
	 */
	public static final String NOTIFY_ALARM_CHANGES="NOTIFY_ALARM_CHANGES";

	/**
	 * If  NOTIFY_EVENT_CHANGES is added to paramList in Constructor, triggers are applied to events table
	 */
	public static final String NOTIFY_EVENT_CHANGES="NOTIFY_EVENT_CHANGES";

	// if true triggers are applied to events table
	private boolean listenForEvents=false;

	// if true triggers are applied to alarms table
	private boolean listenForAlarms=false;

	// SQL statement to remove triggers from events table
	private String disConnectionStatementEvents="";

	// SQL statement to set up triggers on events table
	private String connectionStatementEvents="";

	// SQL statement to remove triggers from alarms table
	private String disConnectionStatementAlarms="";

	// SQL statement to set up triggers on alarms table
	private String connectionStatementAlarms="";

	private PGConnection pgConnection;

	private PGNotificationListener pgListener;

	private Set<DbNotificationClient> dbNotificationClientList = Collections.synchronizedSet(new HashSet<DbNotificationClient>());

	/**
	 * adds new DbNotificationClient to list of clients which will be sent database notifications
	 * @param dbNotificationClient
	 */
	public void addDbNotificationClient(DbNotificationClient dbNotificationClient){
		LOG.debug("adding dbNotificationClient:"+dbNotificationClient.toString());
		dbNotificationClientList.add(dbNotificationClient);
	}

	/**
	 * removes DbNotificationClient from list of clients which will be sent database notifications
	 * @param dbNotificationClient
	 */
	public void removeDbNotificationClient(DbNotificationClient dbNotificationClient){
		LOG.debug("removing dbNotificationClient:"+dbNotificationClient.toString());
		dbNotificationClientList.remove(dbNotificationClient);
	}

	/**
	 * 
	 * @return SQL statement to set up triggers on events table
	 */
	public String getConnectionStatementEvents() {
		return connectionStatementEvents;
	}

	/**
	 * 
	 * @param connectionStatementEvents SQL statement to set up triggers on events table
	 */
	public void setConnectionStatementEvents(String connectionStatementEvents) {
		this.connectionStatementEvents = connectionStatementEvents;
	}

	/**
	 * 
	 * @return SQL statement to remove triggers from events table
	 */
	public String getDisConnectionStatementEvents() {
		return disConnectionStatementEvents;
	}

	/**
	 * 
	 * @param disConnectionStatementEvents SQL statement to remove triggers from events table
	 */
	public void setDisConnectionStatementEvents(String disConnectionStatementEvents) {
		this.disConnectionStatementEvents = disConnectionStatementEvents;
	}

	/**
	 * 
	 * @return connectionStatementAlarms SQL statement to set up triggers on alarms table
	 */
	public String getConnectionStatementAlarms() {
		return connectionStatementAlarms;
	}

	/**
	 * 
	 * @param connectionStatementAlarms SQL statement to set up triggers on alarms table
	 */
	public void setConnectionStatementAlarms(String connectionStatementAlarms) {
		this.connectionStatementAlarms = connectionStatementAlarms;
	}

	/**
	 * 
	 * @return disConnectionStatementAlarms SQL statement to remove triggers from alarms table
	 */
	public String getDisConnectionStatementAlarms() {
		return disConnectionStatementAlarms;
	}

	/**
	 * 
	 * @param disConnectionStatementAlarms SQL statement to remove triggers from alarms table
	 */
	public void setDisConnectionStatementAlarms(String disConnectionStatementAlarms) {
		this.disConnectionStatementAlarms = disConnectionStatementAlarms;
	}

	/**
	 * Constructor sets up database connections and listeners
	 * @param dataSource 
	 * @param paramList
	 * @throws Throwable
	 */
	public DatabaseChangeNotifier(DbNotifierDataSourceFactory dsFactory, List<String> paramList) throws Throwable {

		DataSource dataSource=dsFactory.getPGDataSource();
		
		if(LOG.isDebugEnabled()) {
			String s="DatabaseChangeNotifier Paramaters: ";

			for(String param : paramList){
				s=s+param+" ";
			}
			LOG.debug(s);
			LOG.debug("setting up connection - be patient this is quite slow");
		}

		if (paramList.contains(NOTIFY_ALARM_CHANGES)) listenForAlarms=true;	
		if (paramList.contains(NOTIFY_EVENT_CHANGES)) listenForEvents=true;

		pgConnection = (PGConnection) dataSource.getConnection();

		LOG.debug("setting up connection listener");

		// pgListner is set up outside pgConnection to give hard reference so not garbage collected
		// see http://stackoverflow.com/questions/37916489/listen-notify-pgconnection-goes-down-java
		pgListener = new PGNotificationListener() {
			Logger LOG = LoggerFactory.getLogger(DatabaseChangeNotifier.class);

			@Override
			public void notification(int processId, String channelName, String payload) {

				DbNotification dbn = new DbNotification(processId, channelName, payload);
				
				if(LOG.isDebugEnabled()) {
					LOG.debug("notification received from database - sending to registered clients :\n processId:"+processId
							+ "\n channelName:"+channelName
							+ "\n payload:"+payload);
				}

				// send notifications to registered clients - note each client must return quickly
				synchronized(dbNotificationClientList) {
					Iterator<DbNotificationClient> i = dbNotificationClientList.iterator(); // Must be in synchronized block
					while (i.hasNext()){
						i.next().sendDbNotification(dbn);
					}         
				}


			}
		};

		pgConnection.addNotificationListener(pgListener);

	}

	public void init() throws Throwable {
		LOG.debug("initialising DatabaseChangeNotifier");
		Statement statement = pgConnection.createStatement();

		if(listenForEvents){
			LOG.debug("Executing connectionStatementEvents="+connectionStatementEvents);
			statement.execute(connectionStatementEvents);

			LOG.debug("Executing 'LISTEN opennms_event_changes'");
			statement.execute("LISTEN opennms_event_changes");
		}

		if(listenForAlarms){
			LOG.debug("Executing connectionStatementAlarms="+connectionStatementAlarms);
			statement.execute(connectionStatementAlarms);

			LOG.debug("Executing 'LISTEN opennms_alarm_changes'");
			statement.execute("LISTEN opennms_alarm_changes");
		}

		statement.close();

	}

	public void destroy() throws Throwable {
		LOG.debug("stopping DatabaseChangeNotifier");

		LOG.debug("clearing dbNotificationClientList");
		dbNotificationClientList.clear();

		Statement statement = pgConnection.createStatement();

		if(listenForEvents){

			LOG.debug("Executing 'UNLISTEN opennms_event_changes'");
			statement.execute("UNLISTEN opennms_event_changes");

			LOG.debug("Executing disConnectionStatementEvents="+disConnectionStatementEvents);
			statement.execute(disConnectionStatementEvents);
		}

		if(listenForAlarms){

			LOG.debug("Executing 'UNLISTEN opennms_alarm_changes'");
			statement.execute("UNLISTEN opennms_alarm_changes");

			LOG.debug("Executing disConnectionStatementAlarms="+disConnectionStatementAlarms);
			statement.execute(disConnectionStatementAlarms);
		}

		statement.close();
	}
}

