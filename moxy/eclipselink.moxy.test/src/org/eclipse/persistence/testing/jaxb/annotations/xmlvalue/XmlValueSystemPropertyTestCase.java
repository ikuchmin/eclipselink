/*******************************************************************************
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 * which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 *     Martin Vojtek - 2.6 - initial implementation
 ******************************************************************************/
package org.eclipse.persistence.testing.jaxb.annotations.xmlvalue;

import mockit.Deencapsulation;

import mockit.Expectations;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;

import org.eclipse.persistence.jaxb.compiler.AnnotationsProcessor;
import org.eclipse.persistence.jaxb.compiler.Property;
import org.eclipse.persistence.jaxb.javamodel.Helper;
import org.eclipse.persistence.jaxb.javamodel.JavaClass;
import org.eclipse.persistence.jaxb.javamodel.JavaHasAnnotations;
import org.eclipse.persistence.oxm.annotations.XmlValueExtension;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertTrue;

/**
 * Tests useXmlValueExtension method when system property org.eclipse.persistence.moxy.annotation.xml-value-extension is set.
 */
@RunWith(JMockit.class)
public class XmlValueSystemPropertyTestCase {

    @Test
    public void testSystemXmlValueExtensionSet(final @Mocked({""}) Property property, final @Mocked({""}) Helper helper) {
        new Expectations(System.class) {{
            System.getProperty("org.eclipse.persistence.moxy.annotation.xml-value-extension"); returns("true");
        }};

        AnnotationsProcessor processor = new AnnotationsProcessor(helper);
        boolean result = Deencapsulation.invoke(processor, "useXmlValueExtension", property);
        assertTrue("XmlValueExtension should be used.", result);
    }
}
