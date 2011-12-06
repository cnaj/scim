/*
 * Copyright 2011 UnboundID Corp.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPLv2 only)
 * or the terms of the GNU Lesser General Public License (LGPLv2.1 only)
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 */

package com.unboundid.scim.sdk;

/**
 * Signals the specified version number does not match the resource's latest
 * version number or a Service Provider refused to create a new,
 * duplicate resource.
 *
 * This exception corresponds to HTTP response code 409 CONFLICT.
 */
public class ResourceConflictException extends SCIMException
{
  /**
   * Create a new <code>ResourceConflictException</code> from the provided
   * information.
   *
   * @param errorMessage  The error message for this SCIM exception.
   */
  public ResourceConflictException(final String errorMessage) {
    super(409, errorMessage);
  }
}