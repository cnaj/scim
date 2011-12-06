/*
 * Copyright 2011 UnboundID Corp.
 * All Rights Reserved.
 */

package com.unboundid.scim.extension;

import com.unboundid.directory.tests.standalone.DirectoryInstance;
import com.unboundid.directory.tests.standalone.ExternalInstance;
import com.unboundid.directory.tests.standalone.ExternalInstanceId;
import com.unboundid.directory.tests.standalone.ExternalInstanceManager;
import com.unboundid.directory.tests.standalone.TestCaseUtils;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.scim.sdk.SCIMEndpoint;
import com.unboundid.scim.sdk.SCIMService;
import com.unboundid.scim.data.AuthenticationScheme;
import com.unboundid.scim.data.Entry;
import com.unboundid.scim.data.GroupResource;
import com.unboundid.scim.data.Manager;
import com.unboundid.scim.data.Name;
import com.unboundid.scim.data.ServiceProviderConfig;
import com.unboundid.scim.data.UserResource;
import com.unboundid.scim.sdk.PageParameters;
import com.unboundid.scim.sdk.ResourceNotFoundException;
import com.unboundid.scim.sdk.Resources;
import com.unboundid.scim.sdk.SCIMConstants;
import com.unboundid.scim.sdk.SCIMException;
import com.unboundid.scim.sdk.SortParameters;
import com.unboundid.scim.sdk.Version;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustStoreTrustManager;
import org.apache.wink.client.ClientAuthenticationException;
import org.apache.wink.client.ClientConfig;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;



/**
 * Test coverage for the SCIM server extension. This class tests the extension
 * running in the Directory Server, and serves as a base class to allow the
 * extension to be tested in the Proxy Server.
 */
@Test(sequential = true)
public class SCIMExtensionTestCase extends ServerExtensionTestCase
{
  /**
   * The Directory Server external instance.
   */
  protected DirectoryInstance dsInstance;

  /**
   * A reference to the external instance where the SCIM extension is installed.
   */
  protected ExternalInstance scimInstance;

  /**
   * The SCIM service client.
   */
  protected SCIMService service;

  /**
   * The SCIM secure service client.
   */
  protected SCIMService secureService;

  /**
   * Base DN of the Directory Server.
   */
  protected String baseDN;

  /**
   * The non-secure SCIM port.
   */
  protected int scimPort;

  /**
   * The secure SCIM port.
   */
  protected int scimSecurePort;


  /**
   * Set up SCIM services before running the tests.
   *
   * @throws Exception  If an error occurs.
   */
  protected void setupServices() throws Exception
  {
    scimPort = TestCaseUtils.getFreePort();
    scimSecurePort = TestCaseUtils.getFreePort();
    configureExtension(scimInstance, scimPort, scimSecurePort);

    // Use the external instance trust store as our client trust store.
    final File pwFile =
        scimInstance.fileRelativeToInstanceRoot("config/truststore.pin");
    final char[] pw =
        TestCaseUtils.readFileToLines(pwFile).get(0).toCharArray();
    final KeyManager keyManager = null;
    final TrustManager trustManager =
        new TrustStoreTrustManager(
            scimInstance.fileRelativeToInstanceRoot("config/truststore"),
            pw, "JKS", true);

    final SSLUtil sslUtil = new SSLUtil(keyManager, trustManager);
//    final SSLUtil sslUtil = new SSLUtil(new TrustAllTrustManager());

    final ClientConfig clientConfig =
        createManagerClientConfig(scimInstance, sslUtil.createSSLContext());

    final URI uri = new URI("http", null, scimInstance.getLdapHost(), scimPort,
                            null, null, null);
    service = new SCIMService(uri, clientConfig);

    final URI secureUri = new URI("https", null, scimInstance.getLdapHost(),
                                  scimSecurePort, null, null, null);
    secureService = new SCIMService(secureUri, clientConfig);

    dsInstance.dsconfig(
        "set-access-control-handler-prop",
        "--add", "global-aci:" +
                 "(targetcontrol=\"1.3.6.1.1.13.2 || 1.2.840.113556.1.4.473 " +
                 "|| 2.16.840.1.113730.3.4.9\")" +
                 "(version 3.0;" +
                 "acl \"Authenticated access to controls used by the " +
                 "SCIM plugin\"; allow (all) userdn=\"ldap:///all\";)",
        "--add", "global-aci:" +
                 "(targetattr!=\"userPassword\")" +
                 "(version 3.0; " +
                 "acl \"Allow anonymous read access for anyone\"; " +
                 "allow (read,search,compare) userdn=\"ldap:///anyone\";)",
        "--add", "global-aci:" +
                 "(targetattr=\"*\")" +
                 "(version 3.0; " +
                 "acl \"Allow users to update their own entries\"; " +
                 "allow (write) userdn=\"ldap:///self\";)"
        );
  }



