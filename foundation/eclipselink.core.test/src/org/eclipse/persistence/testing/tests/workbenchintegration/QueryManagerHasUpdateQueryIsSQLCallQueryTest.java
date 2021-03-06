/*******************************************************************************
 * Copyright (c) 1998, 2013 Oracle and/or its affiliates. All rights reserved.
 * This program and the accompanying materials are made available under the 
 * terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0 
 * which accompanies this distribution. 
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at 
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 *     Oracle - initial API and implementation from Oracle TopLink
 ******************************************************************************/  
package org.eclipse.persistence.testing.tests.workbenchintegration;

import org.eclipse.persistence.descriptors.ClassDescriptor;
import org.eclipse.persistence.mappings.DatabaseMapping;
import org.eclipse.persistence.queries.SQLCall;
import org.eclipse.persistence.queries.UpdateObjectQuery;
import org.eclipse.persistence.testing.models.employee.domain.Employee;


/** This class has been modified as per instructions from Tom Ware and Development
 *  the test(), verify() are all inherited from the parent class
 *  We pass in the TopLink project and the string we are looking for and the superclass does the verification and testing
 */
public class QueryManagerHasUpdateQueryIsSQLCallQueryTest extends ProjectClassGeneratorResultFileTest {
    ClassDescriptor descriptorToModify;
    DatabaseMapping mappingToModify;

    public QueryManagerHasUpdateQueryIsSQLCallQueryTest() {
        super(new org.eclipse.persistence.testing.models.employee.relational.EmployeeProject(), 
              "descriptor.getQueryManager().setUpdateSQLString(\"testString\");");
        setDescription("Test addQueryManagerPropertyLines method -> UpdateQuery.isSQLQuery && DescriptorQueryManager().hasUpdateQuery");
    }

    protected void setup() {
        getSession().getIdentityMapAccessor().initializeAllIdentityMaps();

        descriptorToModify = project.getDescriptors().get(Employee.class);
        UpdateObjectQuery testReadQuery = new UpdateObjectQuery();
        SQLCall testCall = new SQLCall();
        testReadQuery.setCall(testCall); // setting the SQLCall on ReadObjectQuery
        testReadQuery.setSQLString("testString");
        descriptorToModify.getQueryManager().setUpdateQuery(testReadQuery);
    }
}
