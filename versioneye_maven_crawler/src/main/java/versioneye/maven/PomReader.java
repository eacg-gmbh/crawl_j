/**
 * Copyright (C) 2009 Future Invent Informationsmanagement GmbH. All rights
 * reserved. <http://www.fuin.org/>
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library. If not, see <http://www.gnu.org/licenses/>.
 */
package versioneye.maven;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.fuin.utils4j.InvokeMethodFailedException;
import org.fuin.utils4j.Utils4J;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

/**
 * Helper class for reading a Maven POM file. WARNING: This is just a quick
 * "hack" to read a project "pom.xml" - May not work in all cases!
 */
public final class PomReader {

    private PomReader() {
        throw new UnsupportedOperationException("");
    }

    @SuppressWarnings("unchecked")
    private static Object createInstance(final Class clasz) {
        try {
            return clasz.newInstance();
        } catch (InstantiationException ex) {
            throw new RuntimeException("Error creating new instance '" + clasz + "'!", ex);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("Error creating new instance '" + clasz + "'!", ex);
        }
    }

    public static Model readSinglePom(String pomPath) throws Exception {
        Reader reader = new FileReader(pomPath);
        MavenXpp3Reader xpp3Reader = new MavenXpp3Reader();
        Model model = xpp3Reader.read(reader);
        reader.close();
        return model;
    }

    public static Model readSinglePom(Reader reader) throws Exception {
        MavenXpp3Reader xpp3Reader = new MavenXpp3Reader();
        Model model = xpp3Reader.read(reader);
        reader.close();
        return model;
    }

    public static Model readModel(final File repositoryDir, final String groupId, final String artifactId) {
        return readModel(repositoryDir, groupId, artifactId, null);
    }

    public static Model readModel(final File repositoryDir, final String groupId, final String artifactId, final String version) {
        String latestVersion  = fetchLatestVersion(repositoryDir, groupId, artifactId, version);
        final File pomXmlFile = new File(repositoryDir, createPathAndFilename(groupId, artifactId, latestVersion, "pom"));
        try {
            final Reader reader = new FileReader(pomXmlFile);
            final Model model;
            try {
                final MavenXpp3Reader xpp3Reader = new MavenXpp3Reader();
                model = xpp3Reader.read(reader);
            } finally {
                reader.close();
            }
            if (model.getParent() == null) {
                return model;
            } else {
                final String parentGroupId = model.getParent().getGroupId();
                final String parentArtifactId = model.getParent().getArtifactId();
                final String parentVersion = model.getParent().getVersion();
                final Model parentModel = readModel(repositoryDir, parentGroupId, parentArtifactId, parentVersion);
                return merge(parentModel, model);
            }
        } catch (XmlPullParserException ex) {
            throw new RuntimeException("Error parsing POM!", ex);
        } catch (final IOException ex) {
            throw new RuntimeException("Error reading POM!", ex);
        }
    }

    private static String fetchLatestVersion(final File repositoryDir, final String groupId, final String artifactId, final String version){
        final String latestVersion;
        if (version == null) {
            latestVersion = findLatestVersion(repositoryDir, groupId, artifactId);
            if (latestVersion == null) {
                throw new IllegalStateException("Latest version for '" + groupId + ":" + artifactId
                        + "' not found! [" + repositoryDir + "]");
            }
        } else {
            latestVersion = version;
        }
        return latestVersion;
    }

    private static String createPath(final String groupId, final String artifactId) {
        return groupId.replace('.', '/') + "/" + artifactId;
    }

    public static String createPathAndFilename(final String groupId, final String artifactId, final String version, final String fileExtension) {
        final String filename = artifactId + "-" + version + "." + fileExtension;
        return createPath(groupId, artifactId) + "/" + version + "/" + filename;
    }

    private static String toGetter(final String field) {
        return "get" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
    }

    private static String toIs(final String field) {
        return "is" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
    }

    private static String toSetter(final String field) {
        return "set" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
    }

