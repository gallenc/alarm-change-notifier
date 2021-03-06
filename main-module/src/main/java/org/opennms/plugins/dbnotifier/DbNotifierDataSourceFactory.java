package org.opennms.plugins.dbnotifier;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import com.impossibl.postgres.jdbc.PGDataSource;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This reads the OpenNMS database configuration from the 
 * OpenNMS database configuration file opennms-datasources.xml
 * and uses it to configure the jdbc-ng connector with 
 * userName, passWord, dataBaseName, hostname, port;
 * 
 * The opennms-datasources.xml location should be set in the dataSourceFileUri parameter. 
 * If it is not set then the factory tries to load the file from the class path.
 * 
 * We use DOM parsing to read the datasource-configuration 
 * since the castor XML classes are not in the Karaf classpath
 * 
 <datasource-configuration>
  <jdbc-data-source name="opennms" 
        database-name="opennms" 
        class-name="org.postgresql.Driver" 
        url="jdbc:postgresql://localhost:5432/opennms"
        user-name="opennms"
        password="opennms" />
  </datasource-configuration>
 * 
 * If the databaseName attribute is set then the factory simply uses the relevant
 * field values rather than loading from opennms-datasources.xml
 */
public class DbNotifierDataSourceFactory {
	private static final Logger LOG = LoggerFactory.getLogger(DbNotifierDataSourceFactory.class);

	private static final String OPENNMS_DATASOURCE_CONFIG_FILE_NAME="opennms-datasources.xml";

	private static final String OPENNMS_DATA_SOURCE_NAME = "opennms";

	private String userName  = null;
	private String passWord  = null;
	private String dataBaseName  = null;
	private String hostname=null;
	private int port=5432;
	private String dsfileUri=null;


	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getPassWord() {
		return passWord;
	}

	public void setPassWord(String passWord) {
		this.passWord = passWord;
	}

	public String getDataBaseName() {
		return dataBaseName;
	}

	public void setDataBaseName(String dataBaseName) {
		this.dataBaseName = dataBaseName;
	}

	public String getHostname() {
		return hostname;
	}

	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	public String getPort() {
		return Integer.toString(port);
	}

	public void setPort(String port) {
		this.port = Integer.parseInt(port);
	}

	/**
	 * @return the dsfileUri
	 */
	public String getDataSourceFileUri() {
		return dsfileUri;
	}

	/**
	 * @param dsfileUri the dsfileUri to set
	 */
	public void setDataSourceFileUri(String fileUri) {
		this.dsfileUri = fileUri;
	}

	public PGDataSource getPGDataSource(){

		PGDataSource pgdc=new PGDataSource();
		pgdc.setHost(hostname);
		pgdc.setPort(port);
		pgdc.setDatabase(dataBaseName);
		pgdc.setUser(userName);
		pgdc.setPassword(passWord);

		return pgdc;

	}


	/**
	 *  This method initialises the class by loading the database values
	 *  either from the config .cfg file or from opennms-datasources.xml
	 */
	public void init(){

		if(dataBaseName!=null){
			LOG.debug("using values supplied in .cfg file for host: "+hostname
					+ " port: "+port
					+ " dataBaseName "+ dataBaseName+" userName: "+userName+ "  password :" + passWord);
		} else {

			try {

				// try loading from file or from classpath
				InputStream istream=null;
				if(dsfileUri!=null){
					File dsFile=new File(dsfileUri);
					LOG.debug("loading database config from "+dsFile.getAbsolutePath());
					istream= new FileInputStream(dsFile);
				}

				if(istream==null){	
					LOG.debug("loading database config from classpath file"+OPENNMS_DATASOURCE_CONFIG_FILE_NAME);
					istream= this.getClass().getClassLoader().getResourceAsStream(OPENNMS_DATASOURCE_CONFIG_FILE_NAME);
					if(istream==null) throw new RuntimeException("could not load database config from classpath "+OPENNMS_DATASOURCE_CONFIG_FILE_NAME);
				}


				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				DocumentBuilder builder = factory.newDocumentBuilder();
				Document document = builder.parse(istream);

				document.getDocumentElement().normalize();

				NodeList listOfDatasources = document.getElementsByTagName("jdbc-data-source");

				String dataSourceName=null;
				String urlStr=null;
				int index = 0;

				while( ( ! OPENNMS_DATA_SOURCE_NAME.equals(dataSourceName) )
						&& (index<listOfDatasources.getLength()) ){

					Node n = listOfDatasources.item(index);
					NamedNodeMap attrs = n.getAttributes();
					dataSourceName = attrs.getNamedItem("name").getNodeValue();
					userName = attrs.getNamedItem("user-name").getNodeValue();
					passWord = attrs.getNamedItem("password").getNodeValue();
					dataBaseName = attrs.getNamedItem("database-name").getNodeValue();
					urlStr = attrs.getNamedItem("url").getNodeValue();
					index++;
				}

				// parse out the hostname and port by removing jdbc:postgresql:
				String baseUrl=	urlStr.replace("jdbc:postgresql:","http:");

				URL url= new URL(baseUrl);
				String protocol = url.getProtocol();
				hostname = url.getHost();
				port = url.getPort();

				LOG.debug("decoded urlStr:"+urlStr +""
						+ " baseUrl:"+baseUrl
						+ " protocol:"+protocol
						+ " host:"+hostname
						+ " port:"+port);

				LOG.debug("Using jdbc-data-source values supplied for "+OPENNMS_DATA_SOURCE_NAME
						+ " datasource in "
						+ OPENNMS_DATASOURCE_CONFIG_FILE_NAME
						+ " file for host: "+hostname
						+ " port: "+port
						+ " dataBaseName "+ dataBaseName+" userName: "+userName+ "  password :" + passWord);

			} catch (Exception e) {
				throw new RuntimeException("cannot load database values from .cfg or "+OPENNMS_DATASOURCE_CONFIG_FILE_NAME, e);
			}
		}
	}



}


