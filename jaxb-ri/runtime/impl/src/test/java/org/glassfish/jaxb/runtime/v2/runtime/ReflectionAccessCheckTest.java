/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.glassfish.jaxb.runtime.v2.runtime;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.glassfish.jaxb.core.v2.ClassFactory;
import org.glassfish.jaxb.runtime.v2.runtime.reflect.Accessor;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Public members of public classes are used with the reflective access check suppressed;
 * binding through them must behave exactly as before.
 */
public class ReflectionAccessCheckTest {

    @XmlRootElement(name = "fields")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class PublicFields {
        @XmlAttribute public int id;
        @XmlElement public String name;
        @XmlElement public Double score;
        @XmlElement protected String hidden;
    }

    @XmlRootElement(name = "props")
    @XmlAccessorType(XmlAccessType.PROPERTY)
    public static class PublicProperties {
        private int id;
        private String name;
        @XmlAttribute public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        @XmlElement public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @Test
    @SuppressWarnings("deprecation")
    public void publicMembersSkipThePerCallAccessCheck() throws Exception {
        Accessor.FieldReflection<PublicFields, Object> field =
                new Accessor.FieldReflection<>(PublicFields.class.getField("name"));
        assertTrue(field.f.isAccessible());

        Accessor.GetterSetterReflection<PublicProperties, Object> property =
                new Accessor.GetterSetterReflection<>(PublicProperties.class.getMethod("getName"),
                        PublicProperties.class.getMethod("setName", String.class));
        assertTrue(property.getter.isAccessible());
        assertTrue(property.setter.isAccessible());

        assertEquals(PublicFields.class, ClassFactory.create(PublicFields.class).getClass());
    }

    @Test
    public void roundTripThroughPublicFieldsAndProperties() throws Exception {
        JAXBContext context = JAXBContext.newInstance(PublicFields.class, PublicProperties.class);

        PublicFields f = (PublicFields) context.createUnmarshaller().unmarshal(new StringReader(
                "<fields id='7'><name>n</name><score>1.5</score><hidden>h</hidden></fields>"));
        assertEquals(7, f.id);
        assertEquals("n", f.name);
        assertEquals(1.5, f.score);
        assertEquals("h", f.hidden);
        StringWriter out = new StringWriter();
        context.createMarshaller().marshal(f, out);
        assertTrue(out.toString().endsWith(
                "<fields id=\"7\"><name>n</name><score>1.5</score><hidden>h</hidden></fields>"), out::toString);

        PublicProperties p = (PublicProperties) context.createUnmarshaller().unmarshal(new StringReader(
                "<props id='3'><name>x</name></props>"));
        assertEquals(3, p.getId());
        assertEquals("x", p.getName());
        out = new StringWriter();
        context.createMarshaller().marshal(p, out);
        assertTrue(out.toString().endsWith("<props id=\"3\"><name>x</name></props>"), out::toString);
    }
}
