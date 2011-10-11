/*
 * Copyright 2011 UnboundID Corp.
 * All Rights Reserved.
 */
package com.unboundid.scim.data;

import com.unboundid.scim.schema.AttributeDescriptor;
import com.unboundid.scim.sdk.SCIMAttribute;
import com.unboundid.scim.sdk.SCIMAttributeValue;

import java.util.ArrayList;
import java.util.List;

/**
 * The User's manager. A complex type that optionally allows Service Providers
 * to represent organizational hierarchy by referencing the "id" attribute of
 * another User
 */
public class Manager
{
  /**
   * The <code>AttributeValueResolver</code> that resolves SCIM attribute values
   * to/from <code>Manager</code> instances.
   */
  public static final AttributeValueResolver<Manager> MANAGER_RESOLVER =
      new AttributeValueResolver<Manager>() {
        /**
         * {@inheritDoc}
         */
        public Manager toInstance(final SCIMAttributeValue value) {
          return new Manager(
              getSingularSubAttributeValue(value, "managerId",
                  STRING_RESOLVER),
              getSingularSubAttributeValue(value, "displayName",
                  STRING_RESOLVER));
        }

        /**
         * {@inheritDoc}
         */
        public SCIMAttributeValue fromInstance(
            final AttributeDescriptor attributeDescriptor,
            final Manager value) {
          final List<SCIMAttribute> subAttributes =
              new ArrayList<SCIMAttribute>(2);

          if (value.managerId != null)
          {
            subAttributes.add(
                SCIMAttribute.createSingularAttribute(
                    attributeDescriptor.getAttribute("managerId"),
                    SCIMAttributeValue.createStringValue(value.managerId)));
          }

          if (value.displayName != null)
          {
            subAttributes.add(
                SCIMAttribute.createSingularAttribute(
                    attributeDescriptor.getAttribute("displayName"),
                    SCIMAttributeValue.createStringValue(value.displayName)));
          }
          return SCIMAttributeValue.createComplexValue(subAttributes);
        }
      };

  private String managerId;

  private String displayName;

  /**
   * Creates a SCIM enterprise user extension 'manager' attribute. Any of the
   * arguments may be {@code null} if they are not to be included.
   *
   * @param managerId        The id of the SCIM resource representing the User's
   *                         manager.
   * @param displayName      The displayName of the User's manager.
   */
  public Manager(final String managerId, final String displayName) {
    this.displayName = displayName;
    this.managerId = managerId;
  }

  /**
   * Retrieves the displayName of the User's manager.
   *
   * @return The displayName of the User's manager.
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Sets the displayName of the User's manager.
   *
   * @param displayName The displayName of the User's manager.
   */
  public void setDisplayName(final String displayName) {
    this.displayName = displayName;
  }

  /**
   * Retrieves the id of the SCIM resource representing the User's manager.
   *
   * @return The id of the SCIM resource representing the User's manager.
   */
  public String getManagerId() {
    return managerId;
  }

  /**
   * Sets the id of the SCIM resource representing the User's manager.
   *
   * @param managerId The id of the SCIM resource representing the User's
   *                  manager.
   */
  public void setManagerId(final String managerId) {
    this.managerId = managerId;
  }
}