  /**
   * Set up before running the tests.
   *
   * @throws Exception  If an error occurs.
   */
  @BeforeClass(alwaysRun = true)
  public void setup() throws Exception
  {
    final ExternalInstanceManager m = ExternalInstanceManager.singleton();

    dsInstance = m.getExternalInstance(ExternalInstanceId.BasicDirectoryServer);

    dsInstance.runSetup("--ldapsPort",
                      String.valueOf(TestCaseUtils.getFreePort()),
                      "--generateSelfSignedCertificate",
                      "--doNotStart");

    final File extensionZipFile =
        new File(System.getProperty("extensionZipFile"));
    dsInstance.installExtension(extensionZipFile);

    dsInstance.startInstance();
    dsInstance.addBaseEntry();

    baseDN = dsInstance.getPrimaryBaseDN();

    dsInstance.getDnsToDumpOnFailureMutable().add("cn=monitor");
    dsInstance.dsconfig(
        "set-log-publisher-prop",
        "--publisher-name", "File-Based Access Logger",
        "--set", "suppress-internal-operations:false"
    );

    scimInstance = dsInstance;
    setupServices();

    //
    //This can be helpful if you want to make SCIM requests from your browser
    //or some other REST client:
    //
    //JOptionPane.showConfirmDialog(null, "SCIM is listening at: " +
    //        uri.toString(), null, JOptionPane.OK_CANCEL_OPTION);

  }



  /**
   * Tear down after the tests are finished.
   */
  @AfterClass(alwaysRun = true)
  public void tearDown()
  {
    //
    //This can be helpful if you want to attach a debugger to the external
    //instance that is running the SCIM server. Just wait for the dialog to pop
    //up, then navigate to the external instance root, stop it, modify the
    //java.properties to include -Xdebug, etc, and then start it back up. You
    //can leave the dialogue up until you are finished debugging, as it will
    //block this thread.
    //
    //JOptionPane.showConfirmDialog(null, "About to shutdown...", null,
    //                                JOptionPane.OK_CANCEL_OPTION);

    final ExternalInstanceManager m = ExternalInstanceManager.singleton();
    dsInstance.stopInstance();
    m.destroyInstance(dsInstance.getInstanceId());
  }



  /**
   * Tests that the plugin can be installed and enabled.
   *
   * @throws Exception  If the test fails.
   */
  @Test
  public void enablePlugin() throws Exception
  {
    assertEquals(getMonitorAsString(scimInstance, "version"), Version.VERSION);
  }



