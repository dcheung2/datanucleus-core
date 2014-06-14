/******************************************************************
Copyright (c) 2004 Andy Jefferson and others. All rights reserved.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

Contributors:
    ...
*****************************************************************/
package org.datanucleus.store.exceptions;

import org.datanucleus.exceptions.NucleusUserException;

/**
 * Representation of an error encountered initialising a datastore.
 * 
 * @version $Revision: 1.5 $
 */
public class DatastoreInitialisationException extends NucleusUserException
{
    private static final long serialVersionUID = 3704576773794574913L;

    /**
     * Constructor for an exception with a message.
     * @param msg the detail message
     */
    public DatastoreInitialisationException(String msg)
    {
        super(msg);
        setFatal();
    }

    /**
     * Constructor for an exception with a message.
     * @param msg the detail message
     * @param ex the source exception
     */
    public DatastoreInitialisationException(String msg, Throwable ex)
    {
        super(msg, ex);
        setFatal();
    }
}