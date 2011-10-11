/*
 * Copyright 2011 UnboundID Corp.
 * All Rights Reserved.
 */

package com.unboundid.scim.marshal.json;

import com.unboundid.scim.data.BaseResource;
import com.unboundid.scim.marshal.Marshaller;
import com.unboundid.scim.sdk.Resources;
import com.unboundid.scim.sdk.SCIMAttribute;
import com.unboundid.scim.sdk.SCIMAttributeValue;
import com.unboundid.scim.sdk.SCIMConstants;
import com.unboundid.scim.sdk.SCIMException;
import com.unboundid.scim.sdk.SCIMObject;
import org.json.JSONException;
import org.json.JSONWriter;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;



/**
 * This class provides a SCIM object marshaller implementation to write SCIM
 * objects to their Json representation.
 */
public class JsonMarshaller implements Marshaller
{
  /**
   * {@inheritDoc}
   */
  public void marshal(final BaseResource resource,
                      final OutputStream outputStream)
      throws Exception
  {
    final OutputStreamWriter outputStreamWriter =
        new OutputStreamWriter(outputStream);
    try
    {
      marshal(resource, new JSONWriter(outputStreamWriter), true);
    }
    finally
    {
      outputStreamWriter.close();
    }
  }

  /**
   * Write a SCIM resource to a JSON writer.
   *
   * @param resource   The SCIM resource to be written.
   * @param jsonWriter Output to write the Object to.
   * @param includeSchemas  Indicates whether the schemas should be written
   *                        at the start of the object.
   * @throws org.json.JSONException Thrown if error writing to output.
   */
  private void marshal(final BaseResource resource,
                       final JSONWriter jsonWriter,
                       final boolean includeSchemas)
      throws JSONException
  {
    jsonWriter.object();

    final Set<String> schemas = new HashSet<String>(
        resource.getScimObject().getSchemas());
    if (includeSchemas)
    {
      // Write out the schemas for this object.
      jsonWriter.key(SCIMObject.SCHEMAS_ATTRIBUTE_NAME);
      jsonWriter.array();
      for (final String schema : schemas)
      {
        jsonWriter.value(schema);
      }
      jsonWriter.endArray();
    }

    // first write out core schema, then if any extensions write them
    // out in their own json object keyed by the schema name

    for (final SCIMAttribute attribute : resource.getScimObject()
        .getAttributes(SCIMConstants.SCHEMA_URI_CORE))
    {
      if (attribute.isPlural())
      {
        this.writePluralAttribute(attribute, jsonWriter);
      }
      else
      {
        this.writeSingularAttribute(attribute, jsonWriter);
      }
    }

    // write out any custom schemas
    for (final String schema : schemas)
    {
      if (!schema.equalsIgnoreCase(SCIMConstants.SCHEMA_URI_CORE))
      {
        jsonWriter.key(schema);
        jsonWriter.object();
        for (SCIMAttribute attribute :
            resource.getScimObject().getAttributes(schema))
        {
          if (attribute.isPlural())
          {
            this.writePluralAttribute(attribute, jsonWriter);
          }
          else
          {
            this.writeSingularAttribute(attribute, jsonWriter);
          }
        }
        jsonWriter.endObject();
      }
    }
    jsonWriter.endObject();
  }

  /**
   * {@inheritDoc}
   */
  public void marshal(final Resources<? extends BaseResource> response,
                      final OutputStream outputStream)
      throws Exception
  {
    final OutputStreamWriter outputStreamWriter =
        new OutputStreamWriter(outputStream);
    try
    {
      final JSONWriter jsonWriter = new JSONWriter(outputStreamWriter);
      jsonWriter.object();
      jsonWriter.key("totalResults");
      jsonWriter.value(response.getTotalResults());

      jsonWriter.key("itemsPerPage");
      jsonWriter.value(response.getItemsPerPage());

      jsonWriter.key("startIndex");
      jsonWriter.value(response.getStartIndex());

      // Figure out what schemas are referenced by the resources.
      final Set<String> schemaURIs = new HashSet<String>();
      for (final BaseResource resource : response)
      {
        schemaURIs.addAll(resource.getScimObject().getSchemas());
      }

      // Write the schemas.
      jsonWriter.key(SCIMObject.SCHEMAS_ATTRIBUTE_NAME);
      jsonWriter.array();
      for (final String schemaURI : schemaURIs)
      {
        jsonWriter.value(schemaURI);
      }
      jsonWriter.endArray();

      // Write the resources.
      jsonWriter.key("Resources");
      jsonWriter.array();
      for (final BaseResource resource : response)
      {
        marshal(resource, jsonWriter, false);
      }
      jsonWriter.endArray();

      jsonWriter.endObject();
    }
    finally
    {
      outputStreamWriter.close();
    }
  }