  /**
   * Tests basic resource creation through SCIM.
   *
   * @throws Exception  If the test fails.
   */
  @Test
  public void testCreate() throws Exception
  {
    //Create a new user
    SCIMEndpoint<UserResource> userEndpoint = service.getUserEndpoint();
    UserResource user = userEndpoint.newResource();
    user.setUserName("jdoe");
    user.setName(
            new Name("John C. Doe", "Doe", "Charles", "John", null, "Sr."));
    user.setTitle("Vice President");
    user.setUserType("Employee");
    Collection<Entry<String>> emails = new HashSet<Entry<String>>(1);
    emails.add(new Entry<String>("j.doe@example.com", "work", true));
    user.setEmails(emails);
    user.setLocale(Locale.US.getDisplayName());
    user.setTimeZone(TimeZone.getDefault().getID());
    user.setSingularAttributeValue(
            SCIMConstants.SCHEMA_URI_ENTERPRISE_EXTENSION, "manager",
            Manager.MANAGER_RESOLVER, new Manager("uid=myManager," + baseDN,
                                                        "Mr. Manager"));

    //Verify basic properties of UserResource
    assertEquals(user.getUserName(), "jdoe");
    assertEquals(user.getName().getFormatted(), "John C. Doe");
    assertEquals(user.getTitle(), "Vice President");
    assertEquals(user.getUserType(), "Employee");
    assertEquals(user.getEmails().iterator().next().getValue(),
                          "j.doe@example.com");
    assertEquals(user.getLocale(), Locale.US.getDisplayName());
    assertEquals(user.getTimeZone(), TimeZone.getDefault().getID());
    assertEquals(user.getSingularAttributeValue(
            SCIMConstants.SCHEMA_URI_ENTERPRISE_EXTENSION, "manager",
            Manager.MANAGER_RESOLVER).getDisplayName(), "Mr. Manager");

    //Mark the start and end time with a 500ms buffer on either side, because
    //the Directory Server will record the actual createTimestamp using the
    //TimeThread, which only updates once every 100ms.
    Date startTime = new Date(System.currentTimeMillis() - 500);
    String beforeCount =
        getMonitorAsString(scimInstance, "user-resource-post-successful");
    UserResource returnedUser = userEndpoint.create(user);
    String afterCount =
        getMonitorAsString(scimInstance, "user-resource-post-successful");
    Date createTime = returnedUser.getMeta().getCreated();
    Date endTime = new Date(System.currentTimeMillis() + 500);

    //Verify what is returned from the SDK
    assertNotNull(returnedUser);
    assertEquals(returnedUser.getUserName(), user.getUserName());
    assertEquals(returnedUser.getName().getFormatted(),
                   user.getName().getFormatted());
    assertEquals(returnedUser.getTitle(), user.getTitle());
    assertEquals(returnedUser.getUserType(), user.getUserType());
    assertEquals(returnedUser.getEmails().iterator().next().getValue(),
        user.getEmails().iterator().next().getValue());
    assertTrue(createTime.after(startTime));
    assertTrue(createTime.before(endTime));
    assertEquals(beforeCount == null ? 0 : Integer.valueOf(beforeCount),
        Integer.valueOf(afterCount) - 1);

    //TODO: no LDAP schema for Locale
    //assertEquals(returnedUser.getLocale(), user.getLocale());

    //TODO: no LDAP schema for TimeZone
    //assertEquals(returnedUser.getTimeZone(), user.getTimeZone());

    assertEquals(returnedUser.getSingularAttributeValue(
            SCIMConstants.SCHEMA_URI_ENTERPRISE_EXTENSION, "manager",
            Manager.MANAGER_RESOLVER).getManagerId(),
                 user.getSingularAttributeValue(
                   SCIMConstants.SCHEMA_URI_ENTERPRISE_EXTENSION, "manager",
                   Manager.MANAGER_RESOLVER).getManagerId());


    //Verify what is actually in the Directory
    SearchResultEntry entry =
      dsInstance.getConnectionPool().getEntry("uid=jdoe," + baseDN, "*", "+");
    assertNotNull(entry);
    assertTrue(entry.hasObjectClass("iNetOrgPerson"));
    assertEquals(entry.getAttributeValue("cn"), "John C. Doe");
    assertEquals(entry.getAttributeValue("givenName"), "John");
    assertEquals(entry.getAttributeValue("sn"), "Doe");
    assertEquals(entry.getAttributeValue("title"), "Vice President");
    assertEquals(entry.getAttributeValue("mail"), "j.doe@example.com");
    assertEquals(entry.getAttributeValue("manager"), "uid=myManager,"+baseDN);

    System.out.println("Full user entry:\n" + entry.toLDIFString());

    //Create a new group
    SCIMEndpoint<GroupResource> grpEndpoint = service.getGroupEndpoint();
    GroupResource group = grpEndpoint.newResource();
    group.setDisplayName("Engineering");
    Collection<Entry<String>> members = new HashSet<Entry<String>>();
    members.add(new Entry<String>(returnedUser.getId(), "User", false));
    group.setMembers(members);

    //Verify the basic properties of GroupResource
    assertEquals(group.getDisplayName(), "Engineering");
    assertEquals(group.getMembers().iterator().next().getValue(),
                          returnedUser.getId());

    //Do the create and verify what is returned from the endpoint
    startTime = new Date(System.currentTimeMillis() - 500);
    beforeCount =
        getMonitorAsString(scimInstance, "group-resource-post-successful");
    GroupResource returnedGroup = grpEndpoint.create(group);
    afterCount =
        getMonitorAsString(scimInstance, "group-resource-post-successful");
    createTime = returnedGroup.getMeta().getCreated();
    endTime = new Date(System.currentTimeMillis() + 500);

    assertNotNull(returnedGroup);
    assertEquals(returnedGroup.getDisplayName(), group.getDisplayName());
    assertTrue(createTime.after(startTime));
    assertTrue(createTime.before(endTime));
    assertEquals(returnedGroup.getMembers().iterator().next().getValue(),
        group.getMembers().iterator().next().getValue());
    assertEquals(beforeCount == null ? 0 : Integer.valueOf(beforeCount),
        Integer.valueOf(afterCount) - 1);

    //Verify what is actually in the Directory
    entry = dsInstance.getConnectionPool().getEntry(
                        "cn=Engineering," + baseDN, "*", "+");
    assertNotNull(entry);
    assertTrue(entry.hasObjectClass("groupOfUniqueNames"));
    assertEquals(entry.getAttributeValue("cn"), "Engineering");
    assertEquals(entry.getAttributeValue("uniqueMember"), returnedUser.getId(),
            entry.toLDIFString());

    System.out.println("Full group entry:\n" + entry.toLDIFString());
  }



