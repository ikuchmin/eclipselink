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
 *     Marcel Valovy - 2.6 - initial implementation
 ******************************************************************************/
package org.eclipse.persistence.testing.jaxb.beanvalidation.rt_dom;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author Marcel Valovy - marcel.valovy@oracle.com
 * @since 2.6
 */
@XmlRootElement
public enum Department {
    RDBMS, JavaSE, JavaEE, Sales, Support
}