  /**
   * {@inheritDoc}
   */
  public void marshal(final SCIMException response,
                      final OutputStream outputStream) throws Exception
  {
    final OutputStreamWriter outputStreamWriter =
        new OutputStreamWriter(outputStream);
    try
    {
      final JSONWriter jsonWriter = new JSONWriter(outputStreamWriter);

      jsonWriter.object();
      jsonWriter.key("Errors");
      jsonWriter.array();

      jsonWriter.object();

      jsonWriter.key("code");
      jsonWriter.value(response.getStatusCode());

      final String description = response.getMessage();
      if (description != null)
      {
        jsonWriter.key("description");
        jsonWriter.value(description);
      }

      jsonWriter.endObject();

      jsonWriter.endArray();

      jsonWriter.endObject();
    }
    finally
    {
      outputStreamWriter.close();
    }
  }



  /**
   * Write a plural attribute to an XML stream.
   *
   * @param scimAttribute The attribute to be written.
   * @param jsonWriter    Output to write the attribute to.
   *
   * @throws org.json.JSONException Thrown if error writing to output.
   */
  private void writePluralAttribute(final SCIMAttribute scimAttribute,
                                    final JSONWriter jsonWriter)
      throws JSONException
  {

    SCIMAttributeValue[] pluralValues = scimAttribute.getPluralValues();
    jsonWriter.key(scimAttribute.getName());
    jsonWriter.array();
    for (SCIMAttributeValue pluralValue : pluralValues)
    {
      for (SCIMAttribute attribute : pluralValue.getAttributes().values())
      {
        this.writeComplexAttribute(attribute, jsonWriter);
      }
    }
    jsonWriter.endArray();
  }



  /**
   * Write a singular attribute to an XML stream.
   *
   * @param scimAttribute The attribute to be written.
   * @param jsonWriter    Output to write the attribute to.
   *
   * @throws org.json.JSONException Thrown if error writing to output.
   */
  private void writeSingularAttribute(final SCIMAttribute scimAttribute,
                                      final JSONWriter jsonWriter)
      throws JSONException
  {
    jsonWriter.key(scimAttribute.getName());
    SCIMAttributeValue val = scimAttribute.getSingularValue();
    if (val.isComplex())
    {
      jsonWriter.object();
      for (SCIMAttribute a : val.getAttributes().values())
      {
        this.writeSingularAttribute(a, jsonWriter);
      }
      jsonWriter.endObject();
    }
    else
    {
      if (scimAttribute.getAttributeDescriptor().getDataType() != null)
      {
        switch (scimAttribute.getAttributeDescriptor().getDataType())
        {
          case BOOLEAN:
            jsonWriter.value(val.getBooleanValue());
            break;

          case INTEGER:
            jsonWriter.value(val.getLongValue());
            break;

          case BINARY:
          case DATETIME:
          case STRING:
          default:
            jsonWriter.value(val.getStringValue());
            break;
        }
      }
      else
      {
        jsonWriter.value(val.getStringValue());
      }
    }
  }



  /**
   * Write a complex attribute to an XML stream.
   *
   * @param scimAttribute The attribute to be written.
   * @param jsonWriter    Output to write the attribute to.
   *
   * @throws org.json.JSONException Thrown if error writing to output.
   */
  private void writeComplexAttribute(final SCIMAttribute scimAttribute,
                                     final JSONWriter jsonWriter)
      throws JSONException
  {
    SCIMAttributeValue value = scimAttribute.getSingularValue();
    Map<String, SCIMAttribute> attributes = value.getAttributes();
    jsonWriter.object();
    for (SCIMAttribute attribute : attributes.values())
    {
      writeSingularAttribute(attribute, jsonWriter);
    }
    jsonWriter.endObject();
  }
}