  /**
   * Tests a basic modify operation against a user resource using PUT.
   *
   * @throws Exception If the test fails.
   */
  @Test
  public void testModifyWithPut() throws Exception
  {
    //Add an entry to the Directory
    dsInstance.addEntry("dn: uid=testModifyWithPut," + baseDN,
                      "objectclass: top",
                      "objectclass: person",
                      "objectclass: organizationalPerson",
                      "objectclass: inetOrgPerson",
                      "uid: testModifyWithPut",
                      "cn: testModifyWithPut",
                      "givenname: Test",
                      "sn: User");

    //Update the entry via SCIM
    SCIMEndpoint<UserResource> userEndpoint = service.getUserEndpoint();
    UserResource user = userEndpoint.get("uid=testModifyWithPut," + baseDN);
    assertNotNull(user);
    //This will change the 'cn' to 'Test User'
    user.setName(new Name("Test User", "User", null, "Test", null, null));
    user.setUserType("Employee");
    user.setTitle("Chief of Operations");
    user.setDisplayName("Test Modify with PUT");

    //Mark the start and end time with a 500ms buffer on either side, because
    //the Directory Server will record the actual modifyTimestamp using the
    //TimeThread, which only updates once every 100ms.
    Date startTime = new Date(System.currentTimeMillis() - 500);
    UserResource returnedUser = userEndpoint.update(user);
    Date lastModified = returnedUser.getMeta().getLastModified();
    Date endTime = new Date(System.currentTimeMillis() + 500);

    //Verify what is returned from the SDK
    assertTrue(returnedUser.getId().equalsIgnoreCase(
        "uid=testModifyWithPut," + baseDN));
    assertEquals(returnedUser.getUserName(), "testModifyWithPut");
    assertEquals(returnedUser.getName().getFormatted(), "Test User");
    assertEquals(returnedUser.getName().getGivenName(), "Test");
    assertEquals(returnedUser.getName().getFamilyName(), "User");
    assertEquals(returnedUser.getTitle(), user.getTitle());
    assertEquals(returnedUser.getDisplayName(), user.getDisplayName());
    assertTrue(lastModified.after(startTime));
    assertTrue(lastModified.before(endTime));

    //Verify the contents of the entry in the Directory
    SearchResultEntry entry =
      dsInstance.getConnectionPool().getEntry(
          "uid=testModifyWithPut," + baseDN);
    assertNotNull(entry);
    assertTrue(entry.hasObjectClass("inetOrgPerson"));
    assertEquals(entry.getAttributeValue("uid"), "testModifyWithPut");
    assertEquals(entry.getAttributeValue("cn"), "Test User");
    assertEquals(entry.getAttributeValue("givenname"), "Test");
    assertEquals(entry.getAttributeValue("sn"), "User");
    assertEquals(entry.getAttributeValue("title"), "Chief of Operations");
    assertEquals(entry.getAttributeValue("displayName"),
                                                   "Test Modify with PUT");

    //Make the exact same update again; this should result in no net change to
    //the Directory entry, but should not fail.
    String beforeCount =
        getMonitorAsString(scimInstance, "user-resource-put-successful");
    // returnedUser =
    userEndpoint.update(user);
    String afterCount =
        getMonitorAsString(scimInstance, "user-resource-put-successful");

    assertEquals(beforeCount == null ? 0 : Integer.valueOf(beforeCount),
        Integer.valueOf(afterCount) - 1);

    beforeCount =
        getMonitorAsString(scimInstance, "user-resource-put-404");
    try
    {
      //Try to update an entry that doesn't exist
      user.setId("uid=fakeUserName," + baseDN);
      user.setUserName("fakeUserName");
      userEndpoint.update(user);
      fail("Expected ResourceNotFoundException when updating " +
            "non-existent user");
    }
    catch(ResourceNotFoundException e)
    {
      //expected
    }
    afterCount =
        getMonitorAsString(scimInstance, "user-resource-put-404");
    assertEquals(beforeCount == null ? 0 : Integer.valueOf(beforeCount),
        Integer.valueOf(afterCount) - 1);
  }



  /**
   *
   * @throws Exception If the test fails.
   */
  @Test(enabled = false)
  public void testModifyWithPatch() throws Exception
  {
    //This is an optional feature of the spec and has not been implemented yet.
  }



  /**
   * Tests the basic DELETE functionality via SCIM.
   *
   * @throws Exception If the test fails.
   */
  @Test
  public void testDelete() throws Exception
  {
    dsInstance.getConnectionPool().add(
           generateUserEntry("testDelete", baseDN, "Test", "User", "password"));

    SCIMEndpoint<UserResource> userEndpoint = service.getUserEndpoint();

    String beforeCount =
        getMonitorAsString(scimInstance, "user-resource-delete-successful");
    userEndpoint.delete("uid=testDelete," + baseDN);
    String afterCount =
        getMonitorAsString(scimInstance, "user-resource-delete-successful");

    assertEquals(beforeCount == null ? 0 : Integer.valueOf(beforeCount),
        Integer.valueOf(afterCount) - 1);

    beforeCount =
        getMonitorAsString(scimInstance, "user-resource-delete-404");
    try
    {
      //Should throw ResourceNotFoundException
      userEndpoint.delete("uid=testDelete," + baseDN);
      fail("Expected ResourceNotFoundException when retrieving " +
              "non-existent user");
    }
    catch(ResourceNotFoundException e)
    {
      //expected
    }
    afterCount =
        getMonitorAsString(scimInstance, "user-resource-delete-404");
    assertEquals(beforeCount == null ? 0 : Integer.valueOf(beforeCount),
        Integer.valueOf(afterCount) - 1);
  }



