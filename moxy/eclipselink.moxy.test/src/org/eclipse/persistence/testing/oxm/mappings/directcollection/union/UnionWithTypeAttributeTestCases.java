/*******************************************************************************
 * Copyright (c) 1998, 2012 Oracle and/or its affiliates. All rights reserved.
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
package org.eclipse.persistence.testing.oxm.mappings.directcollection.union;

import java.math.BigInteger;
import java.util.ArrayList;
import javax.xml.namespace.QName;
import org.eclipse.persistence.oxm.XMLConstants;
import org.eclipse.persistence.oxm.XMLUnionField;
import org.eclipse.persistence.oxm.mappings.XMLDirectMapping;
import org.eclipse.persistence.sessions.Project;
import org.eclipse.persistence.testing.oxm.mappings.XMLMappingTestCases;

public class UnionWithTypeAttributeTestCases extends XMLMappingTestCases {
    private final static String XML_RESOURCE = "org/eclipse/persistence/testing/oxm/mappings/directcollection/union/UnionWithTypeAttribute.xml";
    protected final static Integer CONTROL_ITEM = new Integer(10);
    protected final static String CONTROL_FIRST_NAME = "Jane";
    protected final static String CONTROL_LAST_NAME = "Doe";

    public UnionWithTypeAttributeTestCases(String name) throws Exception {
        super(name);
        setControlDocument(XML_RESOURCE);
        Project p = new SimpleUnionProject();

        ((XMLUnionField)p.getDescriptor(Person.class).getMappingForAttributeName("items").getField()).setIsTypedTextField(true);

        QName stringQName = new QName(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI, "string");
        QName integerQName = new QName(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI, "integer");

        ArrayList schemaTypes = new ArrayList();
        schemaTypes.add(XMLConstants.DATE_QNAME);
        schemaTypes.add(stringQName);
        schemaTypes.add(integerQName);

        ((XMLUnionField)p.getDescriptor(Person.class).getMappingForAttributeName("items").getField()).setSchemaTypes(schemaTypes);
        setProject(p);
    }

    protected Object getControlObject() {
        Person person = new Person();
        person.addItem(CONTROL_ITEM);
        person.setFirstName(CONTROL_FIRST_NAME);
        person.setLastName(CONTROL_LAST_NAME);
        return person;
    }
}