    private static Object get(final Object src, final String field) {
        try {
            return Utils4J.invoke(src, toGetter(field), new Class[] {}, new Object[] {});
        } catch (InvokeMethodFailedException ex) {
            try {
                return Utils4J.invoke(src, toIs(field), new Class[] {}, new Object[] {});
            } catch (InvokeMethodFailedException ex2) {
                throw new RuntimeException("Error getting field '" + field + "' from '" + src
                        + "'!", ex2);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void set(final Object dest, final String field, final Object value, final Class clasz) {
        try {
            Utils4J.invoke(dest, toSetter(field), new Class[] { clasz }, new Object[] { value });
        } catch (InvokeMethodFailedException ex) {
            throw new RuntimeException("Error setting value '" + value + "' for '" + field
                    + "' in '" + dest + "'!", ex);
        }
    }

    private static void set(final Object dest, final String field, final Object value) {
        try {
            Utils4J.invoke(dest, toSetter(field), new Class[] { value.getClass() }, new Object[] { value });
        } catch (InvokeMethodFailedException ex) {
            if (value instanceof Boolean) {
                set(dest, field, value, boolean.class);
            } else if (value instanceof Integer) {
                set(dest, field, value, int.class);
            } else if (value instanceof Long) {
                set(dest, field, value, long.class);
            } else if (value instanceof Short) {
                set(dest, field, value, short.class);
            } else {
                throw new RuntimeException("Error setting value '" + value + "' for '" + field
                        + "' in '" + dest + "'!", ex);
            }
        }
    }

    private static boolean isBaseObject(final Object obj) {
        if (obj instanceof String) {
            return true;
        }
        if (obj instanceof Integer) {
            return true;
        }
        if (obj instanceof Long) {
            return true;
        }
        if (obj instanceof Short) {
            return true;
        }
        if (obj instanceof Boolean) {
            return true;
        }
        if (obj.getClass() == int.class) {
            return true;
        }
        if (obj.getClass() == long.class) {
            return true;
        }
        if (obj.getClass() == short.class) {
            return true;
        }
        if (obj.getClass() == boolean.class) {
            return true;
        }
        return false;
    }

    private static Object copy(final Object from) {
        if (isBaseObject(from)) {
            return from;
        } else {
            final Object to = createInstance(from.getClass());
            copyObjectFields(from, to);
            return to;
        }
    }

    @SuppressWarnings("unchecked")
    private static void copyList(final List<Object> fromValue, final Object to, final String field) {
        List<Object> toList = (List<Object>) get(to, field);
        if (toList == null) {
            toList = (List<Object>) createInstance(fromValue.getClass());
            set(to, field, toList);
        }
        for (int i = 0; i < fromValue.size(); i++) {
            final Object fromObj = fromValue.get(i);
            if (fromObj != null) {
                toList.add(copy(fromObj));
            }
        }
    }

    private static void copyProperties(final Properties fromProps, final Object to, final String field) {
        Properties toProps = (Properties) get(to, field);
        if (toProps == null) {
            toProps = (Properties) createInstance(fromProps.getClass());
            set(to, field, toProps);
        }
        final Iterator<Object> it = fromProps.keySet().iterator();
        while (it.hasNext()) {
            final Object fromKey = it.next();
            final Object fromValue = fromProps.get(fromKey);
            if (fromValue != null) {
                final Object toKey = copy(fromKey);
                final Object toValue = copy(fromValue);
                copyObjectFields(fromValue, toValue);
                toProps.put(toKey, toValue);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> getFields(final Object obj) {
        final List<String> fields = new ArrayList<String>();
        final Method[] methods = obj.getClass().getMethods();
        for (int i = 0; i < methods.length; i++) {
            final String field;
            if (methods[i].getName().startsWith("get")) {
                field = methods[i].getName().substring(3);
            } else if (methods[i].getName().startsWith("is")) {
                field = methods[i].getName().substring(2);
            } else {
                field = null;
            }
            if (field != null) {
                try {
                    final Class type = methods[i].getReturnType();
                    obj.getClass().getMethod(toSetter(field), new Class[] { type });
                    fields.add(field);
                } catch (final NoSuchMethodException ex) {
                    // Ignore
                }
            }
        }
        return fields;
    }

    @SuppressWarnings("unchecked")
    private static void copyObjectFields(final Object from, final Object to) {
        if ((from != null) && (to != null)) {
            final List<String> fields = getFields(from);
            for (int i = 0; i < fields.size(); i++) {
                final String field = fields.get(i);
                final Object fromValue = get(from, field);
                if (fromValue != null) {
                    if (isBaseObject(fromValue)) {
                        set(to, field, fromValue);
                    } else if (fromValue instanceof List) {
                        copyList((List) fromValue, to, field);
                    } else if (fromValue instanceof Properties) {
                        copyProperties((Properties) fromValue, to, field);
                    } else if (fromValue.getClass().getPackage().getName().equals(
                            "org.apache.maven.model")) {
                        copyObjectField(fromValue, to, field);
                    } else {
                        if (fromValue instanceof Xpp3Dom) {
                            set(to, field, fromValue, Object.class);
                        } else {
                            throw new IllegalArgumentException("Cannot copy field '" + field
                                    + "' of type '" + fromValue.getClass().getName() + "' from '"
                                    + from + "' to '" + to + "'!");
                        }
                    }
                }
            }
        }
    }

    private static void copyObjectField(final Object fromValue, final Object to, final String field) {
        Object toValue = get(to, field);
        if (toValue == null) {
            toValue = createInstance(fromValue.getClass());
            set(to, field, fromValue);
        }
        copyObjectFields(fromValue, toValue);
    }

    public static Model merge(final Model parent, final Model child) {
        final Model newModel = new Model();
        copyObjectFields(parent, newModel);
        copyObjectFields(child, newModel);
        return newModel;
    }

    private static String findLatestVersion(final File repositoryDir, final String groupId, final String artifactId) {
        final File dir = new File(repositoryDir, createPath(groupId, artifactId));
        final File file = new File(dir, "maven-metadata-local.xml");
        if (!file.exists()) {
            throw new IllegalArgumentException("File '" + file + "' not found!");
        }

        final String path = "//versions/version/text()";

        String latest = null;
        try {
            final DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
            domFactory.setNamespaceAware(false);
            final DocumentBuilder builder = domFactory.newDocumentBuilder();
            final Document doc = builder.parse(file);
            final XPathFactory factory = XPathFactory.newInstance();
            final XPath xpath = factory.newXPath();
            final XPathExpression expr = xpath.compile(path);
            final Object result = expr.evaluate(doc, XPathConstants.NODESET);
            final NodeList nodes = (NodeList) result;
            if (nodes.getLength() == 0) {
                throw new IllegalStateException("Pfad '" + path + "' nicht gefunden in '" + file
                        + "'!");
            }
            for (int i = 0; i < nodes.getLength(); i++) {
                final Node node = (Node) nodes.item(i);
                latest = node.getNodeValue();
            }
        } catch (final Exception ex) {
            throw new RuntimeException(ex);
        }
        return latest;
    }

}