  /**
   * Tests retrieval of a simple user resource.
   *
   * @throws Exception If the test fails.
   */
  @Test
  public void testRetrieve() throws Exception
  {
    testRetrieve(service, "HTTP");
  }



  /**
   * Tests retrieval of a simple user resource over SSL.
   *
   * @throws Exception If the test fails.
   */
  @Test
  public void testRetrieveSecure() throws Exception
  {
    testRetrieve(secureService, "HTTPS");
  }



  /**
   * Tests retrieval of a simple user resource.
   *
   * @param service  The SCIM service with which to invoke operations.
   * @param connectionHandler  The name of the connection handler that will
   *                           handle the operations.
   *
   * @throws Exception If the test fails.
   */
  private void testRetrieve(final SCIMService service,
                            final String connectionHandler)
      throws Exception
  {
    final String uid = "testRetrieve " + connectionHandler;
    final String dn = "uid=" + uid + "," + baseDN;

    //Add an entry to the Directory
    dsInstance.addEntry("dn: " + dn,
                      "objectclass: top",
                      "objectclass: person",
                      "objectclass: organizationalPerson",
                      "objectclass: inetOrgPerson",
                      "uid: " + uid,
                      "cn: testRetrieve",
                      "givenname: Test",
                      "sn: User",
                      "title: Chief of R&D",
                      "displayName: John Smith",
                      "mail: jsmith@example.com",
                      "employeeType: Engineer");

    //Try to retrieve the user via SCIM
    SCIMEndpoint<UserResource> userEndpoint = service.getUserEndpoint();
    String beforeCount =
        getMonitorAsString(scimInstance, connectionHandler,
                           "user-resource-get-successful");
    UserResource user = userEndpoint.get(dn);
    String afterCount =
        getMonitorAsString(scimInstance, connectionHandler,
                           "user-resource-get-successful");
    assertNotNull(user);
    assertTrue(user.getId().equalsIgnoreCase(dn));
    assertEquals(user.getUserName(), uid);
    assertEquals(user.getUserType(), "Engineer");
    assertEquals(user.getName().getFormatted(), "testRetrieve");
    assertEquals(user.getName().getGivenName(), "Test");
    assertEquals(user.getName().getFamilyName(), "User");
    assertEquals(user.getTitle(), "Chief of R&D");
    assertEquals(user.getDisplayName(), "John Smith");
    assertEquals(user.getEmails().iterator().next().getValue(),
                                                "jsmith@example.com");

    //Retrieve the user with only the 'userName' attribute
    user = userEndpoint.get(dn, null, "userName");
    assertNotNull(user);
    assertTrue(user.getId().equalsIgnoreCase(dn));
    assertEquals(user.getUserName(), uid);
    assertNull(user.getUserType());
    assertNull(user.getName());
    assertNull(user.getTitle());
    assertNull(user.getDisplayName());
    assertNull(user.getEmails());

    //Make sure the stats were updated properly
    assertEquals(beforeCount == null ? 0 : Integer.valueOf(beforeCount),
        Integer.valueOf(afterCount) - 1);
  }



