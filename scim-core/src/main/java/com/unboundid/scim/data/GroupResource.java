/*
 * Copyright 2011 UnboundID Corp.
 * All Rights Reserved.
 */
package com.unboundid.scim.data;

import com.unboundid.scim.schema.ResourceDescriptor;
import com.unboundid.scim.sdk.SCIMConstants;
import com.unboundid.scim.sdk.SCIMObject;

import java.util.Collection;

/**
 * This class represents a Group resource.
 */
public class GroupResource extends BaseResource
{
  /**
   * A <code>ResourceFactory</code> for creating <code>GroupResource</code>
   * instances.
   */
  public static final ResourceFactory<GroupResource> GROUP_RESOURCE_FACTORY =
      new ResourceFactory<GroupResource>() {
    public GroupResource createResource(
        final ResourceDescriptor resourceDescriptor,
        final SCIMObject scimObject) {
      return new GroupResource(resourceDescriptor, scimObject);
    }
  };

  /**
   * Construct an empty <code>GroupResource</code> with the specified
   * <code>ResourceDescriptor</code>.
   *
   * @param resourceDescriptor The resource descriptor for this SCIM resource.
   */
  public GroupResource(final ResourceDescriptor resourceDescriptor) {
    super(resourceDescriptor);
  }

  /**
   * Construct a <code>GroupResource</code> with the specified
   * <code>ResourceDescriptor</code> and backed by the given
   * <code>SCIMObject</code>.
   *
   * @param resourceDescriptor The resource descriptor for this SCIM resource.
   * @param scimObject         The <code>SCIMObject</code> containing all the
   *                           SCIM attributes and their values.
   */
  public GroupResource(final ResourceDescriptor resourceDescriptor,
                       final SCIMObject scimObject) {
    super(resourceDescriptor, scimObject);
  }

  /**
   * Retrieves the human readable name for the Group.
   *
   * @return the human readable name for the Group.
   */
  public String getDisplayName()
  {
    return getSingularAttributeValue(SCIMConstants.SCHEMA_URI_CORE,
        "displayName", AttributeValueResolver.STRING_RESOLVER);
  }

  /**
   * Sets the human readable name for the Group.
   *
   * @param displayName the human readable name for the Group.
   * @return this resource instance.
   */
  public GroupResource setDisplayName(final String displayName)
  {
    setSingularAttributeValue(SCIMConstants.SCHEMA_URI_CORE, "displayName",
        AttributeValueResolver.STRING_RESOLVER, displayName);
    return this;
  }

  /**
   * Retrieves the list of member IDs of the Group.
   *
   * @return the list of member IDs of the Group.
   */
  public Collection<Entry<String>> getMembers()
  {
    return getPluralAttributeValue(SCIMConstants.SCHEMA_URI_CORE,
        "members", Entry.STRINGS_RESOLVER);
  }

  /**
   * Sets the list of member IDs of the Group.
   *
   * @param members the list of member IDs of the Group.
   * @return this resource instance.
   */
  public GroupResource setMembers(final Collection<Entry<String>> members)
  {
    setPluralAttributeValue(SCIMConstants.SCHEMA_URI_CORE, "members",
        Entry.STRINGS_RESOLVER, members);
    return this;
  }
}