  /**
   * Tests the list/query resources functionality using all the different
   * filter types.
   *
   * @throws Exception If the test fails.
   */
  @Test
  public void testFiltering() throws Exception
  {
    final SimpleDateFormat formatter =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
    formatter.setTimeZone(TimeZone.getTimeZone("UTC"));

    long halfwayTime = 0;

    // Create some users.
    final long NUM_USERS = 10;
    for (int i = 0; i < NUM_USERS; i++)
    {
      if(i == NUM_USERS / 2)
      {
        //Record the time when half the entries have been created
        halfwayTime = System.currentTimeMillis();
        TestCaseUtils.sleep(100);
      }

      final String uid = "filterUser." + i;

      com.unboundid.ldap.sdk.Entry e;

      if(i % 2 == 0)
      {
        e = generateUserEntry(uid, baseDN, "Test", "User", "password",
                new Attribute("mail", uid + "@example.com",
                                            "evenNumber@example.com"));
      }
      else if(i % 3 == 0)
      {
        e = generateUserEntry(uid, baseDN, "Test", "User", "password",
                new Attribute("displayName", uid));
      }
      else
      {
        e = generateUserEntry(uid, baseDN, "Test", "User", "password",
                new Attribute("title", "Engineer"));
      }

      dsInstance.getConnectionPool().add(e);
    }

    SCIMEndpoint<UserResource> userEndpoint = service.getUserEndpoint();

    //
    // All of the filters used below first check if the userName starts with
    // "filterUser" so that we only consider the users created for this test.
    //

    String beforeCount =
        getMonitorAsString(scimInstance, "user-resource-query-successful");
    //Test 'eq' (equals)
    Resources<UserResource> results =
        userEndpoint.query("userName sw \"filterUser\" and " +
                               "emails eq \"filterUser.6@example.com\"");
    assertEquals(results.getTotalResults(), 1);
    Iterator<UserResource> iter = results.iterator();
    UserResource user = iter.next();
    assertEquals(user.getUserName(), "filterUser.6");
    assertFalse(iter.hasNext());

    //Test 'co' (contains)
    results =
      userEndpoint.query("userName sw \"filterUser\" and " +
                             "emails co \"User.4@example\"");
    assertEquals(results.getTotalResults(), 1);
    iter = results.iterator();
    user = iter.next();
    assertEquals(user.getUserName(), "filterUser.4");
    assertFalse(iter.hasNext());

    //Test 'sw' (starts with)
    results =
      userEndpoint.query("userName sw \"filterUser\" and title sw \"Eng\"");
    assertEquals(results.getTotalResults(), 3);
    iter = results.iterator();
    while(iter.hasNext())
    {
      user = iter.next();
      String uid = user.getUserName();
      int idx = Integer.parseInt(uid.substring(uid.indexOf(".") + 1));
      assertTrue(idx % 2 != 0);
      assertTrue(idx % 3 != 0);
    }

    //Test 'pr' (present)
    results = userEndpoint.query("userName sw \"filterUser\" and emails pr");
    assertEquals(results.getTotalResults(), 5);
    iter = results.iterator();
    while(iter.hasNext())
    {
      user = iter.next();
      String uid = user.getUserName();
      int idx = Integer.parseInt(uid.substring(uid.indexOf(".") + 1));
      assertTrue(idx % 2 == 0);
    }

    //Test 'gt' (greater than)
    Date halfwayDate = new Date(halfwayTime);
    String formattedTime = formatter.format(halfwayDate);
    results = userEndpoint.query("userName sw \"filterUser\" and " +
                                 "meta.created gt \"" + formattedTime + "\"");
    assertEquals(results.getTotalResults(), 5);
    iter = results.iterator();
    while(iter.hasNext())
    {
      user = iter.next();
      String uid = user.getUserName();
      int idx = Integer.parseInt(uid.substring(uid.indexOf(".") + 1));
      assertTrue(idx >= 5);
    }

    //Test 'lt' (less than)
    results = userEndpoint.query("userName sw \"filterUser\" and " +
                                 "meta.created lt \"" + formattedTime + "\"");
    assertEquals(results.getTotalResults(), 5);
    iter = results.iterator();
    while(iter.hasNext())
    {
      user = iter.next();
      String uid = user.getUserName();
      int idx = Integer.parseInt(uid.substring(uid.indexOf(".") + 1));
      assertTrue(idx < 5);
    }
    String afterCount =
        getMonitorAsString(scimInstance, "user-resource-query-successful");
    assertEquals(beforeCount == null ? 0 : Integer.valueOf(beforeCount),
        Integer.valueOf(afterCount) - 6);
  }



  /**
   * Tests the password change operation via SCIM.
   *
   * @throws Exception If the test fails.
   */
  @Test(enabled = false)
  public void testChangePassword() throws Exception
  {
    //This is an optional feature of the spec and has not been implemented yet.
  }



  /**
   * Tests Group resources and the groups attribute.
   *
   * @throws Exception  If the test fails.
   */
  @Test
  public void testGroups() throws Exception
  {
    final SCIMEndpoint<GroupResource> groupEndpoint =
        service.getGroupEndpoint();
    final SCIMEndpoint<UserResource> userEndpoint =
        service.getUserEndpoint();

    // Create some users.
    UserResource user1 = userEndpoint.newResource();
    user1.setUserName("groupsUser.1");
    user1.setName(new Name("User 1", "GroupsUser", null, "Test", null, null));
    user1 = userEndpoint.create(user1);

    // Create different kinds of groups.
    GroupResource groupOfUniqueNames = groupEndpoint.newResource();
    groupOfUniqueNames.setDisplayName("groupOfUniqueNames");
    final List<Entry<String>> members = new ArrayList<Entry<String>>(1);
    members.add(new Entry<String>(user1.getId(), null, false));
    groupOfUniqueNames.setMembers(members);
    groupOfUniqueNames = groupEndpoint.create(groupOfUniqueNames);

    dsInstance.getConnectionPool().add(
        generateGroupOfNamesEntry("groupOfNames", baseDN,
                                  user1.getId()));
    GroupResource groupOfNames =
        groupEndpoint.get("cn=groupOfNames," + baseDN);

    dsInstance.addEntry(
        "dn: cn=groupOfURLs," + baseDN,
        "objectClass: groupOfURLs",
        "cn: groupOfURLs",
        "memberURL: ldap:///" + baseDN + "??sub?(sn=GroupsUser)"
    );
    GroupResource groupOfURLs =
        groupEndpoint.get("cn=groupOfURLs," + baseDN);

    // Verify that the groups attribute is set correctly.
    user1 = userEndpoint.get(user1.getId());
    assertNotNull(user1.getGroups(), "User does not have the groups attribute");
    assertEquals(user1.getGroups().size(), 3,
                 "User has the wrong number of groups");
    final ArrayList<String> groups = new ArrayList<String>();
    // TODO cannot access display name in multi-valued attributes
//    final ArrayList<String> displayNames = new ArrayList<String>();
    for (final Entry<String> groupEntry : user1.getGroups())
    {
      groups.add(groupEntry.getValue());
//      displayNames.add(groupEntry.getDisplay());
    }
    assertTrue(groups.contains(groupOfNames.getId()));
    assertTrue(groups.contains(groupOfUniqueNames.getId()));
    assertTrue(groups.contains(groupOfURLs.getId()));
//    assertTrue(displayNames.contains("groupOfNames"));
//    assertTrue(displayNames.contains("groupOfUniqueNames"));
//    assertTrue(displayNames.contains("groupOfURLs"));

    // Verify that the members attribute is set correctly.
    assertNotNull(groupOfNames.getMembers());
    assertEquals(groupOfNames.getMembers().size(), 1);
    assertEquals(groupOfNames.getMembers().iterator().next().getValue(),
                 user1.getId());

    assertNotNull(groupOfUniqueNames.getMembers());
    assertEquals(groupOfUniqueNames.getMembers().size(), 1);
    assertEquals(groupOfUniqueNames.getMembers().iterator().next().getValue(),
                 user1.getId());

    assertNotNull(groupOfURLs.getMembers());
    assertEquals(groupOfURLs.getMembers().size(), 1);
    assertEquals(groupOfURLs.getMembers().iterator().next().getValue(),
                 user1.getId());
  }



  /**
   * Provides test coverage for pagination.
   *
   * @throws Exception  If the test fails.
   */
  @Test
  public void testPagination() throws Exception
  {
    // Create some users.
    final long NUM_USERS = 10;
    for (int i = 0; i < NUM_USERS; i++)
    {
      final String uid = "paginationUser." + i;
      dsInstance.getConnectionPool().add(
          generateUserEntry(uid, baseDN,
                            "Test", "User", "password"));
    }

    // Fetch the users one page at a time with page size equal to 1.
    final SCIMEndpoint<UserResource> userEndpoint = service.getUserEndpoint();
    int pageSize = 1;
    final Set<String> userIDs = new HashSet<String>();
    for (long startIndex = 1; startIndex <= NUM_USERS; startIndex += pageSize)
    {
      final Resources<UserResource> resources =
          userEndpoint.query("userName sw \"paginationUser\"", null,
                             new PageParameters(startIndex, pageSize));
      assertEquals(resources.getTotalResults(), NUM_USERS);
      assertEquals(resources.getStartIndex(), startIndex);
      assertEquals(resources.getItemsPerPage(), pageSize);
      for (final UserResource resource : resources)
      {
        userIDs.add(resource.getId());
      }
    }
    assertEquals(userIDs.size(), NUM_USERS);

    // Create some groups.
    final long NUM_GROUPS = 10;
    for (int i = 0; i < NUM_GROUPS; i++)
    {
      final String cn = "paginationGroup." + i;
      dsInstance.getConnectionPool().add(
          generateGroupOfNamesEntry(cn, baseDN, userIDs));
    }

    // Fetch the groups one page at a time with page size equal to 3.
    final SCIMEndpoint<GroupResource> groupEndpoint =
           service.getGroupEndpoint();
    pageSize = 3;
    final Set<String> groupIDs = new HashSet<String>();
    for (long startIndex = 1; startIndex <= NUM_GROUPS; startIndex += pageSize)
    {
      final Resources<GroupResource> resources =
        groupEndpoint.query("displayName sw \"paginationGroup\"",
          new SortParameters("displayName", "ascending"),
          new PageParameters(startIndex, pageSize));

      assertEquals(resources.getTotalResults(), NUM_GROUPS);
      assertEquals(resources.getStartIndex(), startIndex);

      int numResources = 0;
      for (final GroupResource resource : resources)
      {
        numResources++;
        groupIDs.add(resource.getId());
      }
      assertEquals(resources.getItemsPerPage(), numResources);
    }
    assertEquals(groupIDs.size(), NUM_GROUPS);

    // Attempt to fetch resources from a non-existent page.
    final long startIndex = NUM_GROUPS + 1;
    final Resources<GroupResource> resources =
        groupEndpoint.query("displayName sw \"paginationGroup\"",
          new SortParameters("displayName", "ascending"),
          new PageParameters(startIndex, pageSize));
    assertEquals(resources.getTotalResults(), NUM_GROUPS);
    assertEquals(resources.getItemsPerPage(), 0);
    assertEquals(resources.getStartIndex(), startIndex);
  }



  /**
   * Tests the maxResults configuration setting.
   *
   * @throws Exception If the test fails.
   */
  @Test
  public void testMaxResults() throws Exception
  {
    // Lower the maxResults setting.
    final int maxResults = 1;
    scimInstance.dsconfig(
        "set-http-servlet-extension-prop",
        "--extension-name", "SCIM",
        "--add", "extension-argument:maxResults=" + maxResults);

    // Create some users.
    final long NUM_USERS = 10;
    for (int i = 0; i < NUM_USERS; i++)
    {
      final String uid = "maxResultsUser." + i;
      dsInstance.getConnectionPool().add(
          generateUserEntry(uid, baseDN, "Test", "User", "password"));
    }

    // Try to fetch more users than can be returned.
    final SCIMEndpoint<UserResource> userEndpoint = service.getUserEndpoint();
    final Resources<UserResource> resources = userEndpoint.query(null);
    assertEquals(resources.getTotalResults(), maxResults);

    //Clean up
    scimInstance.dsconfig(
        "set-http-servlet-extension-prop",
        "--extension-name", "SCIM",
        "--remove", "extension-argument:maxResults=" + maxResults);
  }



  /**
   * Tests that an attempt to set an invalid configuration is handled correctly.
   *
   * @throws Exception If the test passes.
   */
  @Test(expectedExceptions = AssertionError.class)
  public void testInvalidConfiguration() throws Exception
  {
    // Create a resources configuration that is not valid because it defines
    // no resources.
    final String resourcesFile = TestCaseUtils.createTempFile(
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>",
        "<scim-ldap:resources xmlns:" +
        "scim-ldap=\"http://www.unboundid.com/scim-ldap\">",
        "</scim-ldap:resources>");
    scimInstance.dsconfig(
        "set-http-servlet-extension-prop",
        "--extension-name", "SCIM",
        "--set", "extension-argument:useResourcesFile=" + resourcesFile);
  }



  /**
   * Tests the Service Provider Config endpoint.
   *
   * @throws Exception If the test fails.
   */
  @Test
  public void testServiceProviderConfig() throws Exception
  {
    final ServiceProviderConfig config = service.getServiceProviderConfig();

    // These assertions need to be updated as optional features are implemented.
    assertFalse(config.getPatchConfig().isSupported());
    assertFalse(config.getBulkConfig().isSupported());
    assertTrue(config.getFilterConfig().isSupported());
    assertTrue(config.getFilterConfig().getMaxResults() > 0);
    assertFalse(config.getChangePasswordConfig().isSupported());
    assertFalse(config.getSortConfig().isSupported());
    assertFalse(config.getETagConfig().isSupported());
    assertTrue(config.getAuthenticationSchemes().size() > 0);

    for (final AuthenticationScheme s : config.getAuthenticationSchemes())
    {
      assertNotNull(s.getName());
      assertNotNull(s.getDescription());
    }
  }



  /**
   * Tests HTTP Basic Auth.
   *
   * @throws Exception  If the test fails.
   */
  @Test
  public void testBasicAuth() throws Exception
  {
    // Create a new user.
    final String id = "uid=basicAuthUser," + baseDN;
    dsInstance.getConnectionPool().add(
        generateUserEntry(
            "basicAuthUser", baseDN, "Basic", "User", "password"));

    // Create a client service that authenticates as the user.
    final URI uri = new URI("http", null, scimInstance.getLdapHost(),
                            scimPort, null, null, null);
    final SCIMService basicAuthService = new SCIMService(uri, id, "password");

    // Check that the authenticated user can read its own entry.
    assertNotNull(basicAuthService.getUserEndpoint().get(id));
  }



  /**
   * Tests HTTP Basic Auth with the wrong credentials.
   *
   * @throws ClientAuthenticationException  If the test passes.
   * @throws LDAPException                  If the test fails.
   * @throws SCIMException                  If the test fails.
   * @throws URISyntaxException             If the test fails.
   */
  @Test(expectedExceptions = ClientAuthenticationException.class)
  public void testBasicAuthInvalidCredentials()
      throws ClientAuthenticationException, LDAPException, SCIMException,
             URISyntaxException
  {
    // Create a new user.
    final String id = "uid=invalidCredentials," + baseDN;
    dsInstance.getConnectionPool().add(
        generateUserEntry(
            "invalidCredentials", baseDN, "Basic", "User", "password"));

    // Create a client service that authenticates with the wrong password.
    final URI uri = new URI("http", null, scimInstance.getLdapHost(),
                            scimPort, null, null, null);
    final SCIMService basicAuthService = new SCIMService(uri, id, "assword");

    basicAuthService.getUserEndpoint().get(id);
  }
}